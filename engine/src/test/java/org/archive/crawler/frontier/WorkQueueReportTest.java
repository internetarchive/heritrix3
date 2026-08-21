package org.archive.crawler.frontier;

import org.archive.modules.CrawlURI;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkQueueReportTest {

    @Test
    public void testReportToWithGroup() {
        WorkQueue wq = new WorkQueue("testKey") {
            private static final long serialVersionUID = 1L;
            @Override
            protected void insertItem(WorkQueueFrontier frontier, CrawlURI curi, boolean overwriteIfPresent) {}
            @Override
            protected long deleteMatchingFromQueue(WorkQueueFrontier frontier, String match) { return 0; }
            @Override
            protected void deleteItem(WorkQueueFrontier frontier, CrawlURI item) {}
            @Override
            protected CrawlURI peekItem(WorkQueueFrontier frontier) { return null; }
        };

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        
        // Test without group
        wq.reportTo(pw);
        pw.flush();
        String report = sw.toString();
        assertTrue(report.contains("Queue testKey (p1)"));
        assertTrue(!report.contains("in queueGroup"));

        // Test with group
        sw = new StringWriter();
        pw = new PrintWriter(sw);
        wq.reportTo("testGroup", pw);
        pw.flush();
        report = sw.toString();
        assertTrue(report.contains("Queue testKey (p1) in queueGroup testGroup"));
    }
}
