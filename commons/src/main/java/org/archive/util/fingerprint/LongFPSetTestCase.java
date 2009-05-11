/* LongFPSetTestCase
 *
 * $Id$
 *
 * Created Wed Jan 21 09:00:29 CET 2004
 *
 * Copyright (C) 2004 Internet Archive.
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
 * JUnit test suite for LongFPSet.  This is an abstract class which defines
 * the generic tests that test the {@link LongFPSet} interface.  Subclasses
 * may test specifics of {@link LongFPSet} subclass implementations
 *
 * @author <a href="mailto:me@jamesc.net">James Casey</a>
 * @version $ Id:$
 */
abstract public class LongFPSetTestCase extends TestCase {

    /** the unerlying FPSet we wish to test */
    private LongFPSet fpSet;

    /**
     * Create a new LongFPSetTest object
     *
     * @param testName the name of the test
     */
    public LongFPSetTestCase(final String testName) {
        super(testName);
    }

    public void setUp() {
        fpSet = makeLongFPSet();
    }

    abstract LongFPSet makeLongFPSet();

    /** check that we can add fingerprints */
    public void testAdd() {
        long l1 = (long)1234;
        long l2 = (long)2345;

        assertEquals("empty set to start", 0, fpSet.count());
        assertTrue("set changed on addition of l1", fpSet.add(l1));
        assertTrue("set changed on addition of l2", fpSet.add(l2));
        assertFalse("set didn't change on re-addition of l1", fpSet.add(l1));
    }

    /** check we can call add/remove/contains() with 0 as a value */
    public void testWithZero() {
        long zero = (long)0;

        assertEquals("empty set to start", 0, fpSet.count());
        assertFalse("zero is not there", fpSet.contains(zero));
        assertTrue("zero added", fpSet.add(zero));

        // now one element
        assertEquals("one fp in set", 1, fpSet.count());
        assertTrue("zero is the element", fpSet.contains(zero));

        // and remove
        assertTrue("zero removed", fpSet.remove(zero));
        assertEquals("empty set again", 0, fpSet.count());
    }

    /** check that contains() does what we expect */
    public void testContains() {
        long l1 = (long) 1234;
        long l2 = (long) 2345;
        long l3 = (long) 1334;

        assertEquals("empty set to start", 0, fpSet.count());
        fpSet.add(l1);
        fpSet.add(l2);

        assertTrue("contains l1", fpSet.contains(l1));
        assertTrue("contains l2", fpSet.contains(l2));
        assertFalse("does not contain l3", fpSet.contains(l3));
    }

    /** test remove() works as expected */
    public void testRemove() {
        long l1 = (long) 1234;

        assertEquals("empty set to start", 0, fpSet.count());

        // remove before it's there
        assertFalse("fp not in set", fpSet.remove(l1));
        // now add
        fpSet.add(l1);
        // and remove again
        assertTrue("fp was in set", fpSet.remove(l1));
        // check set is empty again
        assertEquals("empty set again", 0, fpSet.count());
    }

    /** check count works ok */
    public void testCount() {
        final int NUM = 1000;
        assertEquals("empty set to start", 0, fpSet.count());

        for(int i = 1; i < NUM; ++i) {
            fpSet.add((long)i);
            assertEquals("correct num", i, fpSet.count());
        }
        for (int i = NUM - 1; i > 0; --i) {
            fpSet.remove((long) i);
            assertEquals("correct num", i -1, fpSet.count());
        }
        assertEquals("empty set to start", 0, fpSet.count());

    }
}

