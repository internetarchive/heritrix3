/* ArrayLongFPCacheTest
*
* $Id$
*
* Created on Oct 5, 2005
*
* Copyright (C) 2005 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
