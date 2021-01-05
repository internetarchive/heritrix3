/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual
 *  contributors.
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.modules.extractor;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.warc.BaseWARCRecordBuilder;
import org.archive.modules.warc.WARCRecordBuilder;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * Extracts links to media by running youtube-dl in a subprocess. Runs only on
 * html.
 *
 * <p>
 * Also implements {@link WARCRecordBuilder} to write youtube-dl json to the
 * warc.
 * 
 * <p>
 * To use <code>ExtractorYoutubeDL</code>, add this top-level bean:
 * 
 * <pre>
 * &lt;bean id="extractorYoutubeDL" class="org.archive.modules.extractor.ExtractorYoutubeDL"/&gt;
 * </pre>
 * 
 * Then add <code>&lt;ref bean="extractorYoutubeDL"/&gt;</code> to end of the
 * fetch chain, and to the end of the warc writer chain.
 * 
 * <p>
 * Keeps a log of containing pages and media captured as a result of youtube-dl
 * extraction. The format of the log is as follows:
 *
 * <pre>
 * [timestamp] [media-http-status] [media-length] [media-mimetype] [media-digest] [media-timestamp] [media-url] [annotation] [containing-page-digest] [containing-page-timestamp] [containing-page-url] [seed-url]
 * </pre>
 *
 * <p>
 * For containing pages, all of the {@code media-*} fields have the value
 * {@code "-"}, and the annotation field looks like {@code "youtube-dl:3"},
 * meaning that ExtractorYoutubeDL extracted 3 media links from the page.
 *
 * <p>
 * For media, the annotation field looks like {@code "youtube-dl:1/3"}, meaning
 * this is the first of three media links extracted from the containing page.
 * The intention is to use this for playback. The rest of the fields included in
 * the log were also chosen to support creation of an index of media by
 * containing page, to be used for playback.
 *
 * @author nlevitt
 */
