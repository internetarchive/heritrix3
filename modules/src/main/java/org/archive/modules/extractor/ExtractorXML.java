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
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringEscapeUtils;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.util.UriUtils;

/**
 * A simple extractor which finds HTTP URIs inside XML/RSS files,
 * inside attribute values and simple elements (those with only
 * whitespace + HTTP URI + whitespace as contents).
 * 
 * NOTE: This processor may open a ReplayCharSequence from the 
 * CrawlURI's Recorder, without closing that ReplayCharSequence, to allow
 * reuse by later processors in sequence. In the usual (Heritrix) case, a 
 * call after all processing to the Recorder's endReplays() method ensures
 * timely close of any reused ReplayCharSequences. Reuse of this processor
 * elsewhere should ensure a similar cleanup call to Recorder.endReplays()
 * occurs. 
 * 
 * @contributor gojomo
 */
public class ExtractorXML extends ContentExtractor {

    private static final long serialVersionUID = 3L;

    private static Logger logger =
        Logger.getLogger(ExtractorXML.class.getName());

    static final Pattern XML_URI_EXTRACTOR = Pattern.compile(    
    "(?i)[\"\'>]\\s*(" + UriUtils.NAIVE_LIKELY_URI_PATTERN + ")\\s*[\"\'<]"); 
    // GROUPS:
    // (G1) URI

    /**
     * @param name
     */
    public ExtractorXML() {
    }

    
    @Override
    protected boolean shouldExtract(CrawlURI curi) {
        String mimeType = curi.getContentType();

        try {
            return mimeType != null
                    && (mimeType.toLowerCase().indexOf("xml") >= 0
                            || curi.toString().toLowerCase().endsWith(".rss")
                            || curi.toString().toLowerCase().endsWith(".xml") 
                            || (curi.getRecorder().getReplayCharSequence().length() >= 8 
                                    && curi.getRecorder().getReplayCharSequence().subSequence(0, 8).toString().matches("[\\ufeff]?<\\?xml\\s.*")));
        } catch (IOException e) {
            logger.severe(curi + " - failed getting ReplayCharSequence: " + e);
            return false;
        } 
    }
    
    /**
     * @param curi Crawl URI to process.
     */
    @Override
    protected boolean innerExtract(CrawlURI curi) {
        ReplayCharSequence cs = null;
        try {
            cs = curi.getRecorder().getReplayCharSequence();
            numberOfLinksExtracted.addAndGet(processXml(this, curi, cs));
            // Set flag to indicate that link extraction is completed.
            return true;
        } catch (IOException e) {
            logger.severe("Failed getting ReplayCharSequence: " + e.getMessage());
        }
        return false; 
    }

    public static long processXml(Extractor ext, 
            CrawlURI curi, CharSequence cs) {
        long foundLinks = 0;
        Matcher matcher = XML_URI_EXTRACTOR.matcher(cs);
        while (matcher.find()) {
            String xmlUri = StringEscapeUtils.unescapeXml(matcher.group(1));
            if (UriUtils.isLikelyFalsePositive(xmlUri)) {
                continue;
            }
            
            foundLinks++;
            try {
                // treat as speculative, as whether context really 
                // intends to create a followable/fetchable URI is
                // unknown
                int max = ext.getExtractorParameters().getMaxOutlinks();
                Link.addRelativeToBase(curi, max, xmlUri, 
                        LinkContext.SPECULATIVE_MISC, Hop.SPECULATIVE); 
            } catch (URIException e) {
                // There may not be a controller (e.g. If we're being run
                // by the extractor tool).
                ext.logUriError(e, curi.getUURI(), xmlUri);
            }
        }
        return foundLinks;
    }
}
