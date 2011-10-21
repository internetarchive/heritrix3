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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.regex.Matcher;

import junit.framework.TestCase;

import org.archive.net.PublicSuffixes2.Node;

/**
 * Test cases for PublicSuffixes utility. Confirm expected matches/nonmatches
 * from constructed regex.
 * 
 * @author gojomo
 */
public class PublicSuffixes2Test extends TestCase {
    // test of low level implementation
    
    public void testCompare() {
        Node n = new Node("hoge");
        assertTrue(n.compareTo('a') > 0);
        assertEquals(-1, n.compareTo('*'));
        assertEquals(-1, n.compareTo('!'));
        assertEquals(-1, n.compareTo(new Node("*,")));
        assertEquals(-1, n.compareTo(new Node("!muga,")));
        assertEquals(-1, n.compareTo(new Node("")));
        
        n = new Node("*,");
        assertEquals(1, n.compareTo('a'));
        assertEquals(0, n.compareTo('*'));
        assertEquals(1, n.compareTo('!'));
        assertEquals(0, n.compareTo(new Node("*,")));
        assertEquals(1, n.compareTo(new Node("!muga,")));
        assertEquals(-1, n.compareTo(new Node("")));
        
        n = new Node("!hoge");
        assertEquals(1, n.compareTo('a'));
        assertEquals(-1, n.compareTo('*'));
        assertEquals(0, n.compareTo('!'));
        assertEquals(-1, n.compareTo(new Node("*,")));
        assertEquals(0, n.compareTo(new Node("!muga,")));
        assertEquals(-1, n.compareTo(new Node("")));
        
        n = new Node("");
        assertEquals(1, n.compareTo('a'));
        assertEquals(1, n.compareTo('*'));
        assertEquals(1, n.compareTo('!'));
        assertEquals(0, n.compareTo(new Node("")));
    }
    
    protected String dump(Node alt) {
        StringWriter w = new StringWriter();
        PublicSuffixes2.dump(alt, 0, new PrintWriter(w));
        return w.toString();
    }
    public void testTrie1()  {
        Node alt = new Node(null, new ArrayList<Node>());
        alt.addBranch("ac,");
        // specifically, should not have empty string as match.
        assertEquals("(null)\n" +
                "  \"ac,\"\n", dump(alt));
        alt.addBranch("ac,com,");
        assertEquals("(null)\n" +
        		"  \"ac,\"\n" +
        		"    \"com,\"\n" +
        		"    \"\"\n", dump(alt));
        alt.addBranch("ac,edu,");
        assertEquals("(null)\n" +
        		"  \"ac,\"\n" +
        		"    \"com,\"\n" +
        		"    \"edu,\"\n" +
        		"    \"\"\n", dump(alt));
    }
    public void testTrie2() {
        Node alt = new Node(null, new ArrayList<Node>());
        alt.addBranch("ac,");
        alt.addBranch("*,");
        assertEquals("(null)\n" +
        		"  \"ac,\"\n" +
        		"  \"*,\"\n", dump(alt));
    }

    public void testTrie3() {
        Node alt = new Node(null, new ArrayList<Node>());
        alt.addBranch("ac,");
        alt.addBranch("ac,!hoge,");
        alt.addBranch("ac,*,");
        // exception goes first.
        assertEquals("(null)\n" +
        		"  \"ac,\"\n" +
        		"    \"!hoge,\"\n" +
        		"    \"*,\"\n" +
        		"    \"\"\n", dump(alt));
    }

    // test of higher-level functionality
    
    Matcher m = PublicSuffixes2.getTopmostAssignedSurtPrefixPattern()
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
                PublicSuffixes2.reduceSurtToAssignmentLevel("1.2.3.4"));
    }
    
    public void testIPV6() {
        assertEquals("unexpected reduction", 
                "[2001:0db8:85a3:08d3:1319:8a2e:0370:7344]",
                PublicSuffixes2.reduceSurtToAssignmentLevel(
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
        assertSame("topmostAssignedSurtPrefixPattern not cached",PublicSuffixes2.getTopmostAssignedSurtPrefixPattern(),PublicSuffixes2.getTopmostAssignedSurtPrefixPattern());
        assertSame("topmostAssignedSurtPrefixRegex not cached",PublicSuffixes2.getTopmostAssignedSurtPrefixRegex(),PublicSuffixes2.getTopmostAssignedSurtPrefixRegex()); 
    }
    
    // TODO: test UTF domains?

    protected void matchPrefix(String surtDomain, String expectedAssignedPrefix) {
        m.reset(surtDomain);
        assertTrue("expected match not found in '" + surtDomain, m.find());
        assertEquals("expected match not found", expectedAssignedPrefix, m
                .group());
    }
    
    // performance
    public void testPerformanceBuild() {
        PublicSuffixesTest v1 = new PublicSuffixesTest();
        long t1 = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            v1.testBasics();
        }
        System.out.format("Version 1: %fs\n", (System.currentTimeMillis() - t1)/100.0);

        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            testBasics();
        }
        System.out.format("Version 2: %fs\n", (System.currentTimeMillis() - t0)/100.0);
    }
}