public class ExtractorYoutubeDL extends Extractor
        implements Lifecycle, WARCRecordBuilder {
    private static Logger logger =
            Logger.getLogger(ExtractorYoutubeDL.class.getName());

    protected static final String YDL_CONTAINING_PAGE_DIGEST = "ydl-containing-page-digest";
    protected static final String YDL_CONTAINING_PAGE_TIMESTAMP = "ydl-containing-page-timestamp";
    protected static final String YDL_CONTAINING_PAGE_URI = "ydl-containing-page-uri";

    protected static final int MAX_VIDEOS_PER_PAGE = 1000;

    protected transient Logger ydlLogger = null;

    // unnamed toethread-local temporary file
    protected transient ThreadLocal<RandomAccessFile> tempfile = new ThreadLocal<RandomAccessFile>() {
        protected RandomAccessFile initialValue() {
            File t;
            try {
                t = File.createTempFile("ydl", ".json");
                RandomAccessFile f = new RandomAccessFile(t, "rw");
                t.delete();
                return f;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    };

    protected CrawlerLoggerModule crawlerLoggerModule;
    public CrawlerLoggerModule getCrawlerLoggerModule() {
        return this.crawlerLoggerModule;
    }
    @Autowired
    public void setCrawlerLoggerModule(CrawlerLoggerModule crawlerLoggerModule) {
        this.crawlerLoggerModule = crawlerLoggerModule;
    }

    @Override
    public void start() {
        if (!isRunning) {
            // loggerModule.start() creates the log directory, and it might be
            // possible for this module to start before loggerModule, so we need
            // to run this here to prevent an exception
            getCrawlerLoggerModule().start();

            ydlLogger = getCrawlerLoggerModule().setupSimpleLog(getBeanName());
        }
        super.start();
    }

    protected String readToEnd(Reader r) throws IOException {
        StringBuilder buf = new StringBuilder();
        char[] rbuf = new char[4096];
        while (true) {
            int n = r.read(rbuf);
            if (n < 0) {
                return buf.toString();
            }
            buf.append(rbuf, 0, n);
        }
    }

    /**
     * - If {@code uri} is annotated "youtube-dl" and is a 3xx (redirect),
     *   find the redirect among the outlinks and add the "youtube-dl"
     *   annotation to it as well, and also make a note of the containing page
     *   inside the CrawlURI. {@link ExtractorHTTP} needs to have have run
     *   already.
     *
     * - If {@code uri} is annotated "youtube-dl" and is an actual video
     *   download, log a line to ExtractorYoutubeDL.log
     *
     * - If {@link #shouldExtract(CrawlURI)}, do youtube-dl extraction.
     */
    @Override
    protected void extract(CrawlURI uri) {
        String ydlAnnotation = findYdlAnnotation(uri);
        if (ydlAnnotation != null) {
            if (uri.getFetchStatus() >= 300 && uri.getFetchStatus() < 400) {
                doRedirectInheritance(uri, ydlAnnotation);
            } else {
                logCapturedVideo(uri, ydlAnnotation);
            }
        } else {
            YoutubeDLResults results = runYoutubeDL(uri);
            for (int i = 0; i < results.videoUrls.size(); i++) {
                addVideoOutlink(uri, results.videoUrls.get(i), i, results.videoUrls.size());
            }

            for (String pageUrl: results.pageUrls) {
                try {
                    UURI dest = UURIFactory.getInstance(uri.getUURI(), pageUrl);
                    CrawlURI link = uri.createCrawlURI(dest, LinkContext.NAVLINK_MISC,
                            Hop.NAVLINK);
                    uri.getOutLinks().add(link);
                } catch (URIException e1) {
                    logUriError(e1, uri.getUURI(), pageUrl);
                }
            }

            if (results.videoUrls.size() > 0) {
                String annotation = "youtube-dl:" + results.videoUrls.size();
                uri.getAnnotations().add(annotation);
                logContainingPage(uri, annotation);
            }
        }
    }

    protected void addVideoOutlink(CrawlURI uri, String videoUrl, int playlistIndex, int nEntries) {
        try {
            UURI dest = UURIFactory.getInstance(uri.getUURI(), videoUrl);
            CrawlURI link = uri.createCrawlURI(dest, LinkContext.EMBED_MISC,
                    Hop.EMBED);

            // annotation
            String annotation = "youtube-dl:" + (playlistIndex + 1) + "/" + nEntries;
            link.getAnnotations().add(annotation);

            // save info unambiguously identifying containing page capture
            link.getData().put(YDL_CONTAINING_PAGE_URI, uri.toString());
            link.getData().put(YDL_CONTAINING_PAGE_TIMESTAMP,
                    ArchiveUtils.get17DigitDate(uri.getFetchBeginTime()));
            link.getData().put(YDL_CONTAINING_PAGE_DIGEST,
                    uri.getContentDigestSchemeString());

            uri.getOutLinks().add(link);
        } catch (URIException e) {
            logUriError(e, uri.getUURI(), videoUrl);
        }
    }

    protected String findYdlAnnotation(CrawlURI uri) {
        for (String annotation: uri.getAnnotations()) {
            if (annotation.startsWith("youtube-dl:")) {
                return annotation;
            }
        }
        return null;
    }

    protected void logCapturedVideo(CrawlURI uri, String ydlAnnotation) {
        // "length" logic copied from UriProcessingFormatter
        String length = "-";
        if (uri.isHttpTransaction()) {
            if(uri.getContentLength() >= 0) {
                length = Long.toString(uri.getContentLength());
            } else if (uri.getContentSize() > 0) {
                length = Long.toString(uri.getContentSize());
            }
        } else {
            if (uri.getContentSize() > 0) {
                length = Long.toString(uri.getContentSize());
            }
        }

        String seed = uri.containsDataKey(CoreAttributeConstants.A_SOURCE_TAG)
                ? uri.getSourceTag()
                : "-";

        ydlLogger.info(
                uri.getFetchStatus()
                + " " + length
                + " " + MimetypeUtils.truncate(uri.getContentType())
                + " " + uri.getContentDigestSchemeString()
                + " " + ArchiveUtils.get17DigitDate(uri.getFetchBeginTime())
                + " " + uri
                + " " + ydlAnnotation
                + " " + uri.getData().get(YDL_CONTAINING_PAGE_DIGEST)
                + " " + uri.getData().get(YDL_CONTAINING_PAGE_TIMESTAMP)
                + " " + uri.getData().get(YDL_CONTAINING_PAGE_URI)
                + " " + seed);
    }

    protected void logContainingPage(CrawlURI uri, String annotation) {
        String seed = uri.containsDataKey(CoreAttributeConstants.A_SOURCE_TAG)
                ? uri.getSourceTag()
                : "-";

        ydlLogger.info(
                "- - - - - -"
                + " " + annotation
                + " " + uri.getContentDigestSchemeString()
                + " " + ArchiveUtils.get17DigitDate(uri.getFetchBeginTime())
                + " " + uri
                + " " + seed);
    }

    protected void doRedirectInheritance(CrawlURI uri, String ydlAnnotation) {
        for (CrawlURI link: uri.getOutLinks()) {
            if ("R".equals(link.getLastHop())) {
                link.getAnnotations().add(ydlAnnotation);
                link.getData().put(YDL_CONTAINING_PAGE_URI,
                        uri.getData().get(YDL_CONTAINING_PAGE_URI));
                link.getData().put(YDL_CONTAINING_PAGE_TIMESTAMP,
                        uri.getData().get(YDL_CONTAINING_PAGE_TIMESTAMP));
                link.getData().put(YDL_CONTAINING_PAGE_DIGEST,
                        uri.getData().get(YDL_CONTAINING_PAGE_DIGEST));
            }
        }
    }

    protected static class YoutubeDLResults {
        RandomAccessFile jsonFile;
        List<String> videoUrls = new ArrayList<String>();
        List<String> pageUrls = new ArrayList<String>();

        public YoutubeDLResults(RandomAccessFile jsonFile) {
            this.jsonFile = jsonFile;
            try {
                this.jsonFile.setLength(0);
                this.jsonFile.seek(0);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Copies stream to RandomAccessFile <code>out</code> as it is read.
     */
    static class TeedInputStream extends InputStream {
        private InputStream in;
        private RandomAccessFile out;

        public TeedInputStream(InputStream in, RandomAccessFile out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b >= 0) {
                out.write(b);
            }
            return b;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            int n = in.read(b, off, len);
            if (n > 0) {
                out.write(b, off, n);
            }
            return n;
        }
    }

    /**
     * Streams through youtube-dl json output. Sticks video urls in
     * <code>results.videoUrls</code>, web page urls in
     * <code>results.pageUrls</code>, and saves the json in anonymous temp file
     * <code>results.jsonFile</code>.
     */
    protected void streamYdlOutput(InputStream in, YoutubeDLResults results) throws IOException {
        TeedInputStream tee = new TeedInputStream(in, results.jsonFile);
        try (JsonReader jsonReader = new JsonReader(new InputStreamReader(tee, "UTF-8"))) {
            while (true) {
                JsonToken nextToken = jsonReader.peek();
                switch (nextToken) {
                case BEGIN_ARRAY:
                    jsonReader.beginArray();
                    break;
                case BEGIN_OBJECT:
                    jsonReader.beginObject();
                    break;
                case BOOLEAN:
                    jsonReader.nextBoolean();
                    break;
                case END_ARRAY:
                    jsonReader.endArray();
                    break;
                case END_DOCUMENT:
                    return;
                case END_OBJECT:
                    jsonReader.endObject();
                    break;
                case NAME:
                    jsonReader.nextName();
                    break;
                case NULL:
                    jsonReader.nextNull();
                    break;
                case NUMBER:
                    jsonReader.nextString();
                    break;
                case STRING:
                    String value = jsonReader.nextString();
                    if ("$.url".equals(jsonReader.getPath())
                            || jsonReader.getPath().matches("^\\$\\.entries\\[\\d+\\]\\.url$")) {
                        results.videoUrls.add(value);
                    } else if ("$.webpage_url".equals(jsonReader.getPath())
                            || jsonReader.getPath().matches("^\\$\\.entries\\[\\d+\\]\\.webpage_url$")) {
                        results.pageUrls.add(value);
                    }
                    break;
                default:
                    throw new RuntimeException("unexpected json token " + nextToken);
                }
            }
        }
    }

    /**
     * Writes output to this.tempFile.get().
     *
     * Reads stdout in this thread, stderr in separate thread.
     * see https://github.com/internetarchive/heritrix3/pull/257/files#r279990349
     */
    protected YoutubeDLResults runYoutubeDL(CrawlURI uri) {
        /*
         * --format=best
         *
         * best: Select the best quality format represented by a single file
         * with video and audio.
         * https://github.com/ytdl-org/youtube-dl/blob/master/README.md#format-selection
         */
        ProcessBuilder pb = new ProcessBuilder("youtube-dl", "--ignore-config",
                "--simulate", "--dump-single-json", "--format=best[height <=? 576]",
                "--no-cache-dir", "--no-playlist",
                "--playlist-end=" + MAX_VIDEOS_PER_PAGE, uri.toString());
        logger.info("running: " + String.join(" ", pb.command()));

        Process proc = null;
        try {
            proc = pb.start();
        } catch (IOException e) {
            logger.log(Level.WARNING, "youtube-dl failed " + pb.command(), e);
            return null;
        }

        Reader err;
        try {
            err = new InputStreamReader(proc.getErrorStream(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        ExecutorService threadPool = Executors.newSingleThreadExecutor();

        Future<String> future = threadPool.submit(new Callable<String>() {
            @Override
            public String call() throws IOException {
                return readToEnd(err);
            }
        });

        YoutubeDLResults results = new YoutubeDLResults(tempfile.get());

        try {
            try {
                streamYdlOutput(proc.getInputStream(), results);
            } catch (EOFException e) {
                try {
                    // this happens when there was no json output, which means no videos
                    // were found, totally normal
                    logger.log(Level.FINE, "problem parsing json from youtube-dl " + pb.command() + " " + future.get());
                } catch (InterruptedException e1) {
                    throw new IOException(e1);
                } catch (ExecutionException e1) {
                    throw new IOException(e1);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "problem reading output from youtube-dl " + pb.command(),
                    e);
            return null;
        } finally {
            try {
                // the process should already have completed
                proc.waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                logger.warning("youtube-dl still running? killing it");
                proc.destroyForcibly();
            }
            threadPool.shutdown();
        }

        return results;
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        // We have some special sauce (not actually extraction) to apply to
        // "youtube-dl"-annotated urls. See extract().
        if (findYdlAnnotation(uri) != null) {
            return true;
        }

        // Otherwise, check if we want to run youtube-dl on the url.
        return shouldExtract(uri);
    }

    /**
     * Returns {@code true} if we should run youtube-dl on this url. We run
     * youtube-dl on html 200s that are not too huge.
     */
    protected boolean shouldExtract(CrawlURI uri) {
        if (uri.getFetchStatus() != 200) {
            return false;
        }

        // see https://github.com/internetarchive/brozzler/blob/65fad5e8b/brozzler/ydl.py#L48
        if (uri.getContentLength() <= 0 || uri.getContentLength() >= 200000000) {
            return false;
        }

        String mime = uri.getContentType().toLowerCase();
        if (mime.startsWith("text/html")
                || mime.startsWith("application/xhtml")
                || mime.startsWith("text/vnd.wap.wml")
                || mime.startsWith("application/vnd.wap.wml")
                || mime.startsWith("application/vnd.wap.xhtml")) {
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldBuildRecord(CrawlURI uri) {
        // should build record for containing page, which has an
        // annotation like "youtube-dl:3" (no slash)
        String annotation = findYdlAnnotation(uri);
        return annotation != null && !annotation.contains("/");
    }

    @Override
    public WARCRecordInfo buildRecord(CrawlURI curi, URI concurrentTo)
            throws IOException {
        final String timestamp =
                ArchiveUtils.getLog14Date(curi.getFetchBeginTime());

        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.metadata);
        recordInfo.setRecordId(BaseWARCRecordBuilder.generateRecordID());
        if (concurrentTo != null) {
            recordInfo.addExtraHeader(HEADER_KEY_CONCURRENT_TO,
                    "<" + concurrentTo + ">");
        }
        recordInfo.setUrl("youtube-dl:" + curi);
        recordInfo.setCreate14DigitDate(timestamp);
        recordInfo.setMimetype("application/vnd.youtube-dl_formats+json;charset=utf-8");
        recordInfo.setEnforceLength(true);

        tempfile.get().seek(0);
        InputStream inputStream = Channels.newInputStream(tempfile.get().getChannel());
        recordInfo.setContentStream(inputStream);
        recordInfo.setContentLength(tempfile.get().length());

        logger.info("built record timestamp=" + timestamp + " url=" + recordInfo.getUrl());

        return recordInfo;
    }

    public static void main(String[] args) throws IOException {
        /*
        File t = File.createTempFile("ydl", ".json");
        try (RandomAccessFile f = new RandomAccessFile(t, "rw")) {
            t.delete();
            f.write("hello!\n".getBytes());
            System.out.println("length: " + f.length());
            System.out.println("tell: " + f.getFilePointer());
            f.seek(0);
            System.out.println("tell: " + f.getFilePointer());
            String l = f.readLine();
            System.out.println("read line: " + l);
            System.out.println("tell: " + f.getFilePointer());
        }
         */

        ExtractorYoutubeDL e = new ExtractorYoutubeDL();

        FileInputStream in = new FileInputStream("/tmp/ydl-single-video.json");
        YoutubeDLResults results = new YoutubeDLResults(e.tempfile.get());
        e.streamYdlOutput(in, results);
        System.out.println("video urls: " + results.videoUrls);
        System.out.println("page urls: " + results.pageUrls);

        results.jsonFile.seek(0);
        byte[] buf = new byte[4096];
        while (true) {
            int n = results.jsonFile.read(buf);
            if (n < 0) {
                break;
            }
            System.out.write(buf, 0, n);
        }

        in = new FileInputStream("/tmp/ydl-uncgreensboro-limited.json");
        results = new YoutubeDLResults(e.tempfile.get());
        e.streamYdlOutput(in, results);
        System.out.println("video urls: " + results.videoUrls);
        System.out.println("page urls: " + results.pageUrls);

        results.jsonFile.seek(0);
        while (true) {
            int n = results.jsonFile.read(buf);
            if (n < 0) {
                break;
            }
            System.out.write(buf, 0, n);
        }

    }
}
