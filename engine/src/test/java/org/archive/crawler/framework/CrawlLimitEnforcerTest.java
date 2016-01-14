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
package org.archive.crawler.framework;

import org.archive.crawler.event.StatSnapshotEvent;
import org.archive.crawler.reporting.CrawlStatSnapshot;
import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.state.ModuleTestBase;

public class CrawlLimitEnforcerTest extends ModuleTestBase {

    public static class MockCrawlController extends CrawlController {
        private static final long serialVersionUID = 1l;
        public CrawlStatus stopRequestedMessage = null;
        @Override
        public synchronized void requestCrawlStop(CrawlStatus message) {
            stopRequestedMessage = message;
        }
    }

    public void testMaxBytesDownload() {
        StatisticsTracker stats = new StatisticsTracker();
        MockCrawlController cc = new MockCrawlController();

        CrawlLimitEnforcer enforcer = new CrawlLimitEnforcer();
        enforcer.setCrawlController(cc);

        enforcer.setMaxBytesDownload(1000000);

        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 1000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 600000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1000000;}}));
        assertEquals(CrawlStatus.FINISHED_DATA_LIMIT, cc.stopRequestedMessage);
    }

    public void testMaxNovelBytes() {
        StatisticsTracker stats = new StatisticsTracker();
        MockCrawlController cc = new MockCrawlController();

        CrawlLimitEnforcer enforcer = new CrawlLimitEnforcer();
        enforcer.setCrawlController(cc);

        enforcer.setMaxNovelBytes(1000000);

        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 1000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 600000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1000000;}}));
        assertEquals(CrawlStatus.FINISHED_DATA_LIMIT, cc.stopRequestedMessage);
    }


    public void testMaxNovelUrls() {
        StatisticsTracker stats = new StatisticsTracker();
        MockCrawlController cc = new MockCrawlController();

        CrawlLimitEnforcer enforcer = new CrawlLimitEnforcer();
        enforcer.setCrawlController(cc);

        enforcer.setMaxNovelUrls(100);

        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 1000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 600000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 100;}}));
        assertEquals(CrawlStatus.FINISHED_DOCUMENT_LIMIT, cc.stopRequestedMessage);
    }

    public void testMaxDocumentsDownload() {
        StatisticsTracker stats = new StatisticsTracker();
        MockCrawlController cc = new MockCrawlController();

        CrawlLimitEnforcer enforcer = new CrawlLimitEnforcer();
        enforcer.setCrawlController(cc);

        enforcer.setMaxDocumentsDownload(100);

        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 1000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 600000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 100;}}));
        assertEquals(CrawlStatus.FINISHED_DOCUMENT_LIMIT, cc.stopRequestedMessage);
    }

    public void testMaxTimeSeconds() {
        StatisticsTracker stats = new StatisticsTracker();
        MockCrawlController cc = new MockCrawlController();

        CrawlLimitEnforcer enforcer = new CrawlLimitEnforcer();
        enforcer.setCrawlController(cc);

        enforcer.setMaxTimeSeconds(600);

        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 1000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 600000;}}));
        assertEquals(CrawlStatus.FINISHED_TIME_LIMIT, cc.stopRequestedMessage);
    }
    

    public void testMaxWarcNovelBytes() {
        StatisticsTracker stats = new StatisticsTracker();
        MockCrawlController cc = new MockCrawlController();

        CrawlLimitEnforcer enforcer = new CrawlLimitEnforcer();
        enforcer.setCrawlController(cc);

        enforcer.setMaxWarcNovelBytes(1000000);

        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 1000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 600000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1000000;}}));
        assertEquals(CrawlStatus.FINISHED_DATA_LIMIT, cc.stopRequestedMessage);
    }


    public void testMaxWarcNovelUrls() {
        StatisticsTracker stats = new StatisticsTracker();
        MockCrawlController cc = new MockCrawlController();

        CrawlLimitEnforcer enforcer = new CrawlLimitEnforcer();
        enforcer.setCrawlController(cc);

        enforcer.setMaxWarcNovelUrls(100);

        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{downloadedUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 1000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{elapsedMilliseconds = 600000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{bytesProcessed = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelBytes = 1000000;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{novelUriCount = 100;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 1;}}));
        assertNull(cc.stopRequestedMessage);
        enforcer.onApplicationEvent(new StatSnapshotEvent(stats, new CrawlStatSnapshot() {{warcNovelUriCount = 100;}}));
        assertEquals(CrawlStatus.FINISHED_DOCUMENT_LIMIT, cc.stopRequestedMessage);
    }
}
