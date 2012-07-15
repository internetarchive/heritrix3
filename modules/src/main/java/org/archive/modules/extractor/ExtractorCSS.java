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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;

/**
 * This extractor is parsing URIs from CSS type files.
 * The format of a CSS URL value is 'url(' followed by optional white space
 * followed by an optional single quote (') or double quote (") character
 * followed by the URL itself followed by an optional single quote (') or
 * double quote (") character followed by optional white space followed by ')'.
 * Parentheses, commas, white space characters, single quotes (') and double
 * quotes (") appearing in a URL must be escaped with a backslash:
 * '\(', '\)', '\,'. Partial URLs are interpreted relative to the source of
 * the style sheet, not relative to the document. <a href="http://www.w3.org/TR/REC-CSS1#url">
 * Source: www.w3.org</a>
 *
 * NOTE: This processor may open a ReplayCharSequence from the 
 * CrawlURI's Recorder, without closing that ReplayCharSequence, to allow
 * reuse by later processors in sequence. In the usual (Heritrix) case, a 
 * call after all processing to the Recorder's endReplays() method ensures
 * timely close of any reused ReplayCharSequences. Reuse of this processor
 * elsewhere should ensure a similar cleanup call to Recorder.endReplays()
 * occurs. 
 * 
 * @author Igor Ranitovic
 *
 **/

public class ExtractorCSS extends ContentExtractor {


    @SuppressWarnings("unused")
    private static final long serialVersionUID = 2L;

    private static Logger logger =
        Logger.getLogger("org.archive.crawler.extractor.ExtractorCSS");

    private static String ESCAPED_AMP = "&amp";
    // CSS escapes: "Parentheses, commas, whitespace characters, single 
    // quotes (') and double quotes (") appearing in a URL must be 
    // escaped with a backslash"
    protected static final String CSS_BACKSLASH_ESCAPE = "\\\\([,'\"\\(\\)\\s])";
    
    /**
     *  CSS URL extractor pattern.
     *
     *  This pattern extracts URIs for CSS files
     **/
//    static final String CSS_URI_EXTRACTOR =
//        "url[(]\\s*([\"\']?)([^\\\"\\'].*?)\\1\\s*[)]";
    protected static final String CSS_URI_EXTRACTOR =    
    "(?i)(?:@import (?:url[(]|)|url[(])\\s*([\\\"\']?)" + // G1
    "([^\\\"\'].{0,"+UURI.MAX_URL_LENGTH+"}?)\\1\\s*[);]"; // G2
    // GROUPS:
    // (G1) optional ' or "
    // (G2) URI
    

    /**
     */
    public ExtractorCSS() {
    }

    
    @Override
    protected boolean shouldExtract(CrawlURI curi) {
        String mimeType = curi.getContentType();
        if (mimeType == null) {
            return false; // FIXME: This check should be unnecessary
        }
        if ((mimeType.toLowerCase().indexOf("css") < 0) &&
                (!curi.toString().toLowerCase().endsWith(".css"))) {
            return false;
        }
        return true;
    }
    
    /**
     * @param curi Crawl URI to process.
     */
    public boolean innerExtract(CrawlURI curi) {
        try {
            ReplayCharSequence cs = curi.getRecorder().getContentReplayCharSequence();
            numberOfLinksExtracted.addAndGet(
                processStyleCode(this, curi, cs));
            // Set flag to indicate that link extraction is completed.
            return true;
        } catch (IOException e) {
            logger.log(Level.WARNING, "Problem with ReplayCharSequence: " + e.getMessage(), e);
        }
        return false; 
    }

    public static long processStyleCode(Extractor ext, 
            CrawlURI curi, CharSequence cs) {
        long foundLinks = 0;
        Matcher uris = null;
        String cssUri;
        try {
            uris = TextUtils.getMatcher(CSS_URI_EXTRACTOR, cs);
            while (uris.find()) {
                cssUri = uris.group(2);
                // TODO: Escape more HTML Entities.
                cssUri = TextUtils.replaceAll(ESCAPED_AMP, cssUri, "&");
                // Remove backslashes when used as escape character in CSS URL
                cssUri = TextUtils.replaceAll(CSS_BACKSLASH_ESCAPE, cssUri,
                        "$1");
                foundLinks++;
                int max = ext.getExtractorParameters().getMaxOutlinks();
                try {
                    Link.addRelativeToBase(curi, max, cssUri, 
                            LinkContext.EMBED_MISC, Hop.EMBED);
                } catch (URIException e) {
                    ext.logUriError(e, curi.getUURI(), cssUri);
                }
            }
        } catch (StackOverflowError e) {
            DevUtils.warnHandle(e, "ExtractorCSS StackOverflowError");
        } finally {
            TextUtils.recycleMatcher(uris);
        }
        return foundLinks;
    }
}
