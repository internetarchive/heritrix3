/* HashCrawlMapper
 * 
 * Created on Sep 30, 2005
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
package org.archive.crawler.processor;

import java.util.regex.Matcher;

import org.archive.crawler.framework.Frontier;
import org.archive.modules.CrawlURI;
import org.archive.net.PublicSuffixes;
import org.archive.util.TextUtils;
import org.springframework.beans.factory.annotation.Autowired;

import st.ata.util.FPGenerator;

/**
 * Maps URIs to one of N crawler names by applying a hash to the
 * URI's (possibly-transformed) classKey. 
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class HashCrawlMapper extends CrawlMapper {

    private static final long serialVersionUID = 2L;
    
    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    /**
     * Number of crawlers among which to split up the URIs. Their names are
     * assumed to be 0..N-1.
     */
    long crawlerCount = 1L; 
    public long getCrawlerCount() {
        return this.crawlerCount; 
    }
    public void setCrawlerCount(long count) {
        this.crawlerCount = count;
    }

    /**
     * Whether to use the PublicSuffixes-supplied reduce regex.
     * 
     */
    {
        setUsePublicSuffixesRegex(true);
    }
    public boolean getUsePublicSuffixesRegex() {
        return (Boolean) kp.get("usePublicSuffixesRegex");
    }
    public void setUsePublicSuffixesRegex(boolean usePublicSuffixes) {
        kp.put("usePublicSuffixesRegex",usePublicSuffixes);
    }
    
    /**
     * A regex pattern to apply to the classKey, using the first match as the
     * mapping key. If empty (the default), use the full classKey.
     * 
     */
    {
        setReducePrefixRegex("");
    }
    public String getReducePrefixRegex() {
        return (String) kp.get("reducePrefixRegex");
    }
    public void setReducePrefixRegex(String regex) {
        kp.put("reducePrefixRegex",regex);
    }
    
    /**
     * Constructor.
     */
    public HashCrawlMapper() {
        super();
    }

    /**
     * Look up the crawler node name to which the given CrawlURI 
     * should be mapped. 
     * 
     * @param cauri CrawlURI to consider
     * @return String node name which should handle URI
     */
    protected String map(CrawlURI cauri) {
        // get classKey, via frontier to generate if necessary
        String key = frontier.getClassKey(cauri);
        String reduceRegex = getReduceRegex(cauri);
        return mapString(key, reduceRegex, getCrawlerCount()); 
    }

    protected String getReduceRegex(CrawlURI cauri) {
        if(getUsePublicSuffixesRegex()) {
            return PublicSuffixes.getTopmostAssignedSurtPrefixRegex();
        } else {
            return getReducePrefixRegex();
        }
    }

    public static String mapString(String key, String reducePattern,
            long bucketCount) {

        if (reducePattern != null && reducePattern.length()>0) {
            Matcher matcher = TextUtils.getMatcher(reducePattern,key);
            if(matcher.find()) {
                key = matcher.group();
            }
            TextUtils.recycleMatcher(matcher);
        }
        long fp = FPGenerator.std64.fp(key);
        long bucket = fp % bucketCount;
        return Long.toString(bucket >= 0 ? bucket : -bucket);
    }
}