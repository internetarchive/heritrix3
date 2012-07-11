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


import static org.archive.modules.CoreAttributeConstants.A_FETCH_BEGAN_TIME;
import static org.archive.modules.CoreAttributeConstants.A_FETCH_COMPLETED_TIME;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_FAILED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_LOST;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DEEMED_NOT_FOUND;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DEFERRED;

import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.IgnoreRobotsPolicy;
import org.archive.modules.net.Robotstxt;
import org.archive.modules.net.ServerCache;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * A step, late in the processing of a CrawlURI, for marking-up the 
 * CrawlURI with values to affect frontier disposition, and updating
 * information that may have been affected by the fetch. This includes
 * robots info and other stats. 
 * 
 * (Formerly called CrawlStateUpdater, when it did less.)
 *
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class DispositionProcessor extends Processor {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = -1072728147960180091L;
    private static final Logger logger =
        Logger.getLogger(DispositionProcessor.class.getName());

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    /**
     * How many multiples of last fetch elapsed time to wait before recontacting
     * same server.
     */
    {
        setDelayFactor(5.0f);
    }
    public float getDelayFactor() {
        return (Float) kp.get("delayFactor");
    }
    public void setDelayFactor(float factor) {
        kp.put("delayFactor",factor);
    }

    /**
     * always wait this long after one completion before recontacting same
     * server, regardless of multiple
     */
    {
        setMinDelayMs(3000);
    }
    public int getMinDelayMs() {
        return (Integer) kp.get("minDelayMs");
    }
    public void setMinDelayMs(int minDelay) {
        kp.put("minDelayMs",minDelay);
    }
    
    /**
     * Whether to respect a 'Crawl-Delay' (in seconds) given in a site's
     * robots.txt
     */
    {
        setRespectCrawlDelayUpToSeconds(300);
    }
    public int getRespectCrawlDelayUpToSeconds() {
        return (Integer) kp.get("respectCrawlDelayUpToSeconds");
    }
    public void setRespectCrawlDelayUpToSeconds(int respect) {
        kp.put("respectCrawlDelayUpToSeconds",respect);
    }

    /** never wait more than this long, regardless of multiple */
    {
        setMaxDelayMs(30000);
    }
    public int getMaxDelayMs() {
        return (Integer) kp.get("maxDelayMs");
    }
    public void setMaxDelayMs(int maxDelay) {
        kp.put("maxDelayMs",maxDelay);
    }    

    /** maximum per-host bandwidth usage */
    {
        setMaxPerHostBandwidthUsageKbSec(0);
    }
    public int getMaxPerHostBandwidthUsageKbSec() {
        return (Integer) kp.get("maxPerHostBandwidthUsageKbSec");
    }
    public void setMaxPerHostBandwidthUsageKbSec(int max) {
        kp.put("maxPerHostBandwidthUsageKbSec",max);
    }
    
    /**
     * Whether to set a CrawlURI's force-retired directive, retiring
     * its queue when it finishes. Mainly intended for URI-specific 
     * overlay settings; setting true globally will just retire all queues 
     * after they offer one URI, rapidly ending a crawl. 
     */
    {
        setForceRetire(false);
    }
    public boolean getForceRetire() {
        return (Boolean) kp.get("forceRetire");
    }
    public void setForceRetire(boolean force) {
        kp.put("forceRetire",force);
    }
    
    /**
     * Auto-discovered module providing configured (or overridden)
     * User-Agent value and RobotsHonoringPolicy
     */
    protected CrawlMetadata metadata;
    public CrawlMetadata getMetadata() {
        return metadata;
    }
    @Autowired
    public void setMetadata(CrawlMetadata provider) {
        this.metadata = provider;
    }

    public DispositionProcessor() {
        super();
    }

    @Override
    protected boolean shouldProcess(CrawlURI puri) {
        return puri instanceof CrawlURI;
    }
    
    @Override
    protected void innerProcess(CrawlURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        
        // Tally per-server, per-host, per-frontier-class running totals
        CrawlServer server = serverCache.getServerFor(curi.getUURI());

        String scheme = curi.getUURI().getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https") &&
                server != null) {
            // Update connection problems counter
            if(curi.getFetchStatus() == S_CONNECT_FAILED || curi.getFetchStatus() == S_CONNECT_LOST ) {
                server.incrementConsecutiveConnectionErrors();
            } else if (curi.getFetchStatus() > 0){
                server.resetConsecutiveConnectionErrors();
            }

            // Update robots info
            try {
                if ("/robots.txt".equals(curi.getUURI().getPath()) && curi.getFetchStatus() != S_DEFERRED) {
                    // shortcut retries  w/ DEEMED when ignore-all
                    if (metadata.getRobotsPolicy() instanceof IgnoreRobotsPolicy) {
                        if(curi.getFetchStatus() < 0 && curi.getFetchStatus()!=S_DEFERRED) {
                            // prevent the rest of the usual retries
                            curi.setFetchStatus(S_DEEMED_NOT_FOUND);
                        }
                    }
                    
                    // Update server with robots info
                    // NOTE: in some cases the curi's status can be changed here
                    server.updateRobots(curi);
                }
            }
            catch (URIException e) {
                logger.severe("Failed get path on " + curi.getUURI());
            }
        }
        
        // set politeness delay
        curi.setPolitenessDelay(politenessDelayFor(curi));
        
        // consider operator-set force-retire
        if (getForceRetire()) {
            curi.setForceRetire(true);
        }
        
        // TODO: set other disposition decisions
        // success, failure, retry(retry-delay)
    }
    
    /**
     * Update any scheduling structures with the new information in this
     * CrawlURI. Chiefly means make necessary arrangements for no other URIs at
     * the same host to be visited within the appropriate politeness window.
     * 
     * @param curi
     *            The CrawlURI
     * @return millisecond politeness delay
     */
    protected long politenessDelayFor(CrawlURI curi) {
        long durationToWait = 0;
        Map<String,Object> cdata = curi.getData();
        if (cdata.containsKey(A_FETCH_BEGAN_TIME)
                && cdata.containsKey(A_FETCH_COMPLETED_TIME)) {

            long completeTime = curi.getFetchCompletedTime();
            long durationTaken = (completeTime - curi.getFetchBeginTime());
            durationToWait = (long)(getDelayFactor() * durationTaken);

            long minDelay = getMinDelayMs();
            if (minDelay > durationToWait) {
                // wait at least the minimum
                durationToWait = minDelay;
            }

            long maxDelay = getMaxDelayMs();
            if (durationToWait > maxDelay) {
                // wait no more than the maximum
                durationToWait = maxDelay;
            }
            
            long respectThreshold = getRespectCrawlDelayUpToSeconds() * 1000;
            if (durationToWait<respectThreshold) {
                // may need to extend wait
                CrawlServer s = getServerCache().getServerFor(curi.getUURI());
                String ua = curi.getUserAgent();
                if (ua == null) {
                    ua = metadata.getUserAgent();
                }
                Robotstxt rep = s.getRobotstxt();
                if (rep != null) {
                    long crawlDelay = (long)(1000 * rep.getDirectivesFor(ua).getCrawlDelay());
                    crawlDelay = 
                        (crawlDelay > respectThreshold) 
                            ? respectThreshold 
                            : crawlDelay;
                    if (crawlDelay > durationToWait) {
                        // wait at least the directive crawl-delay
                        durationToWait = crawlDelay;
                    }
                }
            }
            
            long now = System.currentTimeMillis();
            int maxBandwidthKB = getMaxPerHostBandwidthUsageKbSec();
            if (maxBandwidthKB > 0) {
                // Enforce bandwidth limit
                ServerCache cache = this.getServerCache();
                CrawlHost host = cache.getHostFor(curi.getUURI());
                long minDurationToWait = host.getEarliestNextURIEmitTime()
                        - now;
                float maxBandwidth = maxBandwidthKB * 1.024F; // kilo factor
                long processedBytes = curi.getContentSize();
                host
                        .setEarliestNextURIEmitTime((long)(processedBytes / maxBandwidth)
                                + now);

                if (minDurationToWait > durationToWait) {
                    durationToWait = minDurationToWait;
                }
            }
        }
        return durationToWait;
    }
}
