package org.archive.modules.extractor;

import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.exception.NestableRuntimeException;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TextUtils;

/**
 * A subclass of {@link ExtractorJS} that has some customized behavior for
 * specific kinds of web pages. As of April 2015, the one special behavior it
 * has is for drupal generated pages. See https://webarchive.jira.com/browse/ARI-4190
 */
public class KnowledgableExtractorJS extends ExtractorJS {

    private static Logger LOGGER = 
            Logger.getLogger(KnowledgableExtractorJS.class.getName());

    /**
     * XXX explain
     * Only wraps the methods we need to work, or it would be very long.
     */
    protected static class CustomizedCrawlURIFacade extends CrawlURI {
        private static final long serialVersionUID = 1l;

        protected CrawlURI wrapped;
        protected UURI baseURI;

        public CustomizedCrawlURIFacade(CrawlURI wrapped, UURI baseURI) {
            super(wrapped.getUURI(), wrapped.getPathFromSeed(), wrapped.getVia(), wrapped.getViaContext());
            this.wrapped = wrapped;
            this.baseURI = baseURI;
        }

        /**
         * @return value set in {@link #KnowledgableExtractorJS(CrawlURI, UURI)}
         */
        @Override
        public UURI getBaseURI() {
            return baseURI;
        }

        /** Delegates to wrapped CrawlURI */
        @Override
        public CrawlURI createCrawlURI(UURI destination, LinkContext context,
                Hop hop) throws URIException {
            return wrapped.createCrawlURI(destination, context, hop);
        }

        /** Delegates to wrapped CrawlURI */
        @Override
        public Collection<CrawlURI> getOutLinks() {
            return wrapped.getOutLinks();
        }

        /** Delegates to wrapped CrawlURI */
        @Override
        public void incrementDiscardedOutLinks() {
            wrapped.incrementDiscardedOutLinks();
        }
    }

    public long considerStrings(Extractor ext, 
            CrawlURI curi, CharSequence cs, boolean handlingJSFile) {

        CrawlURI baseUri = curi;

        Matcher m = TextUtils.getMatcher("jQuery\\.extend\\(Drupal\\.settings,[^'\"]*['\"]basePath['\"]:[^'\"]*['\"]([^'\"]+)['\"]", cs);
        if (m.find()) {
            String basePath = m.group(1);
            try {
                basePath = StringEscapeUtils.unescapeJavaScript(basePath);
            } catch (NestableRuntimeException e) {
                LOGGER.log(Level.WARNING, "problem unescaping purported drupal basePath '" + basePath + "'", e);
            }

            try {
                UURI baseUURI =  UURIFactory.getInstance(curi.getUURI(), basePath);
                baseUri = new CustomizedCrawlURIFacade(curi, baseUURI);
            } catch (URIException e) {
                LOGGER.log(Level.WARNING, "problem creating UURI from drupal basePath '" + basePath + "'", e);
            }
        }
        TextUtils.recycleMatcher(m);

        return super.considerStrings(ext, baseUri, cs, handlingJSFile);
    }

}
