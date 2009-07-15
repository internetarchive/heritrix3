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

import org.apache.commons.codec.DecoderException;
import org.apache.commons.httpclient.URIException;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.ProcessorURI;
import org.archive.net.LaxURLCodec;
import org.archive.net.UURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;

import static org.archive.modules.extractor.Hop.SPECULATIVE;
import static org.archive.modules.extractor.LinkContext.JS_MISC;

/**
 * Processes Javascript files for strings that are likely to be
 * crawlable URIs.
 *
 * @author gojomo
 *
 */
public class ExtractorJS extends ContentExtractor {

    private static final long serialVersionUID = 2L;

    private static Logger LOGGER =
        Logger.getLogger("org.archive.crawler.extractor.ExtractorJS");

    static final String AMP = "&";
    static final String ESCAPED_AMP = "&amp;";
    static final String WHITESPACE = "\\s";

    // finds whitespace-free strings in Javascript
    // (areas between paired ' or " characters, possibly backslash-quoted
    // on the ends, but not in the middle)
    static final String JAVASCRIPT_STRING_EXTRACTOR =
        "(\\\\{0,8}+(?:\"|\'))(\\S{0,"+UURI.MAX_URL_LENGTH+"}?)(?:\\1)";
    // GROUPS:
    // (G1) ' or " with optional leading backslashes
    // (G2) whitespace-free string delimited on boths ends by G1

    // determines whether a string is likely URI
    // (no whitespace or '<' '>',  has an internal dot or some slash,
    // begins and ends with either '/' or a word-char)
    static final String STRING_URI_DETECTOR =
        "(?:\\w|[\\.]{0,2}/)[\\S&&[^<>]]*(?:\\.|/)[\\S&&[^<>]]*(?:\\w|/)";

    protected long numberOfCURIsHandled = 0;
    protected static long numberOfLinksExtracted = 0;

    // strings that STRING_URI_DETECTOR picks up as URIs,
    // which are known to be problematic, and NOT to be 
    // added to outLinks
    protected final static String[] STRING_URI_DETECTOR_EXCEPTIONS = {
        "text/javascript"
        };
    
    // URIs known to produce false-positives with the current JS extractor.
    // e.g. currently (2.0.3) the JS extractor produces 13 false-positive 
    // URIs from http://www.google-analytics.com/urchin.js and only 2 
    // good URIs, which are merely one pixel images.
    // TODO: remove this blacklist when JS extractor is improved 
    protected final static String[] EXTRACTOR_URI_EXCEPTIONS = {
        "http://www.google-analytics.com/urchin.js"
        };
    
    /**
     * @param name
     */
    public ExtractorJS() {
    }

    
    protected boolean shouldExtract(ProcessorURI uri) {
        
        // special-cases, for when we know our current JS extractor does poorly.
        // TODO: remove this test when JS extractor is improved 
        for (String s: EXTRACTOR_URI_EXCEPTIONS) {
            if (uri.toString().equals(s))
                return false;
        }
        
        String contentType = uri.getContentType();
        if ((contentType == null)) {
            return false;
        }

        // If the content-type indicates js, we should process it.
        if (contentType.indexOf("javascript") >= 0) {
            return true;
        }
        if (contentType.indexOf("jscript") >= 0) {
            return true;
        }
        if (contentType.indexOf("ecmascript") >= 0) {
            return true;
        }
        
        // If the filename indicates js, we should process it.
        if (uri.toString().toLowerCase().endsWith(".js")) {
            return true;
        }
        
        // If the viaContext indicates a script, we should process it.
        LinkContext context = uri.getViaContext();
        if (context == null) {
            return false;
        }
        String s = context.toString().toLowerCase();
        return s.startsWith("script");
    }
    

    @Override
    protected boolean innerExtract(ProcessorURI curi) {
        this.numberOfCURIsHandled++;
        ReplayCharSequence cs = null;
        try {
            cs = curi.getRecorder().getReplayCharSequence();
            try {
                numberOfLinksExtracted += considerStrings(this, curi, cs, 
                        true);
            } catch (StackOverflowError e) {
                DevUtils.warnHandle(e, "ExtractorJS StackOverflowError");
            }
            // Set flag to indicate that link extraction is completed.
            return true;
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
        } finally {
            ArchiveUtils.closeQuietly(cs);
        }
        return false;
    }

