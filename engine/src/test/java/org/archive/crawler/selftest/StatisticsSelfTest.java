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
    protected String changeGlobalConfig(String config) {
        String warcWriterConfig = " <bean id='warcWriter' class='org.archive.modules.writer.WARCWriterProcessor'/>\n";
        config = config.replace("<!--@@MORE_EXTRACTORS@@-->", warcWriterConfig);
        return super.changeGlobalConfig(config);
    }

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
        verifyWarcStats();
    }

    protected void verifyWarcStats() {
        StatisticsTracker stats = heritrix.getEngine().getJob("selftest-job").getCrawlController().getStatisticsTracker();
        assertNotNull(stats);
        assertEquals(14, (long) stats.getCrawledBytes().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
        assertEquals(12669, (long) stats.getCrawledBytes().get(CrawledBytesHistotable.WARC_NOVEL_CONTENT_BYTES) - stats.getBytesPerHost("dns:"));

        assertEquals(3, (long) stats.getServerCache().getHostFor("127.0.0.1").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
        assertEquals(2942, (long) stats.getServerCache().getHostFor("127.0.0.1").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_CONTENT_BYTES));
        assertEquals(10, (long) stats.getServerCache().getHostFor("localhost").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
        assertEquals(9727, (long) stats.getServerCache().getHostFor("localhost").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_CONTENT_BYTES));
        assertEquals(1, (long) stats.getServerCache().getHostFor("dns:").getSubstats().get(CrawledBytesHistotable.WARC_NOVEL_URLS));
    }

    protected void verifySourceStats() throws Exception {
        StatisticsTracker stats = heritrix.getEngine().getJob("selftest-job").getCrawlController().getStatisticsTracker();
        assertNotNull(stats);
        assertTrue(stats.getTrackSources());
        CrawledBytesHistotable sourceStats = stats.getSourceStats("http://127.0.0.1:7777/index.html");
        assertNull(sourceStats);
        sourceStats = stats.getSourceStats("http://127.0.0.1:7777/a.html");
        assertNotNull(sourceStats);
        assertEquals(4, sourceStats.keySet().size());
        assertEquals(2942l, (long) sourceStats.get("novel"));
        assertEquals(3l, (long) sourceStats.get("novelCount"));
        assertEquals(2942l, (long) sourceStats.get("warcNovelContentBytes"));
        assertEquals(3l, (long) sourceStats.get("warcNovelUrls"));

        sourceStats = stats.getSourceStats("http://localhost:7777/b.html");
        assertNotNull(sourceStats);
        assertEquals(4, sourceStats.keySet().size());
        assertEquals(9727l, (long) sourceStats.get("novel") - stats.getBytesPerHost("dns:"));
        assertEquals(11l, (long) sourceStats.get("novelCount"));
        assertEquals(9727l, (long) sourceStats.get("warcNovelContentBytes") - stats.getBytesPerHost("dns:"));
        assertEquals(11l, (long) sourceStats.get("warcNovelUrls"));
    }

}
