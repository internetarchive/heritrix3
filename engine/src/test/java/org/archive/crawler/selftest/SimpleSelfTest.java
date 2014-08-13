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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * @contributor pjack
 * @contributor gojomo
 */
public class SimpleSelfTest extends SelfTestBase {

    
    final private static Set<String> EXPECTED = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(new String[] {
            "index.html", "link1.html", "link2.html", "link3.html", 
            "robots.txt", "favicon.ico"
    })));

    @Override
    protected void verify() throws Exception {
        Set<String> files = filesInArcs();
        assertEquals(EXPECTED,files);
    }
}
