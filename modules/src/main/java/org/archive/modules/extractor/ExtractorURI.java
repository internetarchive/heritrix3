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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.url.LaxURLCodec;
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

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    private static Logger LOGGER =
        Logger.getLogger(ExtractorURI.class.getName());

    protected static final String ABS_HTTP_URI_PATTERN = "^https?://[^\\s<>]*$";

    /**
     * Constructor
     * 
     * @param name
     */
    public ExtractorURI() {
    }

    
    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return true;
    }
    
    /**
     * Perform usual extraction on a CrawlURI
     * 
     * @param curi Crawl URI to process.
     */
    @Override
    public void extract(CrawlURI curi) {
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
    protected void extractLink(CrawlURI curi, Link wref) {
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
                numberOfLinksExtracted.incrementAndGet();
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
}
