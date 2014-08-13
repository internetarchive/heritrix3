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

package org.archive.util;


import java.io.IOException;
import java.util.Properties;

import junit.framework.TestCase;


/**
 * PropertyUtils tests. 
 * 
 * @contributor gojomo
 * @version $Date: 2009-11-19 14:39:53 -0800 (Thu, 19 Nov 2009) $, $Revision: 6674 $
 */
public class PropertyUtilsTest extends TestCase {
    
    public void testSimpleInterpolate() throws IOException {
        Properties props = new Properties(); 
        props.put("foo", "OOF");
        props.put("bar","RAB");
        String original = "FOO|${foo}  BAR|${bar}";
        String expected = "FOO|OOF  BAR|RAB";
        assertEquals("interpalation problem",expected,PropertyUtils.interpolateWithProperties(original,props));
    }
}
