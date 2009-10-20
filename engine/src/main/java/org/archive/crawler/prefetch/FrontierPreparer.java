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
package org.archive.crawler.prefetch;

import static org.archive.modules.SchedulingConstants.HIGH;
import static org.archive.modules.SchedulingConstants.MEDIUM;

import org.apache.commons.lang.StringUtils;
import org.archive.crawler.framework.Scoper;
import org.archive.crawler.frontier.CostAssignmentPolicy;
import org.archive.crawler.frontier.QueueAssignmentPolicy;
import org.archive.crawler.frontier.SurtAuthorityQueueAssignmentPolicy;
import org.archive.crawler.frontier.UnitCostAssignmentPolicy;
import org.archive.crawler.frontier.precedence.CostUriPrecedencePolicy;
import org.archive.crawler.frontier.precedence.UriPrecedencePolicy;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.canonicalize.RulesCanonicalizationPolicy;
import org.archive.modules.canonicalize.UriCanonicalizationPolicy;
import org.archive.net.UURI;
import org.archive.spring.KeyedProperties;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Processor to preload URI with as much precalculated policy-based 
 * info as possible before it reaches frontier criticial sections.
 * 
 * Frontiers also maintain a direct reference to this class, in case
 * they need to perform remedial preparation for URIs that do not
 * pass through this processor on the CandidateChain.
 * 
 * @contributor gojomo
 */
public class FrontierPreparer extends Scoper {
    private static final long serialVersionUID = 1L;

    /**
     * Number of hops (of any sort) from a seed up to which a URI has higher
     * priority scheduling than any remaining seed. For example, if set to 1
     * items one hop (link, embed, redirect, etc.) away from a seed will be
     * scheduled with HIGH priority. If set to -1, no preferencing will occur,
     * and a breadth-first search with seeds processed before discovered links
     * will proceed. If set to zero, a purely depth-first search will proceed,
     * with all discovered links processed before remaining seeds. Seed
     * redirects are treated as one hop from a seed.
     */
    {
        setPreferenceDepthHops(-1); // no limit
    }
    public int getPreferenceDepthHops() {
        return (Integer) kp.get("preferenceDepthHops");
    }
    public void setPreferenceDepthHops(int depth) {
        kp.put("preferenceDepthHops",depth);
    }
    
    /** number of hops of embeds (ERX) to bump to front of host queue */
    {
        setPreferenceEmbedHops(1);
    }
    public int getPreferenceEmbedHops() {
        return (Integer) kp.get("preferenceEmbedHops");
    }
    public void setPreferenceEmbedHops(int pref) {
        kp.put("preferenceEmbedHops",pref);
    }
    
    /**
     * Ordered list of url canonicalization rules.  Rules are applied in the 
     * order listed from top to bottom.
     */
    {
        setCanonicalizationPolicy(new RulesCanonicalizationPolicy());
    }
    public UriCanonicalizationPolicy getCanonicalizationPolicy() {
        return (UriCanonicalizationPolicy) kp.get("uriCanonicalizationRules");
    }
    @Autowired(required=false)
    public void setCanonicalizationPolicy(UriCanonicalizationPolicy policy) {
        kp.put("uriCanonicalizationRules",policy);
    }

    /**
     * Defines how to assign URIs to queues. Can assign by host, by ip, 
     * by SURT-ordered authority, by SURT-ordered authority truncated to 
     * a topmost-assignable domain, and into one of a fixed set of buckets 
     * (1k).
     */
    {
        setQueueAssignmentPolicy(new SurtAuthorityQueueAssignmentPolicy());
    }
    public QueueAssignmentPolicy getQueueAssignmentPolicy() {
        return (QueueAssignmentPolicy) kp.get("queueAssignmentPolicy");
    }
    @Autowired(required=false)
    public void setQueueAssignmentPolicy(QueueAssignmentPolicy policy) {
        kp.put("queueAssignmentPolicy",policy);
    }
    
    /** URI precedence assignment policy to use. */
    {
        setUriPrecedencePolicy(new CostUriPrecedencePolicy());
    }
    public UriPrecedencePolicy getUriPrecedencePolicy() {
        return (UriPrecedencePolicy) kp.get("uriPrecedencePolicy");
    }
    @Autowired(required=false)
    public void setUriPrecedencePolicy(UriPrecedencePolicy policy) {
        kp.put("uriPrecedencePolicy",policy);
    }
    
    /** cost assignment policy to use. */
    {
        setCostAssignmentPolicy(new UnitCostAssignmentPolicy());
    }
    public CostAssignmentPolicy getCostAssignmentPolicy() {
        return (CostAssignmentPolicy) kp.get("costAssignmentPolicy");
    }
    @Autowired(required=false)
    public void setCostAssignmentPolicy(CostAssignmentPolicy policy) {
        kp.put("costAssignmentPolicy",policy);
    }
    
