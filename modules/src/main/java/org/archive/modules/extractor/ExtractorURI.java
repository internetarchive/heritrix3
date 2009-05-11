/*
 * ExtractorURI
 *
 * $Id$
 *
 * Created on July 20, 2006
 *
 * Copyright (C) 2006 Internet Archive.
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.ProcessorURI;
import org.archive.net.LaxURLCodec;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TextUtils;

/**
 * An extractor for finding URIs inside other URIs. Unlike most other
 * extractors, this works on URIs discovered by previous extractors. Thus 
 * it should appear near the end of any set of extractors.
 *
 * Initially, only finds absolute HTTP(S) URIs in query-string or its 
 * parameters.
 *
 * TODO: extend to find URIs in path-info
 *
 * @author Gordon Mohr
 *
 **/

public class ExtractorURI extends Extractor {

    private static final long serialVersionUID = 3L;

    private static Logger LOGGER =
        Logger.getLogger(ExtractorURI.class.getName());

    static final String ABS_HTTP_URI_PATTERN = "^https?://[^\\s<>]*$";
    

    private AtomicLong linksExtracted = new AtomicLong(0);

    /**
     * Constructor
     * 
     * @param name
     */
    public ExtractorURI() {
    }

    
    @Override
    protected boolean shouldProcess(ProcessorURI uri) {
        return true;
    }
    
    /**
     * Perform usual extraction on a CrawlURI
     * 
     * @param curi Crawl URI to process.
     */
    @Override
    public void extract(ProcessorURI curi) {
        List<Link> links = new ArrayList<Link>(curi.getOutLinks());
        int max = links.size();
        for (int i = 0; i < max; i++) {
            Link wref = links.get(i);
            extractLink(curi, wref);
        }
    }

    /**
     * Consider a single Link for internal URIs
     * 
     * @param curi CrawlURI to add discoveries to 
     * @param wref Link to examine for internal URIs
     */
    protected void extractLink(ProcessorURI curi, Link wref) {
        UURI source = null;
        try {
            source = UURIFactory.getInstance(wref.getDestination().toString());
        } catch (URIException e) {
            LOGGER.log(Level.FINE,"bad URI",e);
        }
        if(source == null) {
            // shouldn't happen
            return; 
        }
        List<String> found = extractQueryStringLinks(source);
        for (String uri : found) {
            try {
                UURI src = curi.getUURI();
                UURI dest = UURIFactory.getInstance(uri);
                LinkContext lc = LinkContext.SPECULATIVE_MISC;
                Hop hop = Hop.SPECULATIVE;
                Link link = new Link(src, dest, lc, hop);
                linksExtracted.incrementAndGet();
                curi.getOutLinks().add(link);
            } catch (URIException e) {
                LOGGER.log(Level.FINE, "bad URI", e);
            }
        }
        // TODO: consider path URIs too
        
    }

    /**
     * Look for URIs inside the supplied UURI.
     * 
     * Static for ease of testing or outside use. 
     * 
     * @param source UURI to example
     * @return List of discovered String URIs.
     */
    protected static List<String> extractQueryStringLinks(UURI source) {
        List<String> results = new ArrayList<String>(); 
        String decodedQuery;
        try {
            decodedQuery = source.getQuery();
        } catch (URIException e1) {
            // shouldn't happen
            return results;
        }
        if(decodedQuery==null) {
            return results;
        }
        // check if full query-string appears to be http(s) URI
        Matcher m = TextUtils.getMatcher(ABS_HTTP_URI_PATTERN,decodedQuery);
        if(m.matches()) {
            TextUtils.recycleMatcher(m);
            results.add(decodedQuery);
        }
        // split into params, see if any param value is http(s) URI
        String rawQuery = new String(source.getRawQuery());
        String[] params = rawQuery.split("&");
        for (String param : params) {
            String[] keyVal = param.split("=");
            if(keyVal.length==2) {
                String candidate;
                try {
                    candidate = LaxURLCodec.DEFAULT.decode(keyVal[1]);
                } catch (DecoderException e) {
                    continue;
                }
                // TODO: use other non-UTF8 codecs when appropriate
                m.reset(candidate);
                if(m.matches()) {
                    results.add(candidate);
                }
            }
        }
        return results;
    }

    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: "+ExtractorURI.class.getName()+"\n");
        ret.append("  Function:          Extracts links inside other URIs\n");
        ret.append("  CrawlURIs handled: " + getURICount() + "\n");
        ret.append("  Links extracted:   " + linksExtracted + "\n\n");

        return ret.toString();
    }
}
