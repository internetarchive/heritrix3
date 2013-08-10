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
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringEscapeUtils;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.util.TextUtils;
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

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    private static Logger logger =
        Logger.getLogger(ExtractorXML.class.getName());

    static final Pattern XML_URI_EXTRACTOR = Pattern
            .compile("(?s)[\"\'>]\\s*([^<>\\s'\"@]+)\\s*[\"\'<]");
    // GROUPS:
    // (G1) possible uri

    /**
     * @param name
     */
    public ExtractorXML() {
    }

    
    @Override
    protected boolean shouldExtract(CrawlURI curi) {
        String mimeType = curi.getContentType();

        // first check for xml mimetype or file extension
        // application/vnd.openxmlformats.* seem to be zip archives
        if (mimeType != null
                && (mimeType.toLowerCase().indexOf("xml") >= 0 && !mimeType
                        .matches("(?i)application/vnd.openxmlformats.*"))
                || curi.toString().toLowerCase().endsWith(".rss")
                || curi.toString().toLowerCase().endsWith(".xml")) {
            return true;
        }
        
        // check if content starts with xml preamble "<?xml" and does not
        // contain "<!doctype html" or "<html" early in the content
        String contentStartingChunk = curi.getRecorder().getContentReplayPrefixString(400);
        if (contentStartingChunk.matches("(?is)[\\ufeff]?<\\?xml\\s.*")
                && !contentStartingChunk.matches("(?is).*(?:<!doctype\\s+html|<html[>\\s]).*")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * @param curi Crawl URI to process.
     */
    @Override
    protected boolean innerExtract(CrawlURI curi) {
        ReplayCharSequence cs = null;
        Charset contentDeclaredEncoding = null; 
        try {
            // if charset not spec'd in http header look for <?xml encoding=""?>
            if (!curi.containsContentTypeCharsetDeclaration()) {
                String contentPrefix = curi.getRecorder().getContentReplayPrefixString(50);
                contentDeclaredEncoding = getContentDeclaredCharset(curi, contentPrefix); 
                if(!curi.getRecorder().getCharset().equals(contentDeclaredEncoding) && contentDeclaredEncoding!=null) {
                    String newContentPrefix = curi.getRecorder().getContentReplayPrefixString(50,contentDeclaredEncoding); 
                    Charset reflexiveCharset = getContentDeclaredCharset(curi, newContentPrefix);
                    if(contentDeclaredEncoding.equals(reflexiveCharset)) {
                        // content-declared charset is self-consistent; use
                        curi.getAnnotations().add("usingCharsetInXML:"+contentDeclaredEncoding);
                        curi.getRecorder().setCharset(contentDeclaredEncoding);
                    } else {
                        // error: declared charset not evident once put into effect
                        curi.getAnnotations().add("inconsistentCharsetInXML:"+contentDeclaredEncoding);
                        // so, ignore in favor of original default
                    }
                }
            }
            cs = curi.getRecorder().getContentReplayCharSequence();
            numberOfLinksExtracted.addAndGet(processXml(this, curi, cs));
            // Set flag to indicate that link extraction is completed.
            return true;
        } catch (IOException e) {
            logger.severe("Failed getting ReplayCharSequence: " + e.getMessage());
        }
        return false; 
    }

    protected Charset getContentDeclaredCharset(CrawlURI curi, String contentPrefix) {
        Matcher m = TextUtils.getMatcher("(?s)<\\?xml\\s+[^>]*encoding=['\"]([^'\"]+)['\"]", contentPrefix);
        String charsetName = null;
        try {
            if(m.find()) {
                charsetName = m.group(1); 
                return Charset.forName(charsetName);
            }
        } catch (IllegalArgumentException iae) {
            logger.log(Level.INFO,"Unknown content-encoding '"+charsetName+"' declared; using default");  
            curi.getAnnotations().add("unsatisfiableCharsetInXML:"+charsetName);
        } finally {
            TextUtils.recycleMatcher(m); 
        }
        return null; 
    }


    public static long processXml(Extractor ext, 
            CrawlURI curi, CharSequence cs) {
        long foundLinks = 0;
        Matcher matcher = XML_URI_EXTRACTOR.matcher(cs);
        while (matcher.find()) {
            String xmlUri = StringEscapeUtils.unescapeXml(matcher.group(1));
            if (UriUtils.isVeryLikelyUri(xmlUri)) {
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
        }
        return foundLinks;
    }
    
}
