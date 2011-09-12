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
public class ObjectIdentityBdbManualCacheTest extends TmpDirTestCase {
    EnhancedEnvironment env; 
    private ObjectIdentityBdbManualCache<IdentityCacheableWrapper<HashMap<String,String>>> cache;
    
    protected void setUp() throws Exception {
        super.setUp();
        File envDir = new File(getTmpDir(),"ObjectIdentityBdbCacheTest");
        org.archive.util.FileUtils.ensureWriteableDirectory(envDir);
        FileUtils.deleteDirectory(envDir);
        org.archive.util.FileUtils.ensureWriteableDirectory(envDir);
        env = EnhancedEnvironment.getTestEnvironment(envDir); 
        this.cache = new ObjectIdentityBdbManualCache<IdentityCacheableWrapper<HashMap<String,String>>>();
        this.cache.initialize(env,"setUpCache",IdentityCacheableWrapper.class, env.getClassCatalog());
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
        final ObjectIdentityBdbManualCache<IdentityCacheableWrapper<AtomicInteger>> cbdbmap = 
            new ObjectIdentityBdbManualCache();
        cbdbmap.initialize(env, 
                    "consistencyCache",
                    IdentityCacheableWrapper.class,
                    env.getClassCatalog());
        try {
            final AtomicInteger level = new AtomicInteger(0);
            final int keyCount = 128 * 1024; // 128K  keys
            final int maxLevel = 64; 
            // initial fill
            for(int i=0; i < keyCount; i++) {
                final String key = ""+i;
                cbdbmap.getOrUse(
                        key, 
                        new Supplier<IdentityCacheableWrapper<AtomicInteger>>(
                                new IdentityCacheableWrapper<AtomicInteger>(
                                        key, new AtomicInteger(level.get()))));
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
                            assertTrue("stale value revseq key "+j,cbdbmap.get(""+j).get().get()>=targetValue);
                            Thread.yield();
                        }
                    }
                }
            };//.start();
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
                                cbdbmap.get(""+j).get().get()>=targetValue);
                        Thread.yield();
                    }
                }
            };//.start();
            // increment all keys
            for(; level.get() < maxLevel; level.incrementAndGet()) {
                for(int k = 0; k < keyCount; k++) {
                    IdentityCacheableWrapper<AtomicInteger> wrap = cbdbmap.get(""+k);
                    int foundValue = wrap.get().getAndIncrement();
                    wrap.makeDirty();
                    assertEquals("stale value preinc key "+k, level.get(), foundValue);
                }
                if(level.get() % 10 == 0) {
                    System.out.println("level to "+level.get());
                    if(level.get()>0) {
                        TestUtils.forceScarceMemory();
                    }
                    System.out.println("OIBMCT:"+cbdbmap.composeCacheSummary());
                }
                Thread.yield(); 
            }
        } finally {
            System.err.println("OIBMCT:"+cbdbmap.composeCacheSummary());
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
            String innerKey = key + Integer.toString(i);
            this.cache.getOrUse(
                innerKey, 
                new Supplier<IdentityCacheableWrapper<HashMap<String,String>>>(
                    new IdentityCacheableWrapper<HashMap<String,String>>(
                        innerKey, new HashMap<String,String>()))); 
        }
        // Now add value to hash map.
        for (int i = 0; i < upperbound; i++) {
            HashMap<String,String> m = this.cache.get(key + Integer.toString(i)).get();
            m.put(key, value);
        }
        this.cache.sync();
        for (int i = 0; i < upperbound; i++) {
            HashMap<String,String> m = this.cache.get(key + Integer.toString(i)).get();
            String v = m.get(key);
            assertNotNull("value should not be null",v);
            assertEquals("value incorrect", value, v);
        }
    }
    
    /**
     * Test that in scarce memory conditions, the memory map is 
     * expunged of otherwise unreferenced entries as expected.
     * @throws InterruptedException
     */
    public void xestMemMapCleared() throws InterruptedException {
        TestUtils.forceScarceMemory();
        System.gc(); // minimize effects of earlier test heap use
        assertEquals(0, cache.memMap.size());
        assertEquals(0, cache.diskMap.size());
        for(int i=0; i < 10000; i++) {
            String key = ""+i; 
            cache.getOrUse(
                key, 
                new Supplier<IdentityCacheableWrapper<HashMap<String,String>>>(
                        new IdentityCacheableWrapper<HashMap<String,String>>(
                            key, new HashMap<String,String>())));           
        }
        assertEquals(cache.memMap.size(), 10000);
        assertEquals(cache.size(), 10000);
        TestUtils.forceScarceMemory();
        Thread.sleep(6000);
        // The 'canary' trick may make this explicit page-out, or
        // a page-out riggered by a get() or put...(), unnecessary --
        // but we include anyway.
        //cache.pageOutStaleEntries();
        
        int countNonNull = 0; 
        for(String key: cache.memMap.keySet()) {
            if(cache.memMap.get(key)!=null) {
                countNonNull++;
            }
        }
        System.out.println(cache.size()+","+cache.memMap.size()+","+cache.memMap.keySet().size()+","+cache.memMap.values().size()+","+countNonNull);
        assertEquals("memMap not cleared", 0, cache.memMap.size());
    }
    
    
    public static void main(String [] args) {
        junit.textui.TestRunner.run(ObjectIdentityBdbManualCacheTest.class);
    }
}
