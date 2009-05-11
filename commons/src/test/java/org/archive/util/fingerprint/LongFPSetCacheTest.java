/* LongFPSetCacheTest
 *
 * $Id$
 *
 * Created Wed Jan 21 09:00:29 CET 2004
 *
 *  Copyright (C) 2004 Internet Archive.
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

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * JUnit test suite for LongFPSetCache
 *
 * @author <a href="mailto:me@jamesc.net">James Casey</a>
 * @version $ Id:$
 */
public class LongFPSetCacheTest extends LongFPSetTestCase {
    /**
     * Create a new LongFPSetCacheTest object
     *
     * @param testName the name of the test
     */
    public LongFPSetCacheTest(final String testName) {
        super(testName);
    }

    /**
     * run all the tests for LongFPSetCacheTest
     *
     * @param argv the command line arguments
     */
    public static void main(String argv[]) {
        junit.textui.TestRunner.run(suite());
    }

    /**
     * return the suite of tests for LongFPSetCacheTest
     *
     * @return the suite of test
     */
    public static Test suite() {
        return new TestSuite(LongFPSetCacheTest.class);
    }

    LongFPSet makeLongFPSet() {
        return new LongFPSetCache();
    }

    /**
     *  This is a cache buffer, which does not grow,
     * but chucks out old values.  Therefore it has a different behaviour
     * from all the other LongFPSets.  We do a different test here.
     */

    public void testCount() {
        LongFPSet fpSet = new LongFPSetCache();
        // TODO: for some reason, when run in a debugger, 
        // the cache-item-discard is glacially slow. It's
        // reasonable when executing.) So, reducing the 
        // number of past-saturation tests in order to let
        // full-unit-tests complete more quickly. 
        final int NUM = 800; // was 1000
        final int MAX_ENTRIES = 768;

        assertEquals("empty set to start", 0, fpSet.count());

        for (int i = 1; i < NUM; ++i) {
            fpSet.add((long) i);
            assertEquals("correct num on add",
                    i<MAX_ENTRIES?i:MAX_ENTRIES, fpSet.count());
        }
    }

    // TODO - implement test methods in LongFPSetCacheTest
}

