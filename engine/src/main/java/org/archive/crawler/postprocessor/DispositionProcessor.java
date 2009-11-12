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


import static org.archive.modules.CoreAttributeConstants.A_FETCH_COMPLETED_TIME;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_FAILED;

import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.ModuleAttributeConstants;
import org.archive.modules.Processor;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.RobotsExclusionPolicy;
import org.archive.modules.net.ServerCache;
import org.archive.modules.net.ServerCacheUtil;
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
     * Auto-discovered module providing configured (or overridden)
     * User-Agent value and RobotsHonoringPolicy
     */
    CrawlMetadata metadata;
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
        CrawlServer server = ServerCacheUtil.getServerFor(serverCache, 
                curi.getUURI());

        String scheme = curi.getUURI().getScheme().toLowerCase();
        if (scheme.equals("http") || scheme.equals("https") &&
                server != null) {
            // Update connection problems counter
            if(curi.getFetchStatus() == S_CONNECT_FAILED) {
                server.incrementConsecutiveConnectionErrors();
            } else if (curi.getFetchStatus() > 0){
                server.resetConsecutiveConnectionErrors();
            }

            // Update robots info
            try {
                if (curi.getUURI().getPath() != null &&
                        curi.getUURI().getPath().equals("/robots.txt")) {
                    // Update server with robots info
                    server.updateRobots(metadata.getRobotsHonoringPolicy(),  curi);
                }
            }
            catch (URIException e) {
                logger.severe("Failed get path on " + curi.getUURI());
            }
        }
        
        // set politeness delay
        curi.setPolitenessDelay(politenessDelayFor(curi));
        
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
        if (cdata.containsKey(ModuleAttributeConstants.A_FETCH_BEGAN_TIME)
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
                CrawlServer s = ServerCacheUtil.getServerFor(
                        getServerCache(),curi.getUURI());
                String ua = curi.getUserAgent();
                if (ua == null) {
                    ua = metadata.getUserAgent();
                }
                RobotsExclusionPolicy rep = s.getRobots();
                if (rep != null) {
                    long crawlDelay = (long)(1000 * s.getRobots().getCrawlDelay(ua));
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
