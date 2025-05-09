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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;

/**
 * @author gojomo
 */
public class SurtPrefixSetTest {
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

    @Test
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

    private void assertDoesntContainPrefix(SurtPrefixSet surts, String s) {
        Assertions.assertFalse(surts.containsPrefixOf(s), s + " is prefixed");
    }

    private void assertContainsPrefix(SurtPrefixSet surts, String s) {
        Assertions.assertTrue(surts.containsPrefixOf(s), s + " isn't prefixed");
    }

    private void assertDoesntContain(SurtPrefixSet surts, String s) {
        Assertions.assertFalse(surts.contains(s), s + " is present");
    }

    private void assertContains(SurtPrefixSet surts, String s) {
        Assertions.assertTrue(surts.contains(s), s + " is missing");
    }

    @Test
    public void testImportFromUris() throws IOException {
        String seed = "http://www.archive.org/index.html";
        Assertions.assertEquals("http://(org,archive,www,)/", makeSurtPrefix(seed), "Convert failed " + seed);
        seed = "http://timmknibbs4senate.blogspot.com/";
        Assertions.assertEquals("http://(com,blogspot,timmknibbs4senate,)/", makeSurtPrefix(seed), "Convert failed " + seed);
        seed = "https://one.two.three";
        Assertions.assertEquals("http://(three,two,one,", makeSurtPrefix(seed), "Convert failed " + seed);
        seed = "https://xone.two.three/a/b/c/";
        Assertions.assertEquals("http://(three,two,xone,)/a/b/c/", makeSurtPrefix(seed), "Convert failed " + seed);
        seed = "https://yone.two.three/a/b/c";
        Assertions.assertEquals("http://(three,two,yone,)/a/b/", makeSurtPrefix(seed), "Convert failed " + seed);
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
