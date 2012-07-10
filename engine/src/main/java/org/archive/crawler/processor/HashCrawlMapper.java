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

    @SuppressWarnings("unused")
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
    protected long crawlerCount = 1L; 
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