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
package org.archive.util.fingerprint;

import junit.framework.TestCase;

/**
 * Unit tests for ArrayLongFPCache. 
 * 
 * @author gojomo
 */
public class ArrayLongFPCacheTest extends TestCase {

    public void testAdd() {
        long testVal = 123456L;
        ArrayLongFPCache cache = new ArrayLongFPCache();
        assertFalse("contains test value pre-add",cache.contains(testVal));
        assertFalse("contains test value pre-add",cache.contains(-testVal));
        cache.add(testVal);
        cache.add(-testVal);
        assertTrue("should contain after add",cache.contains(testVal));
        assertTrue("should contain after add",cache.contains(-testVal));
    }

    public void testContains() {
        long testVal1 = 123456L;
        long testVal2 = 9090909090L;
        long testVal3 = 76543210234567L;
        long testVal4 = 1L;
        ArrayLongFPCache cache = new ArrayLongFPCache();
        cache.add(testVal1);
        cache.add(testVal2);
        cache.add(testVal3);
        cache.add(testVal4);
        assertTrue("should contain after add",cache.contains(testVal1));
        assertTrue("should contain after add",cache.contains(testVal2));
        assertTrue("should contain after add",cache.contains(testVal3));
        assertTrue("should contain after add",cache.contains(testVal4));
    }

    public void testReplacement() {
        ArrayLongFPCache cache = new ArrayLongFPCache();
        for(long i=0; i<=ArrayLongFPCache.DEFAULT_SMEAR; i++) {
            cache.add(i*cache.cacheLength()+1);
        }
        assertFalse("contains value after overwrite",cache.contains(1L));
        assertTrue("value not retained",cache.contains(cache.cacheLength()+1));

    }
    
    public void testRemove() {
        long testVal = 4516500024601L;
        ArrayLongFPCache cache = new ArrayLongFPCache();
        cache.add(testVal);
        cache.add(-testVal);
        assertTrue("should contain after add",cache.contains(testVal));
        assertTrue("should contain after add",cache.contains(-testVal));
        cache.remove(testVal);
        cache.remove(-testVal);
        assertFalse("contains test value after remove",cache.contains(testVal));
        assertFalse("contains test value after remove",cache.contains(-testVal));
    }

}
