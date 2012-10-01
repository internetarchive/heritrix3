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

        protected LongFPSet makeLongFPSet() {
                return new MemLongFPSet();
        }

        public void testFoo() {

        }
}

