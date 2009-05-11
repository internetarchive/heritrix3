/* MemLongFPSetTest
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
 *
 * File: MemLongFPSetTest.java
 */

package org.archive.util.fingerprint;


import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * JUnit test suite for MemLongFPSet
 *
 * @author <a href="mailto:me@jamesc.net">James Casey</a>
 * @version $ Id:$
 */
public class MemLongFPSetTest extends LongFPSetTestCase {
        /**
         * Create a new MemLongFPSetTest object
         *
         * @param testName the name of the test
         */
        public MemLongFPSetTest(final String testName) {
                super(testName);
        }

        /**
         * run all the tests for MemLongFPSetTest
         *
         * @param argv the command line arguments
         */
        public static void main(String argv[]) {
                junit.textui.TestRunner.run(suite());
        }

        /**
         * return the suite of tests for MemLongFPSetTest
         *
         * @return the suite of test
         */
        public static Test suite() {
                return new TestSuite(MemLongFPSetTest.class);
        }

        LongFPSet makeLongFPSet() {
                return new MemLongFPSet();
        }

        public void testFoo() {

        }
}

