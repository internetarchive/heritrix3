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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit test suite for LongFPSet.  This is an abstract class which defines
 * the generic tests that test the {@link LongFPSet} interface.  Subclasses
 * may test specifics of {@link LongFPSet} subclass implementations
 *
 * @author <a href="mailto:me@jamesc.net">James Casey</a>
 * @version $ Id:$
 */
abstract public class LongFPSetTestCase {

    /** the underlying FPSet we wish to test */
    private LongFPSet fpSet;

    @BeforeEach
    public void setUp() {
        fpSet = makeLongFPSet();
    }

    protected abstract LongFPSet makeLongFPSet();

    /**
     * check that we can add fingerprints
     */
    @Test
    void testAdd() {
        long l1 = (long) 1234;
        long l2 = (long) 2345;

        assertEquals(0, fpSet.count(), "empty set to start");
        assertTrue(fpSet.add(l1), "set changed on addition of l1");
        assertTrue(fpSet.add(l2), "set changed on addition of l2");
        assertFalse(fpSet.add(l1), "set didn't change on re-addition of l1");
    }

    /**
     * check we can call add/remove/contains() with 0 as a value
     */
    @Test
    void testWithZero() {
        long zero = (long) 0;

        assertEquals(0, fpSet.count(), "empty set to start");
        assertFalse(fpSet.contains(zero), "zero is not there");
        assertTrue(fpSet.add(zero), "zero added");

        // now one element
        assertEquals(1, fpSet.count(), "one fp in set");
        assertTrue(fpSet.contains(zero), "zero is the element");

        // and remove
        assertTrue(fpSet.remove(zero), "zero removed");
        assertEquals(0, fpSet.count(), "empty set again");
    }

    /**
     * check that contains() does what we expect
     */
    @Test
    void testContains() {
        long l1 = (long) 1234;
        long l2 = (long) 2345;
        long l3 = (long) 1334;

        assertEquals(0, fpSet.count(), "empty set to start");
        fpSet.add(l1);
        fpSet.add(l2);

        assertTrue(fpSet.contains(l1), "contains l1");
        assertTrue(fpSet.contains(l2), "contains l2");
        assertFalse(fpSet.contains(l3), "does not contain l3");
    }

    /**
     * test remove() works as expected
     */
    @Test
    void testRemove() {
        long l1 = (long) 1234;

        assertEquals(0, fpSet.count(), "empty set to start");

        // remove before it's there
        assertFalse(fpSet.remove(l1), "fp not in set");
        // now add
        fpSet.add(l1);
        // and remove again
        assertTrue(fpSet.remove(l1), "fp was in set");
        // check set is empty again
        assertEquals(0, fpSet.count(), "empty set again");
    }

    /**
     * check count works ok
     */
    @Test
    void testCount() {
        final int NUM = 1000;
        assertEquals(0, fpSet.count(), "empty set to start");

        for (int i = 1; i < NUM; ++i) {
            fpSet.add((long) i);
            assertEquals(i, fpSet.count(), "correct num");
        }
        for (int i = NUM - 1; i > 0; --i) {
            fpSet.remove((long) i);
            assertEquals(i - 1, fpSet.count(), "correct num");
        }
        assertEquals(0, fpSet.count(), "empty set to start");
    }
}

