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
package org.archive.crawler.selftest;

import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.crawler.util.CrawledBytesHistotable;

public class StatisticsSelfTest extends SelfTestBase {

    @Override
    protected String getSeedsString() {
        // we have these coming from different hosts, so that robots.txt is
        // fetched for both; otherwise what is crawled would be
        // non-deterministic
        return "http://127.0.0.1:7777/a.html\\nhttp://localhost:7777/b.html";
    }

    @Override
    protected void verify() throws Exception {
        verifySourceStats();
    }

    protected void verifySourceStats() throws Exception {
        StatisticsTracker stats = heritrix.getEngine().getJob("selftest-job").getCrawlController().getStatisticsTracker();
        assertNotNull(stats);
        assertTrue(stats.getTrackSources());
        CrawledBytesHistotable sourceStats = stats.getSourceStats("http://127.0.0.1:7777/index.html");
        assertNull(sourceStats);
        sourceStats = stats.getSourceStats("http://127.0.0.1:7777/a.html");
        assertNotNull(sourceStats);
        assertEquals(2, sourceStats.keySet().size());
        assertEquals(2942l, (long) sourceStats.get("novel"));
        assertEquals(3l, (long) sourceStats.get("novelCount"));

        sourceStats = stats.getSourceStats("http://localhost:7777/b.html");
        assertNotNull(sourceStats);
        assertEquals(2, sourceStats.keySet().size());
        assertEquals(9776l, (long) sourceStats.get("novel"));
        assertEquals(11l, (long) sourceStats.get("novelCount"));
    }

}
