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

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonStreamParser;

/**
 * Extracts links to media by running youtube-dl in a subprocess. Runs only on
 * html.
 *
 * <p>
 * Keeps a log of containing pages and media captured as a result of youtube-dl
 * extraction. The format of the log is as follows:
 *
 * <pre>[timestamp] [media-http-status] [media-length] [media-mimetype] [media-digest] [media-timestamp] [media-url] [annotation] [containing-page-digest] [containing-page-timestamp] [containing-page-url] [seed-url]</pre>
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
public class ExtractorYoutubeDL extends Extractor implements Lifecycle {
    private static Logger logger =
            Logger.getLogger(ExtractorYoutubeDL.class.getName());

    protected static final String YDL_CONTAINING_PAGE_DIGEST = "ydl-containing-page-digest";
    protected static final String YDL_CONTAINING_PAGE_TIMESTAMP = "ydl-containing-page-timestamp";
    protected static final String YDL_CONTAINING_PAGE_URI = "ydl-containing-page-uri";

    protected transient Logger ydlLogger = null;

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
            List<JsonObject> ydlJsons = runYoutubeDL(uri);
            if (ydlJsons != null && !ydlJsons.isEmpty()) {
                for (JsonObject json: ydlJsons) {
                    if (json.get("url") != null) {
                        String videoUrl = json.get("url").getAsString();
                        addVideoOutlink(uri, json, videoUrl);
                    }
                }
                String annotation = "youtube-dl:" + ydlJsons.size();
                uri.getAnnotations().add(annotation);
                logContainingPage(uri, annotation);
            }
        }
    }

    protected void addVideoOutlink(CrawlURI uri, JsonObject json,
            String videoUrl) {
        try {
            UURI dest = UURIFactory.getInstance(uri.getUURI(), videoUrl);
            CrawlURI link = uri.createCrawlURI(dest, LinkContext.EMBED_MISC,
                    Hop.EMBED);

            // annotation
            String annotation = "youtube-dl:1/1";
            if (!json.get("playlist_index").isJsonNull()) {
                annotation = "youtube-dl:" + json.get("playlist_index") + "/"
                        + json.get("n_entries");
            }
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

    static protected class ProcessOutput {
        public String stdout;
        public String stderr;
    }

    // read stdout in this thread, stderr in separate thread
    // see https://github.com/internetarchive/heritrix3/pull/257/files#r279990349
    protected ProcessOutput readOutput(Process proc) throws IOException {
        ProcessOutput output = new ProcessOutput();

        Reader err = new InputStreamReader(proc.getErrorStream(), "UTF-8");
        InputStreamReader out = new InputStreamReader(proc.getInputStream(), "UTF-8");
        ExecutorService threadPool = Executors.newSingleThreadExecutor();

        Future<String> future = threadPool.submit(new Callable<String>() {
            @Override
            public String call() throws IOException {
                return readToEnd(err);
            }
        });

        output.stdout = readToEnd(out);

        try {
            output.stderr = future.get();
        } catch (InterruptedException e) {
            throw new IOException(e); // :shrug:
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw new IOException(e);
            }
        } finally {
            threadPool.shutdown();
        }

        return output;
    }

    /**
     *
     * @param uri
     * @return list of json blobs returned by {@code youtube-dl --dump-json}, or
     *         empty list if no videos found, or failure
     */
    protected List<JsonObject> runYoutubeDL(CrawlURI uri) {
        /*
         * --format=best
         *
         * best: Select the best quality format represented by a single file
         * with video and audio.
         * https://github.com/ytdl-org/youtube-dl/blob/master/README.md#format-selection
         */
        ProcessBuilder pb = new ProcessBuilder("youtube-dl", "--ignore-config",
                "--simulate", "--dump-json", "--format=best", uri.toString());
        logger.fine("running " + pb.command());

        Process proc = null;
        try {
            proc = pb.start();
        } catch (IOException e) {
            logger.log(Level.WARNING, "youtube-dl failed " + pb.command(), e);
            return null;
        }

        ProcessOutput output;
        try {
            output = readOutput(proc);
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "problem reading output from youtube-dl " + pb.command(),
                    e);
            return null;
        }

        try {
            if (proc.waitFor() != 0) {
                /*
                 * youtube-dl is noisy when it fails to find a video. I guess
                 * the assumption is that you're running it on pages you know
                 * have videos. We could be hiding real errors in some cases
                 * but it's just too much noise to log this at WARNING level.
                 */
                logger.fine("youtube-dl exited with status "
                        + proc.waitFor() + " " + pb.command()
                        + "\n=== stdout ===\n" + output.stdout
                        + "\n=== stderr ===\n" + output.stderr);
                return null;
            }
        } catch (InterruptedException e) {
            proc.destroyForcibly();
        }

        List<JsonObject> ydlJsons = new ArrayList<JsonObject>();
        JsonStreamParser parser = new JsonStreamParser(output.stdout);
        try {
            while (parser.hasNext()) {
                ydlJsons.add((JsonObject) parser.next());
            }
        } catch (JsonParseException e) {
            // sometimes we get no output at all from youtube-dl, which
            // manifests as a JsonIOException
            logger.log(Level.FINE,
                    "problem parsing json from youtube-dl " + pb.command()
                            + "\n=== stdout ===\n" + output.stdout
                            + "\n=== stderr ===\n" + output.stderr,
                    e);
            return null;
        }

        return ydlJsons;
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
}
