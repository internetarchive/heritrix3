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

package org.archive.crawler.selftest;

import junit.framework.TestCase;

/**
 * @author pjack
 *
 */
public class RandomServletTest extends TestCase {

    
    
    public void testPathParse() {
        for (int i = 0; i < 1000; i++) {
            String s = RandomServletLinkWriter.toPath(i);
            // System.out.println(i +" -> " + s);
            int v = RandomServletLinkWriter.fromPath(s);
            int v2 = RandomServletLinkWriter.fromPath("/" + s);
            assertEquals(i, v);
            assertEquals(i, v2);
        }
    }

}
