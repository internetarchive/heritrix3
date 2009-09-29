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

package org.archive.util;

import java.io.File;
import java.util.HashMap;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;

/**
 * Tests of CachedBdbMap 
 * 
 * @contributor stack
 * @contributor gojomo
 * @version $Date$, $Revision$
 */
public class CachedBdbMapTest extends TmpDirTestCase {
    File envDir;
    BdbModule bdb;
    private CachedBdbMap<String,HashMap<String,String>> cache;
    
    @SuppressWarnings("unchecked")
    protected void setUp() throws Exception {
        super.setUp();
        this.envDir = new File(getTmpDir(),"CachedBdbMapTest");
        this.envDir.mkdirs();
        bdb = new BdbModule();
        bdb.getDir().setBase(null); 
        bdb.getDir().setPath(envDir.getAbsolutePath());
        bdb.start(); 
        this.cache = bdb.getCBMMap(
                this.getClass().getName(), false, String.class, HashMap.class);
    }
    
    protected void tearDown() throws Exception {
        ArchiveUtils.closeQuietly(this.cache);
        bdb.stop();
        FileUtils.deleteDirectory(this.envDir);
        super.tearDown();
    }
    
    public void testBackingDbGetsUpdated() {
        // Enable all logging. Up the level on the handlers and then
        // on the big map itself.
        Handler [] handlers = Logger.getLogger("").getHandlers();
        for (int index = 0; index < handlers.length; index++) {
            handlers[index].setLevel(Level.FINEST);
        }
        Logger.getLogger(CachedBdbMap.class.getName()).
            setLevel(Level.FINEST);
        // Set up values.
        final String value = "value";
        final String key = "key";
        final int upperbound = 3;
        // First put in empty hashmap.
        for (int i = 0; i < upperbound; i++) {
            assertNull("unexpected prior entry", 
                this.cache.putIfAbsent(key + Integer.toString(i), new HashMap<String,String>()));
        }
        // Now add value to hash map.
        for (int i = 0; i < upperbound; i++) {
            HashMap<String,String> m = this.cache.get(key + Integer.toString(i));
            m.put(key, value);
        }
        this.cache.sync();
        for (int i = 0; i < upperbound; i++) {
            HashMap<String,String> m = this.cache.get(key + Integer.toString(i));
            String v = m.get(key);
            if (v == null || !v.equals(value)) {
                Logger.getLogger(CachedBdbMap.class.getName()).
                    warning("Wrong value " + i);
            }
        }
    }
    
    /**
     * Test that in scarce memory conditions, the memory map is 
     * expunged of otherwise unreferenced entries as expected.
     * 
     * NOTE: this test may be especially fragile with regard to 
     * GC/timing issues; relies on timely finalization, which is 
     * never guaranteed by JVM/GC. For example, it is so sensitive
     * to CPU speed that a Thread.sleep(1000) succeeds when my 
     * laptop is plugged in, but fails when it is on battery!
     * 
     * @throws InterruptedException
     */
    public void testMemMapCleared() throws InterruptedException {
        System.gc();
        System.runFinalization();
        System.gc();
        assertEquals(cache.memMap.size(), 0);
        for(int i=0; i < 10000; i++) {
            cache.putIfAbsent(""+i, new HashMap<String,String>());
        }
        assertEquals(10000, cache.memMap.size());
        assertEquals(10000, cache.size());
        TestUtils.forceScarceMemory();
        Thread.sleep(2000);
        TestUtils.forceScarceMemory();
        Thread.sleep(2000);
        // The 'canary' trick makes this explicit expunge, or
        // an expunge triggered by a get() or put...(), unnecessary
        //cache.expungeStaleEntries();
        System.out.println(cache.size()+","+cache.memMap.size());
        assertEquals(0, cache.memMap.size());
    }
    
    public static void main(String [] args) {
        junit.textui.TestRunner.run(CachedBdbMapTest.class);
    }
}