    public static long considerStrings(Extractor ext, 
            ProcessorURI curi, CharSequence cs, boolean handlingJSFile) {
        long foundLinks = 0;
        Matcher strings =
            TextUtils.getMatcher(JAVASCRIPT_STRING_EXTRACTOR, cs);
        while(strings.find()) {
            CharSequence subsequence =
                cs.subSequence(strings.start(2), strings.end(2));
            Matcher uri =
                TextUtils.getMatcher(STRING_URI_DETECTOR, subsequence);
            if(uri.matches()) {
                String string = uri.group();
                // protect against adding outlinks for known problematic matches
                if (isUriMatchException(string,cs)) {
                    TextUtils.recycleMatcher(uri);
                    continue;
                }
                string = speculativeFixup(string, curi);
                foundLinks++;
                try {
                    int max = ext.getExtractorParameters().getMaxOutlinks();
                    if (handlingJSFile) {
                        Link.addRelativeToVia(curi, max, string, JS_MISC, 
                                SPECULATIVE);
                    } else {
                        Link.addRelativeToBase(curi, max, string, JS_MISC, 
                                SPECULATIVE);
                    }
                } catch (URIException e) {
                    ext.logUriError(e, curi.getUURI(), string);
                }
            } else {
               foundLinks += considerStrings(ext, curi, subsequence, 
                       handlingJSFile);
            }
            TextUtils.recycleMatcher(uri);
        }
        TextUtils.recycleMatcher(strings);
        return foundLinks;
    }
    
    /**
     * checks to see if URI match is a special case 
     * @param string matched by <code>STRING_URI_DETECTOR</code>
     * @param cs 
     * @return true if string is one of <code>STRING_URI_EXCEPTIONS</code>
     */
    private static boolean isUriMatchException(String string,CharSequence cs) {
        for (String s : STRING_URI_DETECTOR_EXCEPTIONS) {
            if (s.equals(string)) 
                return true;
        }
        return false;
    }


    /**
     * Perform additional fixup of likely-URI Strings
     * 
     * @param string detected candidate String
     * @return String changed/decoded to increase liklihood it is a 
     * meaningful non-404 URI
     */
    public static String speculativeFixup(String string, ProcessorURI puri) {
        String retVal = string;
        
        // unescape ampersands
        retVal = TextUtils.replaceAll(ESCAPED_AMP, retVal, AMP);
        
        // uri-decode if begins with encoded 'http(s)?%3A'
        Matcher m = TextUtils.getMatcher("(?i)^https?%3A.*",retVal); 
        if(m.matches()) {
            try {
                retVal = LaxURLCodec.DEFAULT.decode(retVal);
            } catch (DecoderException e) {
                LOGGER.log(Level.INFO,"unable to decode",e);
            }
        }
        TextUtils.recycleMatcher(m);
        
        // TODO: more URI-decoding if there are %-encoded parts?
        
        // detect scheme-less intended-absolute-URI
        // intent: "opens with what looks like a dotted-domain, and 
        // last segment is a top-level-domain (eg "com", "org", etc)" 
        m = TextUtils.getMatcher(
                "^[^\\./:\\s%]+\\.[^/:\\s%]+\\.([^\\./:\\s%]+)(/.*|)$", 
                retVal);
        if(m.matches()) {
            if(ArchiveUtils.isTld(m.group(1))) { 
                String schemePlus = "http://";       
                // if on exact same host preserve scheme (eg https)
                try {
                    if (retVal.startsWith(puri.getUURI().getHost())) {
                        schemePlus = puri.getUURI().getScheme() + "://";
                    }
                } catch (URIException e) {
                    // error retrieving source host - ignore it
                }
                retVal = schemePlus + retVal; 
            }
        }
        TextUtils.recycleMatcher(m);
        
        return retVal; 
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Processor#report()
     */
    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: org.archive.crawler.extractor.ExtractorJS\n");
        ret.append("  Function:          Link extraction on JavaScript code\n");
        ret.append("  ProcessorURIs handled: " + numberOfCURIsHandled + "\n");
        ret.append("  Links extracted:   " + numberOfLinksExtracted + "\n\n");

        return ret.toString();
    }
}
