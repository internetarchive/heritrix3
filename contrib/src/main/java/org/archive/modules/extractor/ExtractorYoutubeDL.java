package org.archive.modules.extractor;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

public class ExtractorYoutubeDL extends Extractor implements Lifecycle {
    
    private static Logger logger =
            Logger.getLogger(ExtractorYoutubeDL.class.getName());
    
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
            for (JsonObject json: ydlJsons) {
                if (json.get("url") != null) {
                    String videoUrl = json.get("url").getAsString();
                    addVideoOutlink(uri, json, videoUrl);
                }
            }
        }
    }

    private void addVideoOutlink(CrawlURI uri, JsonObject json,
            String videoUrl) {
        try {
            UURI dest = UURIFactory.getInstance(uri.getUURI(), videoUrl);
            CrawlURI link = uri.createCrawlURI(dest, LinkContext.EMBED_MISC,
                    Hop.EMBED);
            String annotation = "youtube-dl:1/1";
            if (!json.get("playlist_index").isJsonNull()) {
                annotation = "youtube-dl:" + json.get("playlist_index") + "/"
                        + json.get("n_entries");
            }
            link.getAnnotations().add(annotation);
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

        // 2019-04-29T21:14:13.139Z     1         53 dns:www.indiewire.com P https://www.indiewire.com/2019/04/gemini-man-trailer-will-smith-ang-lee-1202126973/ text/dns #015 20190429211412388+219 sha1:WAY2F6QNMMIXRR2NWGQH2COJIAKRQO2S - - {"warcFilename":"WEB-20190429211413120-00000-48039~10.30.67.32~6440.warc.gz","warcFileOffset":1530}
        ydlLogger.info(
                uri.getFetchStatus()
                + " " + length
                + " " + MimetypeUtils.truncate(uri.getContentType())
                + " " + uri.getContentDigestSchemeString()
                + " " + ydlAnnotation
                + " " + ArchiveUtils.get17DigitDate(uri.getFetchBeginTime())
                + " " + uri
                + " " + containingPageUri(uri)
                + " " + seed);
    }

    protected String containingPageUri(CrawlURI uri) {
        String u = (String) uri.getData().get("containingPage");
        if (u != null) {
            return u;
        } else {
            return uri.getVia().toString();
        }
    }

    protected void doRedirectInheritance(CrawlURI uri, String ydlAnnotation) {
        for (CrawlURI link: uri.getOutLinks()) {
            if (link.getLastHop() == "R") {
                link.getAnnotations().add(ydlAnnotation);
                link.getData().put("containingPage", containingPageUri(uri));
            }
        }
    }

    /**
     * 
     * @param uri
     * @return list of json blobs returned by {@code youtube-dl --dump-json}, or
     *         empty list if no videos found, or failure
     */
    @SuppressWarnings("unchecked")
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
            return (List<JsonObject>) Collections.EMPTY_LIST;
        }

        String stdout = null;
        String stderr = null;
        try {
            stdout = readToEnd(
                    new InputStreamReader(proc.getInputStream(), "UTF-8"));
            stderr = readToEnd(
                    new InputStreamReader(proc.getErrorStream(), "UTF-8"));
        } catch (IOException e) {
            logger.log(Level.WARNING,
                    "problem reading output from youtube-dl " + pb.command(),
                    e);
            return (List<JsonObject>) Collections.EMPTY_LIST;
        }

        try {
            if (proc.waitFor() != 0) {
                if (!stderr.contains("ERROR: Unsupported URL:")
                        && !stderr.contains("ERROR: There's no video in this tweet")) {
                    logger.warning("youtube-dl exited with status "
                        + proc.waitFor() + " " + pb.command()
                        + "\n=== stdout ===\n" + stdout
                        + "\n=== stderr ===\n" + stderr);
                }
                // else it just didn't find a video
                return (List<JsonObject>) Collections.EMPTY_LIST;
            }
        } catch (InterruptedException e) {
            proc.destroyForcibly();
        }

        // logger.info("youtube-dl stdout:\n" + stdout);
        // logger.info("youtube-dl stderr:\n" + stderr);

        ArrayList<JsonObject> ydlJsons = new ArrayList<JsonObject>();
        JsonStreamParser parser = new JsonStreamParser(stdout);
        try {
            while (parser.hasNext()) {
                ydlJsons.add((JsonObject) parser.next());
            }
        } catch (JsonParseException e) {
            logger.log(Level.WARNING,
                    "problem parsing json from youtube-dl " + pb.command()
                            + "\n=== stdout ===\n" + stdout
                            + "\n=== stderr ===\n" + stderr,
                    e);
            return (List<JsonObject>) Collections.EMPTY_LIST;
        }

        return ydlJsons;
    }

    /**
     * Run youtube-dl on html 200 responses.
     * 
     * @see ExtractorHTML#shouldExtract(CrawlURI)
     */
    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        // We have some special sauce (not actually extraction) to apply to
        // "youtube-dl"-annotated urls, see extract().
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