    /* (non-Javadoc)
     * @see org.archive.modules.Processor#shouldProcess(org.archive.modules.CrawlURI)
     */
    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return true;
    }

    /* (non-Javadoc)
     * @see org.archive.modules.Processor#innerProcess(org.archive.modules.CrawlURI)
     */
    @Override
    protected void innerProcess(CrawlURI curi) {
        prepare(curi);
    }
    
    /**
     * Apply all configured policies to CrawlURI
     * 
     * @param curi CrawlURI
     */
    public void prepare(CrawlURI curi) {
        
        // set schedulingDirective
        curi.setSchedulingDirective(getSchedulingDirective(curi));
            
        // set canonicalized version
        curi.setCanonicalString(canonicalize(curi));
        
        // set queue key
        curi.setClassKey(getClassKey(curi));
        
        // set cost
        curi.setHolderCost(getCost(curi));
        
        // set URI precedence
        getUriPrecedencePolicy().uriScheduled(curi);


    }

    /**
     * Calculate the coarse, original 'schedulingDirective' prioritization
     * for the given CrawlURI
     * 
     * @param curi
     * @return
     */
    protected int getSchedulingDirective(CrawlURI curi) {
        if(StringUtils.isNotEmpty(curi.getPathFromSeed())) {
            char lastHop = curi.getPathFromSeed().charAt(curi.getPathFromSeed().length()-1);
            if(lastHop == 'R') {
                // refer
                return getPreferenceDepthHops() >= 0 ? HIGH : MEDIUM;
            } 
        }
        if (getPreferenceDepthHops() == 0) {
            return HIGH;
            // this implies seed redirects are treated as path
            // length 1, which I belive is standard.
            // curi.getPathFromSeed() can never be null here, because
            // we're processing a link extracted from curi
        } else if (getPreferenceDepthHops() > 0 && 
            curi.getPathFromSeed().length() + 1 <= getPreferenceDepthHops()) {
            return HIGH;
        } else {
            // optionally preferencing embeds up to MEDIUM
            int prefHops = getPreferenceEmbedHops(); 
            if (prefHops > 0) {
                int embedHops = curi.getTransHops();
                if (embedHops > 0 && embedHops <= prefHops
                        && curi.getSchedulingDirective() == SchedulingConstants.NORMAL) {
                    // number of embed hops falls within the preferenced range, and
                    // uri is not already MEDIUM -- so promote it
                    return MEDIUM;
                }
            }
            // Everything else stays as previously assigned
            // (probably NORMAL, at least for now)
            return curi.getSchedulingDirective();
        }
    }
    /**
     * Canonicalize passed CrawlURI. This method differs from
     * {@link #canonicalize(UURI)} in that it takes a look at
     * the CrawlURI context possibly overriding any canonicalization effect if
     * it could make us miss content. If canonicalization produces an URL that
     * was 'alreadyseen', but the entry in the 'alreadyseen' database did
     * nothing but redirect to the current URL, we won't get the current URL;
     * we'll think we've already see it. Examples would be archive.org
     * redirecting to www.archive.org or the inverse, www.netarkivet.net
     * redirecting to netarkivet.net (assuming stripWWW rule enabled).
     * <p>Note, this method under circumstance sets the forceFetch flag.
     * 
     * @param cauri CrawlURI to examine.
     * @return Canonicalized <code>cacuri</code>.
     */
    protected String canonicalize(CrawlURI cauri) {
        String canon = getCanonicalizationPolicy().canonicalize(cauri.getURI());
        if (cauri.isLocation()) {
            // If the via is not the same as where we're being redirected (i.e.
            // we're not being redirected back to the same page, AND the
            // canonicalization of the via is equal to the the current cauri, 
            // THEN forcefetch (Forcefetch so no chance of our not crawling
            // content because alreadyseen check things its seen the url before.
            // An example of an URL that redirects to itself is:
            // http://bridalelegance.com/images/buttons3/tuxedos-off.gif.
            // An example of an URL whose canonicalization equals its via's
            // canonicalization, and we want to fetch content at the
            // redirection (i.e. need to set forcefetch), is netarkivet.dk.
            if (!cauri.toString().equals(cauri.getVia().toString()) &&
                    getCanonicalizationPolicy().canonicalize(
                            cauri.getVia().toCustomString()).equals(canon)) {
                cauri.setForceFetch(true);
            }
        }
        return canon;
    }
    
    /**
     * @param cauri CrawlURI we're to get a key for.
     * @return a String token representing a queue
     */
    public String getClassKey(CrawlURI curi) {
        assert KeyedProperties.overridesActiveFrom(curi);      
        String queueKey = getQueueAssignmentPolicy().getClassKey(curi);
        return queueKey;
    }
    
    /**
     * Return the 'cost' of a CrawlURI (how much of its associated
     * queue's budget it depletes upon attempted processing)
     * 
     * @param curi
     * @return the associated cost
     */
    protected int getCost(CrawlURI curi) {
        assert KeyedProperties.overridesActiveFrom(curi);
        
        int cost = curi.getHolderCost();
        if (cost == CrawlURI.UNCALCULATED) {
            cost = getCostAssignmentPolicy().costOf(curi);
        }
        return cost;
    }
    
}
