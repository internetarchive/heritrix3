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

import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStatusCodes;


/**
 * Extracts link from the fetched content of a URI, as opposed to its headers.
 * 
 * @author pjack
 */
public abstract class ContentExtractor extends Extractor {


    /**
     * Extracts links 
     */
    final protected void extract(CrawlURI uri) {
        boolean finished = innerExtract(uri);
        if (finished) {
            uri.linkExtractorFinished();
        }
    }

    /**
     * Determines if links should be extracted from the given URI. This method
     * performs three checks. The first check runs only if
     * {@link ExtractorParameters#getExtractIndependently()} is false. It checks
     * {@link ExtractorURI#hasBeenLinkExtracted()} result. If that result is
     * true, then this method returns false, as some other extractor has claimed
     * that links are already extracted.
     * 
     * <p>
     * Next, this method checks that the content length of the URI is greater
     * than zero (in other words, that there is actually content for links to be
     * extracted from). If the content length of the URI is zero or less, then
     * this method returns false.
     * 
     * <p>
     * Finally, this method delegates to {@link #innerExtract(ExtractorURI)} and
     * returns that result.
     * 
     * @param uri
     *            the URI to check
     * @return true if links should be extracted from the URI, false otherwise
     */
    final protected boolean shouldProcess(CrawlURI uri) {
        if (!getExtractorParameters().getExtractIndependently()
                && uri.hasBeenLinkExtracted()) {
            return false;
        }
        if (uri.getContentLength() <= 0) {
            return false;
        }
        if (!getExtractorParameters().getExtract404s() 
                && uri.getFetchStatus()==FetchStatusCodes.S_NOT_FOUND) {
            return false; 
        }
        if (!shouldExtract(uri)) {
            return false;
        }
        return true;
    }

    /**
     * Determines if otherwise valid URIs should have links extracted or not.
     * The given URI will have content length greater than zero. Subclasses
     * should implement this method to perform additional checks. For instance,
     * the {@link ExtractorHTML} implementation checks that the content-type of
     * the given URI is text/html.
     * 
     * @param uri
     *            the URI to check
     * @return true if links should be extracted from that URI, false otherwise
     */
    protected abstract boolean shouldExtract(CrawlURI uri);

    
    /**
     * Actually extracts links.  The given URI will have passed the three
     * checks described in {@link #shouldProcess(ExtractorURI)}.  Subclasses
     * should implement this method to discover outlinks in the URI's 
     * content stream.  For instance, {@link ExtractorHTML} extracts links
     * from Anchor tags and so on.
     * 
     * <p>This method should only return true if extraction completed 
     * successfully.  If not (for instance, if an IO error occurred), then
     * this method should return false.  Returning false indicates to the
     * pipeline that downstream extractors should attempt to extract links
     * themselves.  Returning true indicates that downstream extractors
     * should be skipped.
     * 
     * @param uri   the URI whose links to extract
     * @return  true if link extraction finished; false if downstream
     * extractors should attempt to extract links
     */
    protected abstract boolean innerExtract(CrawlURI uri);
    
}
