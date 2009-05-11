/*
 * ExtractorXML
 *
 * $Id$
 *
 * Created on Sep 27, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.ProcessorURI;
import org.archive.util.TextUtils;

/**
 * A simple extractor which finds HTTP URIs inside XML/RSS files,
 * inside attribute values and simple elements (those with only
 * whitespace + HTTP URI + whitespace as contents)
 *
 * @author gojomo
 *
 **/

public class ExtractorXML extends ContentExtractor {

    private static final long serialVersionUID = 3L;

    private static Logger logger =
        Logger.getLogger(ExtractorXML.class.getName());

    private static String ESCAPED_AMP = "&amp";

    static final Pattern XML_URI_EXTRACTOR = Pattern.compile(    
    "(?i)[\"\'>]\\s*(http:[^\\s\"\'<>]+)\\s*[\"\'<]"); 
    // GROUPS:
    // (G1) URI
    
    private AtomicLong linksExtracted = new AtomicLong(0);

    /**
     * @param name
     */
    public ExtractorXML() {
    }

    
    @Override
    protected boolean shouldExtract(ProcessorURI curi) {
        String mimeType = curi.getContentType();
        if (mimeType == null) {
            return false;
        }
        if ((mimeType.toLowerCase().indexOf("xml") < 0) 
                && (!curi.toString().toLowerCase().endsWith(".rss"))
                && (!curi.toString().toLowerCase().endsWith(".xml"))) {
            return false;
        }
        
        return true;
    }
    
    
    /**
     * @param curi Crawl URI to process.
     */
    @Override
    protected boolean innerExtract(ProcessorURI curi) {
        ReplayCharSequence cs = null;
        try {
            cs = curi.getRecorder().getReplayCharSequence();
        } catch (IOException e) {
            logger.severe("Failed getting ReplayCharSequence: " + e.getMessage());
        }
        if (cs == null) {
            logger.severe("Failed getting ReplayCharSequence: " +
                curi.toString());
            return false;
        }
        try {
            this.linksExtracted.addAndGet(processXml(this, curi, cs));

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

    public static long processXml(Extractor ext, 
            ProcessorURI curi, CharSequence cs) {
        long foundLinks = 0;
        Matcher uris = null;
        String xmlUri;
        uris = XML_URI_EXTRACTOR.matcher(cs);
        while (uris.find()) {
            xmlUri = uris.group(1);
            // TODO: Escape more HTML Entities.
            xmlUri = TextUtils.replaceAll(ESCAPED_AMP, xmlUri, "&");
            foundLinks++;
            try {
                // treat as speculative, as whether context really 
                // intends to create a followable/fetchable URI is
                // unknown
                int max = ext.getExtractorParameters().getMaxOutlinks();
                Link.add(curi, max, xmlUri, LinkContext.SPECULATIVE_MISC, 
                        Hop.SPECULATIVE);
            } catch (URIException e) {
                // There may not be a controller (e.g. If we're being run
                // by the extractor tool).
                ext.logUriError(e, curi.getUURI(), xmlUri);
            }
        }
        return foundLinks;
    }

    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: org.archive.crawler.extractor.ExtractorXML\n");
        ret.append("  Function:          Link extraction on XML/RSS\n");
        ret.append("  CrawlURIs handled: " + getURICount() + "\n");
        ret.append("  Links extracted:   " + linksExtracted + "\n\n");

        return ret.toString();
    }
}
