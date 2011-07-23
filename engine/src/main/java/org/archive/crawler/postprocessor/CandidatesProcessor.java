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

package org.archive.crawler.postprocessor;


import static org.archive.modules.fetcher.FetchStatusCodes.S_DEFERRED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_PREREQUISITE_UNSCHEDULABLE_FAILURE;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.crawler.spring.SheetOverlaysManager;
import org.archive.modules.CandidateChain;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.seeds.SeedModule;
import org.archive.spring.KeyedProperties;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Processor which sends all candidate outlinks through the 
 * CandidateChain, scheduling those with non-negative status
 * codes to the frontier. Also performs special handling for
 * 'discovered seeds' -- URIs, as with redirects from seeds, 
 * that may deserve special treatment to expand the scope.
 */
public class CandidatesProcessor extends Processor {

    private static final long serialVersionUID = -3L;
    
    /**
     * Candidate chain
     */
    protected CandidateChain candidateChain;
    public CandidateChain getCandidateChain() {
        return this.candidateChain;
    }
    @Autowired
    public void setCandidateChain(CandidateChain candidateChain) {
        this.candidateChain = candidateChain;
    }
    
    /**
     * The frontier to use.
     */
    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    
    protected CrawlerLoggerModule loggerModule;
    public CrawlerLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(CrawlerLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }
    
    /**
     * If enabled, any URL found because a seed redirected to it (original seed
     * returned 301 or 302), will also be treated as a seed, as long as the hop
     * count is less than {@value #SEEDS_REDIRECT_NEW_SEEDS_MAX_HOPS}.
     */
    {
        setSeedsRedirectNewSeeds(true);
    }
    public boolean getSeedsRedirectNewSeeds() {
        return (Boolean) kp.get("seedsRedirectNewSeeds");
    }
    public void setSeedsRedirectNewSeeds(boolean redirect) {
        kp.put("seedsRedirectNewSeeds",redirect);
    }
    protected static final int SEEDS_REDIRECT_NEW_SEEDS_MAX_HOPS = 5;

    protected SeedModule seeds;
    public SeedModule getSeeds() {
        return this.seeds;
    }
    @Autowired
    public void setSeeds(SeedModule seeds) {
        this.seeds = seeds;
    }
    
    protected SheetOverlaysManager sheetOverlaysManager;
    public SheetOverlaysManager getSheetOverlaysManager() {
        return sheetOverlaysManager;
    }
    @Autowired
    public void setSheetOverlaysManager(SheetOverlaysManager sheetOverlaysManager) {
        this.sheetOverlaysManager = sheetOverlaysManager;
    }
    
    /**
     * Usual no-argument constructor
     */
    public CandidatesProcessor() {
    }
    
    /* (non-Javadoc)
     * @see org.archive.modules.Processor#shouldProcess(org.archive.modules.CrawlURI)
     */
    protected boolean shouldProcess(CrawlURI puri) {
        return true;
    }

    /* (non-Javadoc)
     * @see org.archive.modules.Processor#innerProcess(org.archive.modules.CrawlURI)
     */
    @Override
    protected void innerProcess(final CrawlURI curi) throws InterruptedException {
        // Handle any prerequisites when S_DEFERRED for prereqs
        if (curi.hasPrerequisiteUri() && curi.getFetchStatus() == S_DEFERRED) {
            CrawlURI prereq = curi.getPrerequisiteUri();
            prereq.setFullVia(curi); 
            sheetOverlaysManager.applyOverlaysTo(prereq);
            try {
                KeyedProperties.clearOverridesFrom(curi); 
                KeyedProperties.loadOverridesFrom(prereq);
                
                getCandidateChain().process(prereq, null);
                if(prereq.getFetchStatus()>=0) {
                    frontier.schedule(prereq);
                } else {
                    curi.setFetchStatus(S_PREREQUISITE_UNSCHEDULABLE_FAILURE);
                }
            } finally {
                KeyedProperties.clearOverridesFrom(prereq); 
                KeyedProperties.loadOverridesFrom(curi);
            }
            return;
        }

        // Don't consider candidate links of error pages
        if (curi.getFetchStatus() < 200 || curi.getFetchStatus() >= 400) {
            curi.getOutLinks().clear();
            return;
        }

        for (Link wref: curi.getOutLinks()) {
            CrawlURI candidate;
            try {
                candidate = curi.createCrawlURI(curi.getBaseURI(),wref);
                // at least for duration of candidatechain, offer
                // access to full CrawlURI of via
                candidate.setFullVia(curi); 
            } catch (URIException e) {
                loggerModule.logUriError(e, curi.getUURI(), 
                        wref.getDestination().toString());
                continue;
            }
            sheetOverlaysManager.applyOverlaysTo(candidate);
            try {
                KeyedProperties.clearOverridesFrom(curi); 
                KeyedProperties.loadOverridesFrom(candidate);
                
                if(getSeedsRedirectNewSeeds() && curi.isSeed() 
                        && wref.getHopType() == Hop.REFER
                        && candidate.getHopCount() < SEEDS_REDIRECT_NEW_SEEDS_MAX_HOPS) {
                    candidate.setSeed(true); 
                }
                getCandidateChain().process(candidate, null); 
                if(candidate.getFetchStatus()>=0) {
                    if(checkForSeedPromotion(candidate)) {
                        /*
                         * We want to guarantee crawling of seed version of
                         * CrawlURI even if same url has already been enqueued,
                         * see https://webarchive.jira.com/browse/HER-1891
                         */
                        candidate.setForceFetch(true);
                        
                        getSeeds().addSeed(candidate);
                    } else {
                        frontier.schedule(candidate);
                    }
                    curi.getOutCandidates().add(candidate);
                }
                
            } finally {
                KeyedProperties.clearOverridesFrom(candidate); 
                KeyedProperties.loadOverridesFrom(curi);
            }
        }
        curi.getOutLinks().clear();
    }
    
    /**
     * Check if the URI needs special 'discovered seed' treatment.
     * 
     * @param curi
     */
    protected boolean checkForSeedPromotion(CrawlURI curi) {
        if (curi.isSeed() && curi.getVia() != null
                && curi.flattenVia().length() > 0) {
            // The only way a seed can have a non-empty via is if it is the
            // result of a seed redirect. Returning true here schedules it 
            // via the seeds module, so it may affect scope and be logged 
            // as 'discovered' seed.
            //
            // This is a feature. This is handling for case where a seed
            // gets immediately redirected to another page. What we're doing is
            // treating the immediate redirect target as a seed.
            
            // And it needs rapid scheduling.
            if (curi.getSchedulingDirective() == SchedulingConstants.NORMAL) {
                curi.setSchedulingDirective(SchedulingConstants.MEDIUM);
            }
            return true; 
        }
        return false;
    }
}
