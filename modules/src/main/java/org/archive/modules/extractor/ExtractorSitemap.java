package org.archive.modules.extractor;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.url.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.ContentExtractor;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;

import crawlercommons.sitemaps.AbstractSiteMap;
import crawlercommons.sitemaps.SiteMap;
import crawlercommons.sitemaps.SiteMapIndex;
import crawlercommons.sitemaps.SiteMapParser;
import crawlercommons.sitemaps.SiteMapURL;
import crawlercommons.sitemaps.UnknownFormatException;

/**
 * 
 * @author Andrew Jackson <Andrew.Jackson@bl.uk>
 *
 */
public class ExtractorSitemap extends ContentExtractor {
    private static final Logger LOGGER = Logger
            .getLogger(ExtractorSitemap.class.getName());

    /**
     * If urlPattern is not null then any url marked as a sitemap and matching the pattern is
     * assumed to be a sitemap. Otherwise the mime-type is checked (must be "text/xml" or "application/xml") and the
     * file is "sniffed" for the expected start of a sitemap file.
     */
    private String urlPattern = null;

    /**
     * If true, all urls in the sitemap file are extracted, regardless of whether or not they obey the scoping rules
     * specified in the sitemap protocol (https://www.sitemaps.org/protocol.html).
     */
    private boolean enableLenientExtraction = false;

    /* (non-Javadoc)
     * @see org.archive.modules.extractor.ContentExtractor#shouldExtract(org.archive.modules.CrawlURI)
     */
    @Override
    protected boolean shouldExtract(CrawlURI uri) {
        // If declared as such:
        if (uri.getAnnotations()
                .contains(ExtractorRobotsTxt.ANNOTATION_IS_SITEMAP)) {
            if (uri.is2XXSuccess()) {
                LOGGER.fine("This url (" + uri
                        + ") is declared to be a sitemap (via robots.txt) and is a HTTP 200.");
                return true;
            } else {
                LOGGER.fine("This url (" + uri
                        + ") is declared to be a sitemap (via robots.txt) but is a HTTP "
                        + uri.getFetchStatus() + ".");
            }
        }

        if (urlPattern != null &&  uri.getURI().matches(urlPattern)) {
            return true;
        }

        // Via content type:
        String mimeType = uri.getContentType();
        if (mimeType != null ) {
            // Looks like XML:
            if (mimeType.toLowerCase().startsWith("text/xml")
                    || mimeType.toLowerCase().startsWith("application/xml")) {

                // check if content starts with xml preamble "<?xml" and does
                // contain "<urlset " or "<sitemapindex" early in the content
                String contentStartingChunk = uri.getRecorder()
                        .getContentReplayPrefixString(400);
                if (contentStartingChunk.matches("(?is)[\\ufeff]?<\\?xml\\s.*")
                        && contentStartingChunk.matches(
                                "(?is).*(?:<urlset|<sitemapindex[>\\s]).*")) {
                    LOGGER.info("Based on content sniffing, this is a sitemap: "
                            + uri);
                    return true;
                }
            }
        }
        
        // Otherwise, not
        return false;
    }

    /* (non-Javadoc)
     * @see org.archive.modules.extractor.ContentExtractor#innerExtract(org.archive.modules.CrawlURI)
     */
    @Override
    protected boolean innerExtract(CrawlURI uri) {
        // Parse the sitemap:
        AbstractSiteMap sitemap = parseSiteMap(uri);

        // Did that work?
        if (sitemap != null) {
            // Process results:
            if (sitemap.isIndex()) {
                final Collection<AbstractSiteMap> links = ((SiteMapIndex) sitemap)
                        .getSitemaps();
                for (final AbstractSiteMap asm : links) {
                    if (asm == null) {
                        continue;
                    }
                    this.recordOutlink(uri, asm.getUrl(), asm.getLastModified(),
                            true);
                }
            } else {
                final Collection<SiteMapURL> links = ((SiteMap) sitemap)
                        .getSiteMapUrls();
                for (final SiteMapURL url : links) {
                    if (url == null) {
                        continue;
                    }
                    this.recordOutlink(uri, url.getUrl(), url.getLastModified(),
                            false);
                }
            }
        }

        return false;
    }

    /**
     * Parse the sitemap using the Crawler Commons content-sniffing parser.
     * 
     * @param uri
     * @return
     */
    private AbstractSiteMap parseSiteMap(CrawlURI uri) {
        // The thing we will create:
        AbstractSiteMap sitemap = null;
        // allow partial extraction
        SiteMapParser smp = new SiteMapParser(!isEnableLenientExtraction(), true);
        // Parse it up:
        try {
            // Sitemaps are not supposed to be bigger than 50MB (according to
            // Google) so if we hit problems we can implement that limit:
            byte[] content = IOUtils.toByteArray(
                    uri.getRecorder().getContentReplayInputStream());
            if (content.length > 52428800) {
                LOGGER.warning("Found sitemap exceeding 50MB " + uri + " "
                        + content.length);
            }
            // Now we can process it:
            sitemap = smp.parseSiteMap(content, new URL(uri.getURI()));
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "I/O Exception when parsing sitemap " + uri, e);
        } catch (UnknownFormatException e) {
            LOGGER.log(Level.WARNING,
                    "UnknownFormatException when parsing sitemap " + uri, e);
        }
        return sitemap;
    }

    private void recordOutlink(CrawlURI curi, URL newUri, Date lastModified,
            boolean isSitemap) {
        try {
            // Get the max outlinks (needed by add method):
            //
            // Because sitemaps are really important we excuse this extractor
            // from the general setting:
            //
            // getExtractorParameters().getMaxOutlinks();
            //
            // And instead use the maximum that is allowed for a sitemap:
            int max = 50000;

            // Add the URI:
        	// Adding 'regular' URL listed in the sitemap
            CrawlURI newCuri = addRelativeToBase(curi, max, newUri.toString(),
                    LinkContext.MANIFEST_MISC, Hop.MANIFEST);

            if (newCuri == null) {
                return;
            }

            if (isSitemap) {
                // Annotate as a Site Map:
                newCuri.getAnnotations().add(
                        ExtractorRobotsTxt.ANNOTATION_IS_SITEMAP);
            }
            
            // And log about it:
            LOGGER.fine("Found " + newUri + " from " + curi + " Dated "
                    + lastModified + " and with isSitemap = " + isSitemap);
            // Count it:
            numberOfLinksExtracted.incrementAndGet();
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), newUri.toString());
        }

    }

    public String getUrlPattern() {
        return urlPattern;
    }

    /**
     * If urlPattern is not null then any url marked as a sitemap and matching the pattern is
     * assumed to be a sitemap. Otherwise the mime-type is checked (must be "text/xml" or "application/xml") and the
     * file is "sniffed" for the expected start of a sitemap file.
     * @param urlPattern the url pattern to match
     */
    public void setUrlPattern(String urlPattern) {
        this.urlPattern = urlPattern;
    }

    public boolean isEnableLenientExtraction() {
        return enableLenientExtraction;
    }

    /**
     * If true, all urls in the sitemap file are extracted, regardless of whether or not they obey the scoping rules
     * specified in the sitemap protocol (https://www.sitemaps.org/protocol.html).
     * @param enableLenientExtraction whether to extract all urls
     */
    public void setEnableLenientExtraction(boolean enableLenientExtraction) {
        this.enableLenientExtraction = enableLenientExtraction;
    }


}
