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
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;
import org.archive.bdb.StoredQueue;
import org.archive.util.TmpDirTestCase;
import org.archive.util.bdbje.EnhancedEnvironment;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;

public class StoredQueueTest extends TmpDirTestCase {
    StoredQueue<String> queue;
    EnhancedEnvironment env;
    Database db; 
    File envDir; 

    protected void setUp() throws Exception {
        super.setUp();
        this.envDir = new File(getTmpDir(),"StoredMapTest");
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
    
    protected void tearDown() throws Exception {
        db.close();
        env.close(); 
        FileUtils.deleteDirectory(this.envDir);
        super.tearDown();
    }
    
    public void testAdd() {
        assertEquals("not empty at start",0,queue.size());
        fill(queue, 10);
        assertEquals("unexpected size at full",10,queue.size());
    }

    /**
     * @deprecated Use {@link #fill(Queue,int)} instead
     */
    protected void fill(int size) {
        fill(queue, size);
    }

    protected void fill(java.util.Queue<String> q, int size) {
        for(int i = 1; i <= size; i++) {
            q.add("item-"+i);
        }
    }
    
    protected int drain(java.util.Queue<String> q) {
        int count = 0; 
        while(true) {
            try {
                q.remove();
                count++;
            } catch(NoSuchElementException nse) {
                return count;
            }
        }
    }

    public void testClear() {
        fill(queue, 10);
        queue.clear();
        assertEquals("unexpected size after clear",0,queue.size());
    }

    public void testRemove() {
        fill(queue, 10);
        assertEquals("unexpected remove value","item-1",queue.remove());
        assertEquals("improper count of removed items",9,drain(queue));
        try {
            queue.remove();
            fail("expected NoSuchElementException not received");
        } catch (NoSuchElementException nse) {
            // do nothing
        }
    }
    
    public void testOrdering() {
        fill(queue, 10);
        for(int i = 1; i <= 10; i++) {
            assertEquals("unexpected remove value","item-"+i,queue.remove());
        }
    }

    public void testElement() {
        fill(queue, 10);
        assertEquals("unexpected element value","item-1",queue.element());
        assertEquals("unexpected element value",queue.peek(),queue.element());
        queue.clear();
        try {
            queue.element();
            fail("expected NoSuchElementException not received");
        } catch (NoSuchElementException nse) {
            // do nothing
        }
    }
    
    public void testIdentity() {
        fill(queue,10);
        String peek1 = queue.peek();
        String peek2 = queue.peek();
        assertTrue("peeks of same item note identical object",peek1==peek2);
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
