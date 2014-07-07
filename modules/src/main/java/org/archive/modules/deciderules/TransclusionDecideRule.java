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
package org.archive.modules.deciderules;

import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Hop;

/**
 * Rule ACCEPTs any CrawlURIs whose path-from-seed ('hopsPath' -- see
 * {@link CandidateURI#getPathFromSeed()}) ends 
 * with at least one, but not more than, the given number of 
 * non-navlink ('L') hops. 
 * 
 * Otherwise, if the path-from-seed is empty or if a navlink ('L') occurs
 * within max-trans-hops of the tail of the path-from-seed, this rule
 * returns PASS.
 *  
 * <p>Thus, it allows things like embedded resources (frames/images/media) 
 * and redirects to be transitively included ('transcluded') in a crawl, 
 * even if they otherwise would not, for some reasonable number of hops
 * (usually 1-5).
 *
 * @see <a href="http://www.google.com/search?q=define%3Atransclusion&sourceid=mozilla&start=0&start=0&ie=utf-8&oe=utf-8">Transclusion</a>
 *
 * @author gojomo
 */
public class TransclusionDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = -3975688876990558918L;

    /**
     * Maximum number of non-navlink (non-'L') hops to ACCEPT.
     */
    {
        setMaxTransHops(2);
    }
    public int getMaxTransHops() {
        return (Integer) kp.get("maxTransHops");
    }
    public void setMaxTransHops(int maxTransHops) {
        kp.put("maxTransHops", maxTransHops);
    }
    
    /**
     * Maximum number of speculative ('X') hops to ACCEPT.
     */
    {
        setMaxSpeculativeHops(1);
    }
    public int getMaxSpeculativeHops() {
        return (Integer) kp.get("maxSpeculativeHops");
    }
    public void setMaxSpeculativeHops(int maxSpeculativeHops) {
        kp.put("maxSpeculativeHops", maxSpeculativeHops);
    }

    /**
     * Usual constructor. 
     */
    public TransclusionDecideRule() {
    }

    /**
     * Evaluate whether given object is within the acceptable thresholds of
     * transitive hops.
     * 
     * @param object Object to make decision on.
     * @return true if the transitive hops >0 and <= max
     */
    protected boolean evaluate(CrawlURI curi) {
        String hopsPath = curi.getPathFromSeed();
        if (hopsPath == null || hopsPath.length() == 0) {
            return false; 
        }
        int allCount = 0;
        int nonrefCount = 0; 
        int specCount = 0; 
        for (int i = hopsPath.length() - 1; i >= 0; i--) {
            char c = hopsPath.charAt(i);
            if (c == Hop.NAVLINK.getHopChar() || c == Hop.SUBMIT.getHopChar()) {
                // end of hops counted here
                break;
            } 
            allCount++; 
            if (c != Hop.REFER.getHopChar()) {
                nonrefCount++;
            }
            if (c == Hop.SPECULATIVE.getHopChar()) {
                specCount++;
            }
        }
        // transclusion doesn't apply if there isn't at least one non-nav-hop
        if (allCount <= 0) {
            return false;
        }
        
        // too many speculative hops disqualify from transclusion
        if (specCount > getMaxSpeculativeHops()) {
            return false;
        }
        
        // transclusion applies as long as non-ref hops less than max
        return nonrefCount <= getMaxTransHops();
    }


}
