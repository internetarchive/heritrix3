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


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.management.openmbean.CompositeData;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.framework.CrawlerProcessorTestBase;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.framework.Frontier.FrontierGroup;
import org.archive.crawler.frontier.FrontierJournal;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.modules.fetcher.FetchStats;
import org.archive.modules.fetcher.FetchStats.Stage;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.net.UURIFactory;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.ObjectIdentityMemCache;

import junit.framework.Assert;


/**
 * Unit test for {@link QuotaEnforcer}.
 *
 * @contributor pjack
 * @contributor nlevitt
 */
public class QuotaEnforcerTest extends CrawlerProcessorTestBase {
    
    static class MockFetchStats extends FetchStats {
        private static final long serialVersionUID = 1l;

        public void setNovelBytes(long n) {
            put(NOVEL, n);
        }
        public void setNovelUrls(long n) {
            put(NOVELCOUNT, n);
        }
    }
    
    static class MockServerCache extends DefaultServerCache {
        private static final long serialVersionUID = 1l;
        public void setHostFor(String host, CrawlHost crawlHost) {
            ((ObjectIdentityMemCache<CrawlHost>) hosts).getMap().put(host, crawlHost); 
        }
        public void setServerFor(String h, CrawlServer crawlServer) {
            ((ObjectIdentityMemCache<CrawlServer>) servers).getMap().put(h, crawlServer); 
        }
    }
    
    interface CanSetSubstats {
        public void setSubstats(FetchStats stats);
    }
    
    static class MockCrawlHost extends CrawlHost implements CanSetSubstats {
        private static final long serialVersionUID = 1l;
        public MockCrawlHost(String hostname) {
            super(hostname);
        }
        @Override
        public void setSubstats(FetchStats stats) {
            this.substats = stats;
        }
    }
    
    static class MockCrawlServer extends CrawlServer implements CanSetSubstats {
        private static final long serialVersionUID = 1l;
        public MockCrawlServer(String h) {
            super(h);
        }
        @Override
        public void setSubstats(FetchStats stats) {
            this.substats = stats;
        }
    }
    
    static class MockFrontierGroup implements FrontierGroup, CanSetSubstats {
        private static final long serialVersionUID = 1l;
        
        protected FetchStats substats = new FetchStats();
        
        // the only method that's used in this mock class
        @Override
        public FetchStats getSubstats() {
            return substats;
        }
        
        @Override
        public void setSubstats(FetchStats stats) {
            this.substats = stats;
        }

        @Override
        public void tally(CrawlURI curi, Stage stage) {
        }
        @Override
        public void setIdentityCache(ObjectIdentityCache<?> cache) {
        }
        @Override
        public String getKey() {
            return null;
        }
        @Override
        public void makeDirty() {
        }
    }
    
    static class MockFrontier implements Frontier {
        protected Map<String,MockFrontierGroup> hostGroups = new HashMap<String,MockFrontierGroup>();
        
        // the only method that's used in this mock class
        @Override
        public FrontierGroup getGroup(CrawlURI curi) {
            String host;
            try {
                host = curi.getUURI().getHost();
                MockFrontierGroup group = hostGroups.get(host);
                if (group == null) {
                    group = new MockFrontierGroup();
                    hostGroups.put(host, group);
                }
                return group;
            } catch (URIException e) {
                Assert.fail();
                return null;
            }
        }

        @Override
        public void start() {
        }
        @Override
        public void stop() {
        }
        @Override
        public boolean isRunning() {
            return false;
        }
        @Override
        public void reportTo(PrintWriter writer) throws IOException {
        }
        @Override
        public void shortReportLineTo(PrintWriter pw) throws IOException {
        }
        @Override
        public Map<String, Object> shortReportMap() {
            return null;
        }
        @Override
        public String shortReportLegend() {
            return null;
        }
        @Override
        public CrawlURI next() throws InterruptedException {
            return null;
        }
        @Override
        public boolean isEmpty() {
            return false;
        }
        @Override
        public void schedule(CrawlURI caURI) {
        }
        @Override
        public void finished(CrawlURI cURI) {
        }
        @Override
        public long discoveredUriCount() {
            return 0;
        }
        @Override
        public long queuedUriCount() {
            return 0;
        }
        @Override
        public long futureUriCount() {
            return 0;
        }
        @Override
        public long deepestUri() {
            return 0;
        }
        @Override
        public long averageDepth() {
            return 0;
        }
        @Override
        public float congestionRatio() {
            return 0;
        }
        @Override
        public long finishedUriCount() {
            return 0;
        }
        @Override
        public long succeededFetchCount() {
            return 0;
        }
        @Override
        public long failedFetchCount() {
            return 0;
        }
        @Override
        public long disregardedUriCount() {
            return 0;
        }
        @Override
        public void importURIs(String params) throws IOException {
        }
        @Override
        public long importRecoverFormat(File source, boolean applyScope, boolean includeOnly, boolean forceFetch,
                String acceptTags) throws IOException {
            return 0;
        }
        @Override
        public CompositeData getURIsList(String marker, int numberOfMatches, String regex, boolean verbose) {
            return null;
        }
        @Override
        public long deleteURIs(String queueRegex, String match) {
            return 0;
        }
        @Override
        public void deleted(CrawlURI curi) {
        }
        @Override
        public void considerIncluded(CrawlURI curi) {
        }
        @Override
        public void pause() {
        }
        @Override
        public void unpause() {
        }
        @Override
        public void terminate() {
        }
        @Override
        public FrontierJournal getFrontierJournal() {
            return null;
        }
        @Override
        public String getClassKey(CrawlURI cauri) {
            return null;
        }
        @Override
        public DecideRule getScope() {
            return null;
        }
        @Override
        public void run() {
        }
        @Override
        public void requestState(State target) {
        }
        @Override
        public void beginDisposition(CrawlURI curi) {
        }
        @Override
        public void endDisposition() {
        }
    }

