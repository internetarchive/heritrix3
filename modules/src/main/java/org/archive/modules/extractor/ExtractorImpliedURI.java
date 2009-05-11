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
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.ProcessorURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;


/**
 * An extractor for finding 'implied' URIs inside other URIs.  If the 
 * 'trigger' regex is matched, a new URI will be constructed from the
 * 'build' replacement pattern. 
 * 
 * Unlike most other extractors, this works on URIs discovered by 
 * previous extractors. Thus it should appear near the end of any 
 * set of extractors.
 *
 * Initially, only finds absolute HTTP(S) URIs in query-string or its 
 * parameters.
 *
 * TODO: extend to find URIs in path-info
 *
 * @author Gordon Mohr
 *
 **/

public class ExtractorImpliedURI extends Extractor {

    private static final long serialVersionUID = 3L;

    private static Logger LOGGER =
        Logger.getLogger(ExtractorImpliedURI.class.getName());
   
    
    /**
     * Triggering regular expression. When a discovered URI matches this
     * pattern, the 'implied' URI will be built. The capturing groups of this
     * expression are available for the build replacement pattern.
     */
    {
        setRegex(Pattern.compile("^(.*)$"));
    }
    public Pattern getRegex() {
        return (Pattern) kp.get("regex");
    }
    public void setRegex(Pattern regex) {
        kp.put("regex",regex);
    }

    
    /**
     * Replacement pattern to build 'implied' URI, using captured groups of
     * trigger expression.
     */
    {
        setFormat("");
    }
    public String getFormat() {
        return (String) kp.get("format");
    }
    public void setFormat(String format) {
        kp.put("format",format);
    }

    /**
     * If true, all URIs that match trigger regular expression are removed 
     * from the list of extracted URIs. Default is false.
     */
    {
        setRemoveTriggerUris(false);
    }
    public boolean getRemoveTriggerUris() {
        return (Boolean) kp.get("removeTriggerUris");
    }
    public void setRemoveTriggerUris(boolean remove) {
        kp.put("removeTriggerUris",remove);
    }
    
    final AtomicLong linksExtracted = new AtomicLong();


    /**
     * Constructor.
     */
    public ExtractorImpliedURI() {
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
            Link link = links.get(i);
            Pattern trigger = getRegex();
            String build = getFormat();
            CharSequence dest = link.getDestination();
            String implied = extractImplied(dest, trigger, build);
            if (implied != null) {
                try {
                    UURI src = curi.getUURI();
                    UURI target = UURIFactory.getInstance(implied);
                    LinkContext lc = LinkContext.SPECULATIVE_MISC;
                    Hop hop = Hop.SPECULATIVE;
                    Link out = new Link(src, target, lc, hop);
                    curi.getOutLinks().add(out);
                    linksExtracted.incrementAndGet();

                    boolean removeTriggerURI = getRemoveTriggerUris();
                    // remove trigger URI from the outlinks if configured so.
                    if (removeTriggerURI) {
                       if (curi.getOutLinks().remove(link)) {
                               LOGGER.log(Level.FINE, link.getDestination() + 
                                     " has been removed from " + 
                                     link.getSource() + " outlinks list.");
                               linksExtracted.decrementAndGet();
                       } else {
                               LOGGER.log(Level.FINE, "Failed to remove " + 
                                      link.getDestination() + " from " + 
                                      link.getSource()+ " outlinks list.");
                       }
                    }
                } catch (URIException e) {
                    LOGGER.log(Level.FINE, "bad URI", e);
                }
            }
        }
    }
    
    /**
     * Utility method for extracting 'implied' URI given a source uri, 
     * trigger pattern, and build pattern. 
     * 
     * @param uri source to check for implied URI
     * @param trigger regex pattern which if matched implies another URI
     * @param build replacement pattern to build the implied URI
     * @return implied URI, or null if none
     */
    protected static String extractImplied(CharSequence uri, Pattern trigger, String build) {
        if (trigger == null) {
            return null;
        }
        Matcher m = trigger.matcher(uri);
        if(m.matches()) {
            String result = m.replaceFirst(build);
            return result; 
        }
        return null; 
    }

    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: "+ExtractorImpliedURI.class.getName()+"\n");
        ret.append("  Function:          Extracts links inside other URIs\n");
        ret.append("  CrawlURIs handled: " + getURICount() + "\n");
        ret.append("  Links extracted:   " + linksExtracted.get() + "\n\n");
        return ret.toString();
    }
}
