package org.archive.crawler.frontier;

import com.sleepycat.je.DatabaseException;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.archive.util.ObjectIdentityCache;
import org.junit.jupiter.api.Test;

import javax.management.openmbean.CompositeData;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class WorkQueueFrontierLogTest {

    @Test
    public void testLogAddsQueueGroupAnnotation() throws Exception {
        WorkQueueFrontier frontier = new WorkQueueFrontier() {
            @Override
            public CompositeData getURIsList(String marker, int numberOfMatches, String regex, boolean verbose) {
                return null;
            }

            @Override
            public FrontierGroup getGroup(CrawlURI curi) {
                return null;
            }

            @Override
            public long exportPendingUris(PrintWriter writer) {
                return 0;
            }

            @Override
            public ObjectIdentityCache<WorkQueue> getAllQueues() {
                return null;
            }

            @Override
            public BlockingQueue<String> getReadyClassQueues() {
                return null;
            }

            @Override
            public Set<WorkQueue> getInProcessQueues() {
                return Set.of();
            }

            @Override
            protected boolean workQueueDataOnDisk() {
                return false;
            }
            @Override
            public void initAllQueues() {
            }

            @Override
            protected void initOtherQueues() throws DatabaseException {

            }

            @Override
            protected SortedMap<Integer, Queue<String>> getInactiveQueuesByPrecedence() {
                return null;
            }

            @Override
            protected Queue<String> createInactiveQueueForPrecedence(int precedence) {
                return null;
            }

            @Override
            protected Queue<String> getRetiredQueues() {
                return null;
            }

            @Override
            protected WorkQueue getQueueFor(String classKey) {
                return null;
            }

            @Override
            protected void initInternalQueues() {
            }
        };

        QueueGroupManager qgm = new QueueGroupManager();
        QueueGroup g = new QueueGroup("testGroup");
        g.setGroupMembersByHost(Arrays.asList("example.com"));
        qgm.setGroups(Arrays.asList(g));
        frontier.setQueueGroupManager(qgm);

        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://example.com/"));
        curi.setClassKey("example.com");

        // We need a dummy logger module to avoid NullPointerException in super.log(curi)
        // However, we only care if the annotation is added before super.log(curi) is called.
        // Let's use a simpler approach: check annotations after calling our log method.
        
        try {
            frontier.log(curi);
        } catch (NullPointerException e) {
            // Expected because loggerModule is not set, but annotation should be added already
        }

        assertTrue(curi.getAnnotations().contains("queueGroup:testGroup"), 
            "Annotations should contain queueGroup:testGroup. Found: " + curi.getAnnotations());
    }
}
