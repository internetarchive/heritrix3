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

    protected LongFPSet makeLongFPSet() {
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

