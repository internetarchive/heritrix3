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
package org.archive.surt;

import junit.framework.TestCase;

import org.apache.commons.httpclient.URIException;

/**
 *
 *
 * @author brad
 * @version $Date$, $Revision$
 */
public class SURTTokenizerTest extends TestCase {

        SURTTokenizer tok;
        /**
         * Test method for 'org.archive.wayback.accesscontrol.SURTTokenizer.nextSearch()'
         */
        public void testSimple() {
                tok = toSurtT("http://www.archive.org/foo");
                assertEquals("(org,archive,www,)/foo\t",tok.nextSearch());
                assertEquals("(org,archive,www,)/foo",tok.nextSearch());
                assertEquals("(org,archive,www,",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());
        }
        /** test */
        public void testSlashPath() {
                tok = toSurtT("http://www.archive.org/");
                assertEquals("(org,archive,www,)/\t",tok.nextSearch());
                assertEquals("(org,archive,www,)/",tok.nextSearch());
                assertEquals("(org,archive,www,",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }

        /** test */
        public void testEmptyPath() {
                tok = toSurtT("http://www.archive.org");
                assertEquals("(org,archive,www,)/\t",tok.nextSearch());
                assertEquals("(org,archive,www,)/",tok.nextSearch());
                assertEquals("(org,archive,www,",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }


        /** test */
        public void testEmptyPathMore() {
                tok = toSurtT("http://brad.www.archive.org");
                assertEquals("(org,archive,www,brad,)/\t",tok.nextSearch());
                assertEquals("(org,archive,www,brad,)/",tok.nextSearch());
                assertEquals("(org,archive,www,brad,",tok.nextSearch());
                assertEquals("(org,archive,www,brad",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }       
        /** test */
        public void testLongPathMore() {
                tok = toSurtT("http://brad.www.archive.org/one/two");
                assertEquals("(org,archive,www,brad,)/one/two\t",tok.nextSearch());
                assertEquals("(org,archive,www,brad,)/one/two",tok.nextSearch());
                assertEquals("(org,archive,www,brad,)/one",tok.nextSearch());
                assertEquals("(org,archive,www,brad,",tok.nextSearch());
                assertEquals("(org,archive,www,brad",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }       
        /** test */
        public void testShortPathHash() {
                tok = toSurtT("http://www.archive.org/one/two#hash");
                assertEquals("(org,archive,www,)/one/two\t",tok.nextSearch());
                assertEquals("(org,archive,www,)/one/two",tok.nextSearch());
                assertEquals("(org,archive,www,)/one",tok.nextSearch());
                assertEquals("(org,archive,www,",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }
        /** test */
        public void testCGI1() {
                tok = toSurtT("http://www.archive.org/cgi?foobar");
                assertEquals("(org,archive,www,)/cgi?foobar\t",tok.nextSearch());
                assertEquals("(org,archive,www,)/cgi?foobar",tok.nextSearch());
                assertEquals("(org,archive,www,)/cgi",tok.nextSearch());
                assertEquals("(org,archive,www,",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }
        /** test */
        public void testPort() {
                tok = toSurtT("http://www.archive.org:8080/cgi?foobar");
                assertEquals("(org,archive,www,:8080)/cgi?foobar\t",tok.nextSearch());
                assertEquals("(org,archive,www,:8080)/cgi?foobar",tok.nextSearch());
                assertEquals("(org,archive,www,:8080)/cgi",tok.nextSearch());
                assertEquals("(org,archive,www,:8080",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());
        }
        /** test */
        public void testLogin() {
                tok = toSurtT("http://brad@www.archive.org/cgi?foobar");
                assertEquals("(org,archive,www,@brad)/cgi?foobar\t",tok.nextSearch());
                assertEquals("(org,archive,www,@brad)/cgi?foobar",tok.nextSearch());
                assertEquals("(org,archive,www,@brad)/cgi",tok.nextSearch());
                assertEquals("(org,archive,www,@brad",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }
        /** test */
        public void testLoginPass() {
                tok = toSurtT("http://brad:pass@www.archive.org/cgi?foobar");
                assertEquals("(org,archive,www,@brad:pass)/cgi?foobar\t",tok.nextSearch());
                assertEquals("(org,archive,www,@brad:pass)/cgi?foobar",tok.nextSearch());
                assertEquals("(org,archive,www,@brad:pass)/cgi",tok.nextSearch());
                assertEquals("(org,archive,www,@brad:pass",tok.nextSearch());
                assertEquals("(org,archive,www",tok.nextSearch());
                assertEquals("(org,archive",tok.nextSearch());
                assertEquals("(org",tok.nextSearch());
                assertNull(tok.nextSearch());           
        }
//      /** test */
        // leave this guy out for now: was a bug in Heritrix thus archive-commons
        // wait for new jar...
//      public void testLoginPassPort() {
//              tok = toSurtT("http://brad:pass@www.archive.org:8080/cgi?foobar");
//              assertEquals("(org,archive,www,:8080@brad:pass)/cgi?foobar\t",tok.nextSearch());
//              assertEquals("(org,archive,www,:8080@brad:pass)/cgi?foobar",tok.nextSearch());
//              assertEquals("(org,archive,www,:8080@brad:pass)/cgi",tok.nextSearch());
//              assertEquals("(org,archive,www,:8080@brad:pass",tok.nextSearch());
//              assertEquals("(org,archive,www,:8080",tok.nextSearch());
//              assertEquals("(org,archive,www",tok.nextSearch());
//              assertEquals("(org,archive",tok.nextSearch());
//              assertEquals("(org",tok.nextSearch());
//              assertNull(tok.nextSearch());
//      }
//      
        
        private SURTTokenizer toSurtT(final String u) {
                SURTTokenizer tok = null;
                try {
                        tok = new SURTTokenizer(u);
                } catch (URIException e) {
                        e.printStackTrace();
                        assertFalse("URL Exception " + e.getLocalizedMessage(),true);
                }
                return tok;
        }
        
}