    // separate methods to make it easier to know what failed
    public void testHostNovelKbForceRetire() throws URIException, InterruptedException {
        testNovel("kb", "host", true);
    }
    public void testServerNovelKbForceRetire() throws URIException, InterruptedException {
        testNovel("kb", "server", true);
    }
    public void testGroupNovelKbForceRetire() throws URIException, InterruptedException {
        testNovel("kb", "group", true);
    }
    public void testHostNovelKbNoForceRetire() throws URIException, InterruptedException {
        testNovel("kb", "host", false);
    }
    public void testServerNovelKbNoForceRetire() throws URIException, InterruptedException {
        testNovel("kb", "server", false);
    }
    public void testGroupNovelKbNoForceRetire() throws URIException, InterruptedException {
        testNovel("kb", "group", false);
    }
    
    public void testHostNovelUrlsForceRetire() throws URIException, InterruptedException {
        testNovel("urls", "host", true);
    }
    public void testServerNovelUrlsForceRetire() throws URIException, InterruptedException {
        testNovel("urls", "server", true);
    }
    public void testGroupNovelUrlsForceRetire() throws URIException, InterruptedException {
        testNovel("urls", "group", true);
    }
    public void testHostNovelUrlsNoForceRetire() throws URIException, InterruptedException {
        testNovel("urls", "host", false);
    }
    public void testServerNovelUrlsNoForceRetire() throws URIException, InterruptedException {
        testNovel("urls", "server", false);
    }
    public void testGroupNovelUrlsNoForceRetire() throws URIException, InterruptedException {
        testNovel("urls", "group", false);
    }

    protected void testNovel(String urlsOrKb, String hostServerOrGroup, boolean forceRetire) throws URIException, InterruptedException {
        QuotaEnforcer qe = new QuotaEnforcer();
        MockServerCache serverCache = new MockServerCache();
        qe.setServerCache(serverCache);
        MockFrontier frontier = new MockFrontier();
        qe.setFrontier(frontier);
        qe.setForceRetire(forceRetire);

        // sanity check
        assertTrue("urls".equals(urlsOrKb) || "kb".equals(urlsOrKb));
        assertTrue("host".equals(hostServerOrGroup) || "server".equals(hostServerOrGroup) || "group".equals(hostServerOrGroup));
        
        if ("host".equals(hostServerOrGroup)) {
            if ("urls".equals(urlsOrKb)) {
                qe.setHostMaxNovelUrls(1);
                assertEquals(1, qe.getHostMaxNovelUrls());
            } else if ("kb".equals(urlsOrKb)) {
                qe.setHostMaxNovelKb(100);
                assertEquals(100, qe.getHostMaxNovelKb());
            }
        } else if ("server".equals(hostServerOrGroup)) {
            if ("urls".equals(urlsOrKb)) {
                qe.setServerMaxNovelUrls(1);
                assertEquals(1, qe.getServerMaxNovelUrls());
            } else {
                qe.setServerMaxNovelKb(100);
                assertEquals(100, qe.getServerMaxNovelKb());
            }
        } else if ("group".equals(hostServerOrGroup)) {
            if ("urls".equals(urlsOrKb)) {
                qe.setGroupMaxNovelUrls(1);
                assertEquals(1, qe.getGroupMaxNovelUrls());
            } else {
                qe.setGroupMaxNovelKb(100);
                assertEquals(100, qe.getGroupMaxNovelKb());
            }
        }
        
        // nothing accumulated yet
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://example.com/1"));
        ProcessResult res = qe.process(curi);
        assertEquals(ProcessResult.PROCEED, res);
        if (forceRetire) {
            assertNull(curi.getData().get(CoreAttributeConstants.A_FORCE_RETIRE));
        } else {
            assertEquals(FetchStatusCodes.S_UNATTEMPTED, curi.getFetchStatus());
        }

        // we do all this to set only the stats value we're testing, to avoid
        // quotas checking the wrong thing but tests passing anyway
        CanSetSubstats thing;
        if ("host".equals(hostServerOrGroup)) {
            thing = new MockCrawlHost("example.com");
            serverCache.setHostFor("example.com", (CrawlHost) thing);
        } else if ("server".equals(hostServerOrGroup)) {
            thing = new MockCrawlServer("example.com");
            serverCache.setServerFor("example.com", (CrawlServer) thing);
        } else {
            thing = (MockFrontierGroup) frontier.getGroup(curi);
        }
        MockFetchStats stats = new MockFetchStats();
        if ("urls".equals(urlsOrKb)) {
            stats.setNovelUrls(1);
        } else {
            stats.setNovelBytes(200000);
        }
        thing.setSubstats(stats);
        
        // another uri from same host should hit quota
        curi = new CrawlURI(UURIFactory.getInstance("http://example.com/2"));
        res = qe.process(curi);
        assertEquals(ProcessResult.FINISH, res);
        if (forceRetire) {
            assertTrue((Boolean) curi.getData().get(CoreAttributeConstants.A_FORCE_RETIRE));
        } else {
            assertEquals(FetchStatusCodes.S_BLOCKED_BY_QUOTA, curi.getFetchStatus());
        }
        
        // some other host has not hit quota
        curi = new CrawlURI(UURIFactory.getInstance("http://example.org/"));
        res = qe.process(curi);
        assertEquals(ProcessResult.PROCEED, res);
        if (forceRetire) {
            assertNull(curi.getData().get(CoreAttributeConstants.A_FORCE_RETIRE));
        } else {
            assertEquals(FetchStatusCodes.S_UNATTEMPTED, curi.getFetchStatus());
        }
    }
}
