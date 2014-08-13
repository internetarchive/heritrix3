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
 
package org.archive.crawler.util;

import junit.framework.TestCase;

import org.apache.commons.httpclient.URIException;

/**
 * Test TopNSetTest.

 * @contributor gojomo
 */
public class TopNSetTest extends TestCase{
    
    public void testOne() throws URIException {
        TopNSet tops = new TopNSet(20); 
        tops.update("foo",999); 
        assertEquals("wrong-sized set",1, tops.getTopSet().size());
        assertEquals("bad largest","foo",tops.getLargest());
        assertEquals("bad smallest","foo",tops.getSmallest());
    }
    
    
    public void testTwo() throws URIException {
        TopNSet tops = new TopNSet(20); 
        tops.update("foo",999); 
        tops.update("bar",101); 
        assertEquals("wrong-sized set",2,tops.getTopSet().size());
        assertEquals("bad largest","foo",tops.getLargest());
        assertEquals("bad smallest","bar",tops.getSmallest());
    }
    
    public void testInversion() throws URIException {
        TopNSet tops = new TopNSet(20); 
        tops.update("foo",999); 
        tops.update("bar",101); 
        tops.update("foo",9);
        assertEquals("wrong-sized set",2,tops.getTopSet().size());
        assertEquals("bad largest","bar",tops.getLargest());
        assertEquals("bad smallest","foo",tops.getSmallest());
    }
    
    public void testOverflowUp() throws URIException {
        TopNSet tops = new TopNSet(20); 
        for(int i = 0; i <= 100; i++) {
            tops.update(Integer.toString(i),i);
        }
        assertEquals("wrong-sized set",20,tops.getTopSet().size());
        assertEquals("bad largest","100",tops.getLargest());
        assertEquals("bad smallest","81",tops.getSmallest());
    }
}
