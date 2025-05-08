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
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.archive.util.bdbje.EnhancedEnvironment;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author stack
 * @author gojomo
 * @version $Date: 2009-08-03 23:50:43 -0700 (Mon, 03 Aug 2009) $, $Revision: 6434 $
 */
public class ObjectIdentityBdbManualCacheTest {
    private static final Logger logger =
            Logger.getLogger(ObjectIdentityBdbManualCacheTest.class.getName());

    @TempDir
    Path tempDir;
    EnhancedEnvironment env;
    private ObjectIdentityBdbManualCache<IdentityCacheableWrapper<HashMap<String,String>>> cache;

    @BeforeEach
    public void setUp() throws Exception {
        File envDir = tempDir.toFile();
        org.archive.util.FileUtils.ensureWriteableDirectory(envDir);
        FileUtils.deleteDirectory(envDir);
        org.archive.util.FileUtils.ensureWriteableDirectory(envDir);
        env = EnhancedEnvironment.getTestEnvironment(envDir); 
        this.cache = new ObjectIdentityBdbManualCache<IdentityCacheableWrapper<HashMap<String,String>>>();
        this.cache.initialize(env,"setUpCache",IdentityCacheableWrapper.class, env.getClassCatalog());
    }

    @AfterEach
    public void tearDown() throws Exception {
        this.cache.close();
        File envDir = env.getHome();
        env.close(); 
        FileUtils.deleteDirectory(envDir);
    }

    @Test
    @EnabledIfSystemProperty(named = "runSlowTests", matches = "true", disabledReason = "it takes about 1 minute")
    public void testReadConsistencyUnderLoad() throws Exception {
        final ObjectIdentityBdbManualCache<IdentityCacheableWrapper<AtomicInteger>> cbdbmap =
            new ObjectIdentityBdbManualCache<>();
        cbdbmap.initialize(env, 
                    "consistencyCache",
                    IdentityCacheableWrapper.class,
                    env.getClassCatalog());
        try {
            final int keyCount = 128 * 1024; // 128K  keys
            final int maxLevel = 64; 
            // initial fill
            for(int i=0; i < keyCount; i++) {
                final String key = ""+i;
                cbdbmap.getOrUse(
                        key, 
                        new Supplier<IdentityCacheableWrapper<AtomicInteger>>(
                                new IdentityCacheableWrapper<AtomicInteger>(
                                        key, new AtomicInteger(0))));
            }
            // increment all keys
            for(int level = 0; level < maxLevel; level++) {
                for(int k = 0; k < keyCount; k++) {
                    IdentityCacheableWrapper<AtomicInteger> wrap = cbdbmap.get(""+k);
                    int foundValue = wrap.get().getAndIncrement();
                    wrap.makeDirty();
                    assertEquals(level, foundValue, "stale value preinc key "+k);
                }
                if(level % 10 == 0) {
                    System.out.println("level to "+level);
                    if(level>0) {
                        forceScarceMemory();
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

    @Test
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
            assertNotNull(v,"value should not be null");
            assertEquals(value, v, "value incorrect");
        }
    }
    
    /**
     * Test that in scarce memory conditions, the memory map is 
     * expunged of otherwise unreferenced entries as expected.
     */
    @Test
    @Disabled
    public void xestMemMapCleared() throws InterruptedException {
        forceScarceMemory();
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
        assertEquals(10000, cache.memMap.size());
        assertEquals(10000, cache.size());
        forceScarceMemory();
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
        assertEquals(0, cache.memMap.size(), "memMap not cleared");
    }

    /**
     * Temporarily exhaust memory, forcing weak/soft references to
     * be broken.
     */
    public static void forceScarceMemory() {
        // force soft references to be broken
        LinkedList<SoftReference<byte[]>> hog = new LinkedList<SoftReference<byte[]>>();
        long blocks = Runtime.getRuntime().maxMemory() / 1000000;
        logger.info("forcing scarce memory via "+blocks+" 1MB blocks");
        for(long l = 0; l <= blocks; l++) {
            try {
                hog.add(new SoftReference<byte[]>(new byte[1000000]));
            } catch (OutOfMemoryError e) {
                hog = null;
                logger.info("OOME triggered");
                break;
            }
        }
    }
}
