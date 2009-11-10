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

import java.util.regex.Matcher;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * JUnit test suite for TextUtils
 * 
 * @author gojomo
 * @version $ Id$
 */
public class TextUtilsTest extends TestCase {
    /**
     * Create a new TextUtilsTest object
     * 
     * @param testName
     *            the name of the test
     */
    public TextUtilsTest(final String testName) {
        super(testName);
    }

    /**
     * run all the tests for TextUtilsTest
     * 
     * @param argv
     *            the command line arguments
     */
    public static void main(String argv[]) {
        junit.textui.TestRunner.run(suite());
    }

    /**
     * return the suite of tests for MemQueueTest
     * 
     * @return the suite of test
     */
    public static Test suite() {
        return new TestSuite(TextUtilsTest.class);
    }

    public void testMatcherRecycling() {
        String pattern = "f.*";
        Matcher m1 = TextUtils.getMatcher(pattern,"foo");
        assertTrue("matcher against 'foo' problem", m1.matches());
        TextUtils.recycleMatcher(m1);
        Matcher m2 = TextUtils.getMatcher(pattern,"");
        assertFalse("matcher against '' problem", m2.matches());
        assertTrue("matcher not recycled",m1==m2);
        // now verify proper behavior without recycling
        Matcher m3 = TextUtils.getMatcher(pattern,"fuggedaboutit");
        assertTrue("matcher against 'fuggedaboutit' problem",m3.matches());
        assertFalse("matcher was recycled",m3==m2);
    }
    
    public void testGetFirstWord() {
        final String firstWord = "one";
        String tmpStr = TextUtils.getFirstWord(firstWord + " two three");
        assertTrue("Failed to get first word 1 " + tmpStr,
            tmpStr.equals(firstWord));
        tmpStr = TextUtils.getFirstWord(firstWord);
        assertTrue("Failed to get first word 2 " + tmpStr,
            tmpStr.equals(firstWord));       
    }
    
    public void testUnescapeHtml() {
        final String abc = "abc";
        CharSequence cs = TextUtils.unescapeHtml("abc");
        assertEquals(cs, abc);
        final String backwards = "aaa;lt&aaa";
        cs = TextUtils.unescapeHtml(backwards);
        assertEquals(cs, backwards);
        final String ampersand = "aaa&aaa";
        cs = TextUtils.unescapeHtml(ampersand);
        assertEquals(cs, ampersand);
        final String encodedAmpersand = "aaa&amp;aaa";
        cs = TextUtils.unescapeHtml(encodedAmpersand);
        assertEquals(cs, ampersand);
        final String encodedQuote = "aaa&#39;aaa";
        cs = TextUtils.unescapeHtml(encodedQuote);
        assertEquals(cs, "aaa'aaa");
        final String entityQuote = "aaa&quot;aaa";
        cs = TextUtils.unescapeHtml(entityQuote);
        assertEquals(cs, "aaa\"aaa");
        final String hexencoded = "aaa&#x000A;aaa";
        cs = TextUtils.unescapeHtml(hexencoded);
        assertEquals(cs, "aaa\naaa");
        final String zeroPos = "&amp;aaa";
        cs = TextUtils.unescapeHtml(zeroPos);
        assertEquals(cs, "&aaa");
    }
    
    public void testUnescapeHtmlWithDanglingAmpersand() {
        final String mixedEncodedAmpersand1 = "aaa&aaa&amp;aaa";
        CharSequence cs = TextUtils.unescapeHtml(mixedEncodedAmpersand1);
        assertEquals("aaa&aaa&aaa",cs);
        final String mixedEncodedAmpersand2 = "aaa&aaa&amp;aaa&amp;aaa";
        cs = TextUtils.unescapeHtml(mixedEncodedAmpersand2);
        assertEquals("aaa&aaa&aaa&aaa",cs);
    } 
}

