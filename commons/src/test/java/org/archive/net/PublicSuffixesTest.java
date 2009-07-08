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

package org.archive.net;

import java.util.regex.Matcher;

import junit.framework.TestCase;

/**
 * Test cases for PublicSuffixes utility. Confirm expected matches/nonmatches
 * from constructed regex.
 * 
 * @author gojomo
 */
public class PublicSuffixesTest extends TestCase {
    Matcher m = PublicSuffixes.getTopmostAssignedSurtPrefixPattern()
            .matcher("");

    public void testBasics() {
        matchPrefix("com,example,www,", "com,example,");
        matchPrefix("com,example,", "com,example,");
        matchPrefix("org,archive,www,", "org,archive,");
        matchPrefix("org,archive,", "org,archive,");
        matchPrefix("fr,yahoo,www,", "fr,yahoo,");
        matchPrefix("fr,yahoo,", "fr,yahoo,");
        matchPrefix("au,com,foobar,www,", "au,com,foobar,");
        matchPrefix("au,com,foobar,", "au,com,foobar,");
        matchPrefix("uk,co,virgin,www,", "uk,co,virgin,");
        matchPrefix("uk,co,virgin,", "uk,co,virgin,");
        matchPrefix("au,com,example,www,", "au,com,example,");
        matchPrefix("au,com,example,", "au,com,example,");
        matchPrefix("jp,tokyo,public,assigned,www,",
                "jp,tokyo,public,assigned,");
        matchPrefix("jp,tokyo,public,assigned,", "jp,tokyo,public,assigned,");
    }

    public void testDomainWithDash() {
        matchPrefix("de,bad-site,www", "de,bad-site,");
    }
    
    public void testDomainWithNumbers() {
        matchPrefix("de,archive4u,www", "de,archive4u,");
    }
    
    public void testIPV4() {
        assertEquals("unexpected reduction", 
                "1.2.3.4",
                PublicSuffixes.reduceSurtToAssignmentLevel("1.2.3.4"));
    }
    
    public void testIPV6() {
        assertEquals("unexpected reduction", 
                "[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]",
                PublicSuffixes.reduceSurtToAssignmentLevel(
                        "[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]"));
    }
    
    public void testExceptions() {
        matchPrefix("uk,bl,www,", "uk,bl,");
        matchPrefix("uk,bl,", "uk,bl,");
        matchPrefix("jp,tokyo,metro,subdomain,", "jp,tokyo,metro,");
        matchPrefix("jp,tokyo,metro,", "jp,tokyo,metro,");
    }

    public void testFakeTLD() {
        // we assume any new/unknonwn TLD should be assumed as 2-level;
        // this is preferable for our grouping purpose but might not be
        // for a cookie-assigning browser (original purpose of publicsuffixlist)
        matchPrefix("zzz,example,www,", "zzz,example,");
    }

    public void testUnsegmentedHostname() {
        m.reset("example");
        assertFalse("unexpected match found in 'example'", m.find());
    }

    public void testTopmostAssignedCaching() {
        assertSame("topmostAssignedSurtPrefixPattern not cached",PublicSuffixes.getTopmostAssignedSurtPrefixPattern(),PublicSuffixes.getTopmostAssignedSurtPrefixPattern());
        assertSame("topmostAssignedSurtPrefixRegex not cached",PublicSuffixes.getTopmostAssignedSurtPrefixRegex(),PublicSuffixes.getTopmostAssignedSurtPrefixRegex()); 
    }
    
    // TODO: test UTF domains?

    protected void matchPrefix(String surtDomain, String expectedAssignedPrefix) {
        m.reset(surtDomain);
        assertTrue("expected match not found in '" + surtDomain, m.find());
        assertEquals("expected match not found", expectedAssignedPrefix, m
                .group());
    }
}
