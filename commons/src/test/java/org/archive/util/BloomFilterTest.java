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

import junit.framework.TestCase;


/**
 * BloomFilter tests
 * 
 * @contributor gojomo
 * @version $Date: 2009-11-19 14:39:53 -0800 (Thu, 19 Nov 2009) $, $Revision: 6674 $
 */
public abstract class BloomFilterTest extends TestCase {
    protected BloomFilter bloom; 
    
    protected abstract void setUp() throws Exception;
    
    public void testBasics() {
        // require initial additions to return 'true' (for 'added')
        assertTrue(bloom.add("abracadabra"));
        assertTrue(bloom.add("foobar"));
        assertTrue(bloom.add("rumplestiltskin"));
        assertTrue(bloom.add("buckaroobanzai"));
        assertTrue(bloom.add("scheherazade"));
        
        // require readdition to return 'false' (not added because already present)
        assertFalse(bloom.add("abracadabra"));
        assertFalse(bloom.add("foobar"));
        assertFalse(bloom.add("rumplestiltskin"));
        assertFalse(bloom.add("buckaroobanzai"));
        assertFalse(bloom.add("scheherazade"));
    }
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        bloom = null; 
    }
}
