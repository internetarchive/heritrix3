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
