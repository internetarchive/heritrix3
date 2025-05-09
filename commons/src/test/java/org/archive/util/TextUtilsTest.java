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

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit test suite for TextUtils
 * 
 * @author gojomo
 * @version $ Id$
 */
public class TextUtilsTest {

    @Test
    public void testMatcherRecycling() {
        String pattern = "f.*";
        Matcher m1 = TextUtils.getMatcher(pattern,"foo");
        assertTrue(m1.matches(), "matcher against 'foo' problem");
        TextUtils.recycleMatcher(m1);
        Matcher m2 = TextUtils.getMatcher(pattern,"");
        assertFalse(m2.matches(), "matcher against '' problem");
        assertSame(m1, m2, "matcher not recycled");
        // now verify proper behavior without recycling
        Matcher m3 = TextUtils.getMatcher(pattern,"fuggedaboutit");
        assertTrue(m3.matches(), "matcher against 'fuggedaboutit' problem");
        Assertions.assertNotSame(m3, m2, "matcher was recycled");
    }

    @Test
    public void testGetFirstWord() {
        final String firstWord = "one";
        String tmpStr = TextUtils.getFirstWord(firstWord + " two three");
        assertEquals(firstWord, tmpStr, "Failed to get first word 1 " + tmpStr);
        tmpStr = TextUtils.getFirstWord(firstWord);
        assertEquals(firstWord, tmpStr, "Failed to get first word 2 " + tmpStr);
    }

    @Test
    public void testUnescapeHtml() {
        final String abc = "abc";
        CharSequence cs = TextUtils.unescapeHtml("abc");
        assertEquals(abc, cs);
        final String backwards = "aaa;lt&aaa";
        cs = TextUtils.unescapeHtml(backwards);
        assertEquals(backwards, cs);
        final String ampersand = "aaa&aaa";
        cs = TextUtils.unescapeHtml(ampersand);
        assertEquals(ampersand, cs);
        final String encodedAmpersand = "aaa&amp;aaa";
        cs = TextUtils.unescapeHtml(encodedAmpersand);
        assertEquals(ampersand, cs);
        final String encodedQuote = "aaa&#39;aaa";
        cs = TextUtils.unescapeHtml(encodedQuote);
        assertEquals("aaa'aaa", cs);
        final String entityQuote = "aaa&quot;aaa";
        cs = TextUtils.unescapeHtml(entityQuote);
        assertEquals("aaa\"aaa", cs);
        final String hexencoded = "aaa&#x000A;aaa";
        cs = TextUtils.unescapeHtml(hexencoded);
        assertEquals("aaa\naaa", cs);
        final String zeroPos = "&amp;aaa";
        cs = TextUtils.unescapeHtml(zeroPos);
        assertEquals("&aaa", cs);
    }

    @Test
    public void testUnescapeHtmlWithDanglingAmpersand() {
        final String mixedEncodedAmpersand1 = "aaa&aaa&amp;aaa";
        CharSequence cs = TextUtils.unescapeHtml(mixedEncodedAmpersand1);
        assertEquals("aaa&aaa&aaa", cs);
        final String mixedEncodedAmpersand2 = "aaa&aaa&amp;aaa&amp;aaa";
        cs = TextUtils.unescapeHtml(mixedEncodedAmpersand2);
        assertEquals("aaa&aaa&aaa&aaa", cs);
    }
}

