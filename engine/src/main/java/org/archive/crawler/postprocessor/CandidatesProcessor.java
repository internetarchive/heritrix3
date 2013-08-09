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

    @SuppressWarnings("unused")
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

    /**
     * If true, outlinks from status codes <200 and >=400 
     * will be sent through candidates processing. Default is
     * false. 
     */
    {
        setProcessErrorOutlinks(false);
    }
    public boolean getProcessErrorOutlinks() {
        return (Boolean) kp.get("processErrorOutlinks");
    }
    public void setProcessErrorOutlinks(boolean errorOutlinks) {
        kp.put("processErrorOutlinks",errorOutlinks);
    }
    
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

    /**
     * Run candidatesChain on a single candidate CrawlURI; if its
     * reported status is nonnegative, schedule to frontier. 
     * 
     * Also applies special handling of discovered URIs that by
     * convention we want to treat as seeds (which then may be
     * scheduled indirectly via addSeed). 
     * 
     * @param candidate CrawlURI to consider 
     * @param source CrawlURI from which candidate was discovered/derived
     * @return candidate's status code at end of candidate chain execution
     * @throws InterruptedException
     */
    protected int runCandidateChain(CrawlURI candidate, CrawlURI source) throws InterruptedException {
        // at least for duration of candidatechain, offer
        // access to full CrawlURI of via
        candidate.setFullVia(source); 
        sheetOverlaysManager.applyOverlaysTo(candidate);
        try {
            KeyedProperties.clearOverridesFrom(source); 
            KeyedProperties.loadOverridesFrom(candidate);
            
            // apply special seed-status promotion
            if(getSeedsRedirectNewSeeds() && source.isSeed() 
                    && candidate.getLastHop().equals(Hop.REFER.getHopString())
                    && candidate.getHopCount() < SEEDS_REDIRECT_NEW_SEEDS_MAX_HOPS) {
                candidate.setSeed(true); 
            }
            
            getCandidateChain().process(candidate, null);
            int statusAfterCandidateChain = candidate.getFetchStatus();
            if(statusAfterCandidateChain>=0) {
                if(checkForSeedPromotion(candidate)) {
                    /*
                     * We want to guarantee crawling of seed version of
                     * CrawlURI even if same url has already been enqueued,
                     * see https://webarchive.jira.com/browse/HER-1891
                     */
                    candidate.setForceFetch(true);
                    getSeeds().addSeed(candidate); // triggers scheduling
                } else {
                    
                    frontier.schedule(candidate);
                    
                }
            } 
            return statusAfterCandidateChain;
        } finally {
            KeyedProperties.clearOverridesFrom(candidate); 
            KeyedProperties.loadOverridesFrom(source);
        }        
    }
    
    /**
     * Run candidates chain on each of (1) any prerequisite, if present; 
     * (2) any outCandidates, if present; (3) all outlinks, if appropriate
     * 
     * @see org.archive.modules.Processor#innerProcess(org.archive.modules.CrawlURI)
     */
    @Override
    protected void innerProcess(final CrawlURI curi) throws InterruptedException {
        // (1) Handle any prerequisites when S_DEFERRED for prereqs
        if (curi.hasPrerequisiteUri() && curi.getFetchStatus() == S_DEFERRED) {
            CrawlURI prereq = curi.getPrerequisiteUri();
            
            int prereqStatus = runCandidateChain(prereq, curi);
            
            if (prereqStatus<0) {
                curi.setFetchStatus(S_PREREQUISITE_UNSCHEDULABLE_FAILURE);
            }
            return;
        }

        // (2) NEW: also (and before-outlinks) run outCandidates (usually empty;
        // only current use is a form-submission CrawlURI; could 
        // potentially take over prerequisite duties for consistency
        for(CrawlURI candidate : curi.getOutCandidates()) {
            
            runCandidateChain(candidate, curi);
            
        }
        
        // Only consider candidate links of error pages if configured to do so
        if (!getProcessErrorOutlinks() 
                && (curi.getFetchStatus() < 200 || curi.getFetchStatus() >= 400)) {
            curi.getOutLinks().clear();
            return;
        }

        // (3) Handle outlinks (usual bulk of discoveries) 
        for (Link wref: curi.getOutLinks()) {
            CrawlURI candidate;
            try {
                candidate = curi.createCrawlURI(curi.getBaseURI(),wref);
            } catch (URIException e) {
                loggerModule.logUriError(e, curi.getUURI(), 
                        wref.getDestination().toString());
                continue;
            }
            
            runCandidateChain(candidate, curi);

            // TODO: evaluate if this necessary (anyone uses?); wise (bloat?) 
            curi.getOutCandidates().add(candidate);

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
