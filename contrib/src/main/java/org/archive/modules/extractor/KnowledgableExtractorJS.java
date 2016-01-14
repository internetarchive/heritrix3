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
     * Wraps a {@link CrawlURI}, allowing baseURI to be overridden, without
     * changing the underlying CrawlURI. The only methods implemented are the
     * ones necessary for {@link ExtractorJS} to work properly.
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
        
        // extract youtube videoid from youtube javascript embed and create link
        // for watch page
        m = TextUtils.getMatcher("new[\\s]+YT\\.Player\\(['\"][^'\"]+['\"],[\\s]+\\{[\\n\\s\\w:'\",]+videoId:[\\s]+['\"]([\\w-]+)['\"],", cs);

        if (m.find()) {
            String videoId = m.group(1);

            String newUri = "https://www.youtube.com/watch?v=" + videoId;

            try {
                addRelativeToBase(curi, ext.getExtractorParameters().getMaxOutlinks(), newUri, LinkContext.INFERRED_MISC,
                        Hop.INFERRED);
            } catch (URIException e) {
                // no way this should happen
                throw new IllegalStateException(newUri, e);
            }
        }
        
        TextUtils.recycleMatcher(m);
        
        return super.considerStrings(ext, baseUri, cs, handlingJSFile);
    }

}
