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
import java.io.StringReader;
import java.util.Iterator;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author gojomo
 */
public class SurtPrefixSetTest extends TestCase {
    private static final String ARCHIVE_ORG_DOMAIN_SURT = "http://(org,archive,";
    private static final String WWW_EXAMPLE_ORG_HOST_SURT = "http://(org,example,www,)";
    private static final String HOME_EXAMPLE_ORG_PATH_SURT = "http://(org,example,home,)/pages/";
    private static final String BOK_IS_REDUNDANT_SURT = "http://(is,bok,";
    private static final String IS_DOMAIN_SURT = "http://(is,";
    private static final String WWW_BOK_IS_REDUNDANT_SURT = "http://(is,bok,www";

    private static final String TEST_SURT_LIST = 
        "# a test set of surt prefixes \n" +
        ARCHIVE_ORG_DOMAIN_SURT + "\n" +
        WWW_EXAMPLE_ORG_HOST_SURT + "\n" +
        HOME_EXAMPLE_ORG_PATH_SURT + "\n" +
        BOK_IS_REDUNDANT_SURT + " # is redundant\n" +
        IS_DOMAIN_SURT + "\n" +
        WWW_BOK_IS_REDUNDANT_SURT + " # is redundant\n";
    
    /**
     * Create a new SurtPrefixSetTest object
     * 
     * @param testName
     *            the name of the test
     */
    public SurtPrefixSetTest(final String testName) {
        super(testName);
    }

    /**
     * run all the tests for SurtPrefixSetTest
     * 
     * @param argv
     *            the command line arguments
     */
    public static void main(String argv[]) {
        junit.textui.TestRunner.run(suite());
    }

    /**
     * return the suite of tests for SurtPrefixSetTest
     * 
     * @return the suite of test
     */
    public static Test suite() {
        return new TestSuite(SurtPrefixSetTest.class);
    }
    
    
    
    public void testMisc() throws IOException {
        SurtPrefixSet surts = new SurtPrefixSet();
        StringReader sr = new StringReader(TEST_SURT_LIST);
        surts.importFrom(sr);
        
        assertContains(surts,ARCHIVE_ORG_DOMAIN_SURT);
        assertContains(surts,WWW_EXAMPLE_ORG_HOST_SURT);
        assertContains(surts,HOME_EXAMPLE_ORG_PATH_SURT);
        assertContains(surts,IS_DOMAIN_SURT);
        
        assertDoesntContain(surts,BOK_IS_REDUNDANT_SURT);
        assertDoesntContain(surts,WWW_BOK_IS_REDUNDANT_SURT);
        
        assertContainsPrefix(surts,SURT.fromURI("http://example.is/foo"));
        assertDoesntContainPrefix(surts,SURT.fromURI("http://home.example.org/foo"));
    }

    /**
     * @param surts
     * @param string
     */
    private void assertDoesntContainPrefix(SurtPrefixSet surts, String s) {
        assertEquals(s+" is prefixed", surts.containsPrefixOf(s), false);
    }

    /**
     * @param surts
     * @param string
     */
    private void assertContainsPrefix(SurtPrefixSet surts, String s) {
        assertEquals(s+" isn't prefixed", surts.containsPrefixOf(s), true);
    }

    /**
     * @param surts
     * @param www_bok_is_redundant_surt2
     */
    private void assertDoesntContain(SurtPrefixSet surts, String s) {
        assertEquals(s+" is present", surts.contains(s), false);
    }

    /**
     * @param archive_org_domain_surt2
     */
    private void assertContains(SurtPrefixSet surts, String s) {
        assertEquals(s+" is missing", surts.contains(s), true);
    }
    
    public void testImportFromUris() throws IOException {
        String seed = "http://www.archive.org/index.html";
        assertEquals("Convert failed " + seed,
                "http://(org,archive,www,)/",
                makeSurtPrefix(seed));
        seed = "http://timmknibbs4senate.blogspot.com/";
        assertEquals("Convert failed " + seed,
                "http://(com,blogspot,timmknibbs4senate,)/",
                makeSurtPrefix(seed));
        seed = "https://one.two.three";
        assertEquals("Convert failed " + seed,
                "http://(three,two,one,",
                makeSurtPrefix(seed));
        seed = "https://xone.two.three/a/b/c/";
        assertEquals("Convert failed " + seed,
                "http://(three,two,xone,)/a/b/c/",
                makeSurtPrefix(seed));
        seed = "https://yone.two.three/a/b/c";
        assertEquals("Convert failed " + seed,
                "http://(three,two,yone,)/a/b/",
                makeSurtPrefix(seed));
    }
    
    private String makeSurtPrefix(String seed) {
        SurtPrefixSet surts = new SurtPrefixSet();
        StringReader sr = new StringReader(seed);
        surts.importFromUris(sr);
        String result = null;
        for (Iterator<String> i = surts.iterator(); i.hasNext();) {
            result = (String)i.next();
        }
        return result;
    }
}
