package org.archive.modules.extractor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;

public class ExtractorRobotsTxt extends ContentExtractor {
    private static final Logger LOGGER = Logger
            .getLogger(ExtractorRobotsTxt.class.getName());
    private static final Pattern ROBOTS_PATTERN = Pattern
            .compile("^https?://[^/]+/robots.txt$");
    private static final Pattern SITEMAP_PATTERN = Pattern
            .compile("(?i)Sitemap:\\s*(.+)$");

    public static final String ANNOTATION_IS_SITEMAP = "isSitemap";

    @Override
    protected boolean shouldExtract(CrawlURI uri) {
    	boolean shouldExtract = false;
    	if (uri.isPrerequisite()) {
    		shouldExtract = ROBOTS_PATTERN.matcher(uri.getURI()).matches();
            LOGGER.finest("Checked prerequisite " + uri + " GOT " + shouldExtract);
    	}
        return shouldExtract;
    }

    public List<String> parseRobotsTxt(InputStream input) {
        ArrayList<String> links = new ArrayList<>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        try {
            String line;
            Matcher matcher;
            while ((line = reader.readLine()) != null) {
                matcher = SITEMAP_PATTERN.matcher(line);
                if (matcher.matches()) {
                    links.add(matcher.group(1));
                }
            }
        } catch (IOException e) {
            LOGGER.warning(e.toString());
        }
        return links;
    }

    @Override
    protected boolean innerExtract(CrawlURI curi) {
        try {
            // Parse the robots for the sitemaps.
            List<String> links = parseRobotsTxt(
                    curi.getRecorder()
                    .getContentReplayInputStream());
            LOGGER.finest("Checked " + curi + " GOT " + links);

            // Get the max outlinks (needed by add method):
            int max = getExtractorParameters().getMaxOutlinks();

            // Accrue links:
            for (String link : links) {
                try {
                    // We've found a sitemap:
                    LOGGER.fine("Found site map: " + link);
                    numberOfLinksExtracted.incrementAndGet();

                    // Add links but using the cloned CrawlURI as the crawl
                    // context.
                    CrawlURI newCuri = addRelativeToBase(curi, max, link,
                            LinkContext.MANIFEST_MISC, Hop.MANIFEST);

                    if (newCuri == null) {
                        continue;
                    }

                    // Annotate as a Site Map:
                    newCuri.getAnnotations().add(
                            ExtractorRobotsTxt.ANNOTATION_IS_SITEMAP);

                } catch (URIException e) {
                    logUriError(e, curi.getUURI(), link);
                }
            }

            // Return number of links discovered:
            return !links.isEmpty();

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, curi.getURI(), e);
            curi.getNonFatalFailures().add(e);
        }
        return false;
    }

}
