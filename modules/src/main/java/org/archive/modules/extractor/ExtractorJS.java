/* Copyright (C) 2003 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Created on Nov 17, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.archive.modules.extractor;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.ProcessorURI;
import org.archive.net.UURI;
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

    /**
     * @param name
     */
    public ExtractorJS() {
    }

    
    protected boolean shouldExtract(ProcessorURI uri) {
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
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
        }

        if (cs == null) {
            LOGGER.warning("Failed getting ReplayCharSequence: " +
                curi.toString());
            return false;
        }

        try {
            try {
                numberOfLinksExtracted += considerStrings(this, curi, cs, 
                        true);
            } catch (StackOverflowError e) {
                DevUtils.warnHandle(e, "ExtractorJS StackOverflowError");
            }
            // Set flag to indicate that link extraction is completed.
            return true;
        } finally {
            // Done w/ the ReplayCharSequence. Close it.
            if (cs != null) {
                try {
                    cs.close();
                } catch (IOException ioe) {
                    LOGGER.warning(TextUtils.exceptionToString(
                        "Failed close of ReplayCharSequence.", ioe));
                }
            }
        }
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
                string = TextUtils.replaceAll(ESCAPED_AMP, string, AMP);
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
