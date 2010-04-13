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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.archive.util.bdbje.EnhancedEnvironment;

/**
 * @contributor stack
 * @contributor gojomo
 * @version $Date: 2009-08-03 23:50:43 -0700 (Mon, 03 Aug 2009) $, $Revision: 6434 $
 */
public class ObjectIdentityBdbCacheTest extends TmpDirTestCase {
    EnhancedEnvironment env; 
    private ObjectIdentityBdbCache<HashMap<String,String>> cache;
    
    protected void setUp() throws Exception {
        super.setUp();
        File envDir = new File(getTmpDir(),"ObjectIdentityBdbCacheTest");
        envDir.mkdirs();
        FileUtils.deleteDirectory(envDir);
        envDir.mkdirs();
        env = EnhancedEnvironment.getTestEnvironment(envDir); 
        this.cache = new ObjectIdentityBdbCache<HashMap<String,String>>();
        this.cache.initialize(env,"setUpCache",HashMap.class, env.getClassCatalog());
    }
    
    protected void tearDown() throws Exception {
        this.cache.close();
        File envDir = env.getHome();
        env.close(); 
        FileUtils.deleteDirectory(envDir);
        super.tearDown();
    }
    
    @SuppressWarnings("unchecked")
    public void testReadConsistencyUnderLoad() throws Exception {
        final ObjectIdentityBdbCache<AtomicInteger> cbdbmap = 
            new ObjectIdentityBdbCache();
        cbdbmap.initialize(env, 
                    "consistencyCache",
                    AtomicInteger.class,
                    env.getClassCatalog());
        try {
            final AtomicInteger level = new AtomicInteger(0);
            final int keyCount = 128 * 1024; // 128K  keys
            final int maxLevel = 64; 
            // initial fill
            for(int i=0; i < keyCount; i++) {
                cbdbmap.getOrUse(""+i, new Supplier<AtomicInteger>(new AtomicInteger(level.get())));
            }
            // backward checking that all values always at level or higher
            new Thread() {
                public void run() {
                    untilmax: while(true) {
                        for(int j=keyCount-1; j >= 0; j--) {
                            int targetValue = level.get(); 
                            if(targetValue>=maxLevel) {
                                break untilmax;
                            }
                            assertTrue("stale value revseq key "+j,cbdbmap.get(""+j).get()>=targetValue);
                            Thread.yield();
                        }
                    }
                }
            }.start();
            // random checking that all values always at level or higher
            new Thread() {
                public void run() {
                    untilmax: while(true) {
                        int j = RandomUtils.nextInt(keyCount);
                        int targetValue = level.get(); 
                        if(targetValue>=maxLevel) {
                            break untilmax;
                        }
                        assertTrue("stale value random key "+j,
                                cbdbmap.get(""+j).get()>=targetValue);
                        Thread.yield();
                    }
                }
            }.start();
            // increment all keys
            for(; level.get() < maxLevel; level.incrementAndGet()) {
                for(int k = 0; k < keyCount; k++) {
                    int foundValue = cbdbmap.get(""+k).getAndIncrement();
                    assertEquals("stale value preinc key "+k, level.get(), foundValue);
                }
                if(level.get() % 10 == 0) {
                    System.out.println("level to "+level.get());
                }
                Thread.yield(); 
            }
        } finally {
            cbdbmap.close();
        }
        // SUCCESS
    }
    
    public void testBackingDbGetsUpdated() {
        // Set up values.
        final String value = "value";
        final String key = "key";
        final int upperbound = 3;
        // First put in empty hashmap.
        for (int i = 0; i < upperbound; i++) {
            this.cache.getOrUse(key + Integer.toString(i), new Supplier<HashMap<String,String>>(new HashMap<String,String>()));
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
            assertNotNull("value should not be null",v);
            assertEquals("value incorrect", value, v);
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
        TestUtils.forceScarceMemory();
        System.gc(); // minimize effects of earlier test heap use
        assertEquals(0, cache.memMap.size());
        assertEquals(0, cache.diskMap.size());
        for(int i=0; i < 10000; i++) {
            cache.getOrUse(""+i, new Supplier<HashMap<String, String>>(new HashMap<String,String>()));
        }
        assertEquals(cache.memMap.size(), 10000);
        assertEquals(cache.size(), 10000);
        TestUtils.forceScarceMemory();
        Thread.sleep(3000);
        // The 'canary' trick may make this explicit page-out, or
        // a page-out riggered by a get() or put...(), unnecessary --
        // but we include anyway.
        cache.pageOutStaleEntries();
        System.out.println(cache.size()+","+cache.memMap.size());
        assertEquals("memMap not cleared", 0, cache.memMap.size());
    }
    
    
    public static void main(String [] args) {
        junit.textui.TestRunner.run(ObjectIdentityBdbCacheTest.class);
    }
}
