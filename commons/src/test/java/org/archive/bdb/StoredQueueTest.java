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

package org.archive.bdb;

import java.io.File;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.io.FileUtils;
import org.archive.util.bdbje.EnhancedEnvironment;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class StoredQueueTest {
    @TempDir
    Path tempDir;
    StoredQueue<String> queue;
    EnhancedEnvironment env;
    Database db; 
    File envDir; 

    @BeforeEach
    public void setUp() throws Exception {
        this.envDir = new File(tempDir.toFile(),"StoredMapTest");
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
        this.queue = new StoredQueue<String>(db, String.class, env.getClassCatalog());
    }

    @AfterEach
    protected void tearDown() throws Exception {
        db.close();
        env.close(); 
        FileUtils.deleteDirectory(this.envDir);
    }

    @Test
    public void testAdd() {
        assertEquals(0, queue.size(), "not empty at start");
        fill(queue, 10);
        assertEquals(10, queue.size(), "unexpected size at full");
    }

    /**
     * @deprecated Use {@link #fill(Queue, int)} instead
     */
    protected void fill(int size) {
        fill(queue, size);
    }

    protected void fill(java.util.Queue<String> q, int size) {
        for (int i = 1; i <= size; i++) {
            q.add("item-" + i);
        }
    }

    protected int drain(java.util.Queue<String> q) {
        int count = 0;
        while (true) {
            try {
                q.remove();
                count++;
            } catch (NoSuchElementException nse) {
                return count;
            }
        }
    }

    @Test
    public void testClear() {
        fill(queue, 10);
        queue.clear();
        assertEquals(0, queue.size(), "unexpected size after clear");
    }

    @Test
    public void testRemove() {
        fill(queue, 10);
        assertEquals("item-1", queue.remove(), "unexpected remove value");
        assertEquals(9, drain(queue), "improper count of removed items");
        assertThrows(NoSuchElementException.class, () -> {
            queue.remove();
        });
    }

    @Test
    public void testOrdering() {
        fill(queue, 10);
        for (int i = 1; i <= 10; i++) {
            assertEquals("item-" + i, queue.remove(), "unexpected remove value");
        }
    }

    @Test
    public void testElement() {
        fill(queue, 10);
        assertEquals("item-1", queue.element(), "unexpected element value");
        assertEquals(queue.peek(), queue.element(), "unexpected element value");
        queue.clear();
        assertThrows(NoSuchElementException.class, () -> {
            queue.element();
        });
    }

    @Test
    public void testIdentity() {
        fill(queue, 10);
        String peek1 = queue.peek();
        String peek2 = queue.peek();
        assertSame(peek1, peek2, "peeks of same item note identical object");
    }

    public void xestTimingsAgainstLinkedBlockingQueue() {
        tryTimings(50000);
        tryTimings(500000);
    }

    private void tryTimings(int i) {
        LinkedBlockingQueue<String> lbq = new LinkedBlockingQueue<String>();
        long start = System.currentTimeMillis();
        fill(lbq,i);
        drain(lbq);
        long finish = System.currentTimeMillis();
        System.out.println("LBQ - "+i+":"+(finish-start));
        start = System.currentTimeMillis();
        fill(queue,i);
        drain(queue);
        finish = System.currentTimeMillis();
        System.out.println("SQ - "+i+":"+(finish-start));
    }
}
