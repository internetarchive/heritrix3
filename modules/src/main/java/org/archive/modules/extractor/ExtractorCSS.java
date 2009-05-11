/*
 * ExtractorCSS
 *
 * $Id$
 *
 * Created on Jan 6, 2004
 *
 * Copyright (C) 2004 Internet Archive.
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
 * @author Igor Ranitovic
 *
 **/

public class ExtractorCSS extends ContentExtractor {


    private static final long serialVersionUID = 2L;

    private static Logger logger =
        Logger.getLogger("org.archive.crawler.extractor.ExtractorCSS");

    private static String ESCAPED_AMP = "&amp";
    // CSS escapes: "Parentheses, commas, whitespace characters, single 
    // quotes (') and double quotes (") appearing in a URL must be 
    // escaped with a backslash"
    static final String CSS_BACKSLASH_ESCAPE = "\\\\([,'\"\\(\\)\\s])";
    
    /**
     *  CSS URL extractor pattern.
     *
     *  This pattern extracts URIs for CSS files
     **/
//    static final String CSS_URI_EXTRACTOR =
//        "url[(]\\s*([\"\']?)([^\\\"\\'].*?)\\1\\s*[)]";
    static final String CSS_URI_EXTRACTOR =    
    "(?i)(?:@import (?:url[(]|)|url[(])\\s*([\\\"\']?)" + // G1
    "([^\\\"\'].{0,"+UURI.MAX_URL_LENGTH+"}?)\\1\\s*[);]"; // G2
    // GROUPS:
    // (G1) optional ' or "
    // (G2) URI
    
    private long numberOfLinksExtracted = 0;

    /**
     */
    public ExtractorCSS() {
    }

    
    @Override
    protected boolean shouldExtract(ProcessorURI curi) {
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
    public boolean innerExtract(ProcessorURI curi) {
        ReplayCharSequence cs = null;
        try {
            cs = curi.getRecorder().getReplayCharSequence();
        } catch (IOException e) {
            logger.severe("Failed getting ReplayCharSequence: " + e.getMessage());
        }
        if (cs == null) {
            logger.warning("Failed getting ReplayCharSequence: " +
                curi.toString());
            return false;
        }
        
        // We have a ReplayCharSequence open.  Wrap all in finally so we
        // for sure close it before we leave.
        try {
            this.numberOfLinksExtracted +=
                processStyleCode(this, curi, cs);
            // Set flag to indicate that link extraction is completed.
            return true;
        } finally {
            if (cs != null) {
                try {
                    cs.close();
                } catch (IOException ioe) {
                    logger.warning(TextUtils.exceptionToString(
                        "Failed close of ReplayCharSequence.", ioe));
                }
            }
        }
    }

    public static long processStyleCode(Extractor ext, 
            ProcessorURI curi, CharSequence cs) {
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

    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: org.archive.crawler.extractor.ExtractorCSS\n");
        ret.append("  Function:          Link extraction on Cascading Style Sheets (.css)\n");
        ret.append("  ExtractorURIs handled: " + getURICount() + "\n");
        ret.append("  Links extracted:   " + numberOfLinksExtracted + "\n\n");

        return ret.toString();
    }
}
