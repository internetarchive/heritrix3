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
package org.archive.crawler.frontier;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.tree.Key;
import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;
import org.archive.bdb.StoredQueue;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.url.URIException;
import org.archive.util.Recorder;
import org.archive.util.bdbje.EnhancedEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for BdbMultipleWorkQueues functionality.
 *
 * @author gojomo
 */
public class BdbMultipleWorkQueuesTest {
    private static Logger logger =
        Logger.getLogger(BdbMultipleWorkQueuesTest.class.getName());
    @TempDir
    Path tempDir;
    @TempDir
    Path curiTempDir;

    private BdbMultipleWorkQueues pendingUris = null;
    private EnhancedEnvironment env;
    private Database db;
    private File envDir;

    protected Recorder getRecorder() throws IOException {
        if (Recorder.getHttpRecorder() == null) {
            Recorder httpRecorder = new Recorder(curiTempDir.toFile(),
                    getClass().getName(), 16 * 1024, 512 * 1024);
            Recorder.setHttpRecorder(httpRecorder);
        }

        return Recorder.getHttpRecorder();
    }

    protected CrawlURI makeCrawlURI(String uri) throws URIException,
            IOException {
        UURI uuri = UURIFactory.getInstance(uri);
        CrawlURI curi = new CrawlURI(uuri);
        curi.setClassKey("key");
        curi.setSeed(true);
        curi.setRecorder(getRecorder());
        return curi;
    }

    @BeforeEach
    protected void setUp() throws Exception {
        this.envDir = new File(tempDir.toFile(),"BdbMultipleWorkQueuesTest");
        org.archive.util.FileUtils.ensureWriteableDirectory(this.envDir);
        try {
            EnvironmentConfig envConfig = new EnvironmentConfig();
            envConfig.setTransactional(false);
            envConfig.setAllowCreate(true);
            env = new EnhancedEnvironment(envDir,envConfig);
            BdbModule.BdbConfig dbConfig = StoredQueue.databaseConfig();
            db = env.openDatabase(null, "StoredMapTest", dbConfig.toDatabaseConfig());
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        this.pendingUris = new BdbMultipleWorkQueues(db, env.getClassCatalog());

    }

    @AfterEach
    protected void tearDown() throws Exception {
        if(this.pendingUris!=null)
            this.pendingUris.close();
        if (this.envDir.exists()) {
            FileUtils.deleteDirectory(this.envDir);
        }
    }

    /**
     * Basic sanity checks for calculateInsertKey() -- ensure ordinal, cost,
     * and schedulingDirective have the intended effects, for ordinal values
     * up through 1/4th of the maximum (about 2^61).
     */
    @Test
    public void testCalculateInsertKey() throws URIException {
        while(Thread.interrupted()) {
            logger.warning("stray interrupt cleared");
        }

        for (long ordinalOrigin = 1; ordinalOrigin < Long.MAX_VALUE / 4; ordinalOrigin <<= 1) {
            CrawlURI curi1 =
                new CrawlURI(UURIFactory.getInstance("http://archive.org/foo"));
            curi1.setOrdinal(ordinalOrigin);
            curi1.setClassKey("foo");
            byte[] key1 =
                BdbMultipleWorkQueues.calculateInsertKey(curi1).getData();
            CrawlURI curi2 =
                new CrawlURI(UURIFactory.getInstance("http://archive.org/bar"));
            curi2.setOrdinal(ordinalOrigin + 1);
            curi2.setClassKey("foo");
            byte[] key2 =
                BdbMultipleWorkQueues.calculateInsertKey(curi2).getData();
            CrawlURI curi3 =
                new CrawlURI(UURIFactory.getInstance("http://archive.org/baz"));
            curi3.setOrdinal(ordinalOrigin + 2);
            curi3.setClassKey("foo");
            curi3.setSchedulingDirective(SchedulingConstants.HIGH);
            byte[] key3 =
                BdbMultipleWorkQueues.calculateInsertKey(curi3).getData();
            CrawlURI curi4 =
                new CrawlURI(UURIFactory.getInstance("http://archive.org/zle"));
            curi4.setOrdinal(ordinalOrigin + 3);
            curi4.setClassKey("foo");
            curi4.setPrecedence(2);
            byte[] key4 =
                BdbMultipleWorkQueues.calculateInsertKey(curi4).getData();
            CrawlURI curi5 =
                new CrawlURI(UURIFactory.getInstance("http://archive.org/gru"));
            curi5.setOrdinal(ordinalOrigin + 4);
            curi5.setClassKey("foo");
            curi5.setPrecedence(1);
            byte[] key5 =
                BdbMultipleWorkQueues.calculateInsertKey(curi5).getData();
            // ensure that key1 (with lower ordinal) sorts before key2 (higher
            // ordinal)
            assertTrue(Key.compareKeys(key1, key2, null) < 0,
                    "lower ordinal sorting first (" + ordinalOrigin + ")");
            // ensure that key3 (with HIGH scheduling) sorts before key2 (even
            // though
            // it has lower ordinal)
            assertTrue(Key.compareKeys(key3, key2, null) < 0,
                    "lower directive sorting first (" + ordinalOrigin + ")");
            // ensure that key5 (with lower cost) sorts before key4 (even though 
            // key4  has lower ordinal and same default NORMAL scheduling directive)
            assertTrue(Key.compareKeys(key5, key4, null) < 0,
                    "lower cost sorting first (" + ordinalOrigin + ")");
        }
    }

    @Test
    public void testThreadInterrupt() throws InterruptedException, IOException {
        MockToeThread mockToeThread = new MockToeThread(this.pendingUris, makeCrawlURI("http://www.archive.org"));

        mockToeThread.start();

        while (mockToeThread.isAlive()) {
            Thread.sleep(100);
        }
        mockToeThread.join();
        assertNull(mockToeThread.thrownException);

    }
    class MockToeThread extends Thread {
        BdbMultipleWorkQueues pendingUris;
        CrawlURI curi;
        Exception thrownException;
        public MockToeThread(BdbMultipleWorkQueues pendingUris, CrawlURI curi) {
            this.pendingUris = pendingUris;
            this.curi = curi;
            this.thrownException = null;
        }
        @Override
        public void run() {
            this.pendingUris.put(this.curi, true);

            Thread.currentThread().interrupt();
            try {
                this.pendingUris.put(this.curi, true);
            }
            catch (com.sleepycat.je.EnvironmentFailureException ex) {
                this.thrownException = ex;
            }

        }
    }

}
