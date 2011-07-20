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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;

public class TextUtils {
    private static final String FIRSTWORD = "^([^\\s]*).*$";

    /** thread-local cached matchers, by string key */
    private static final ThreadLocal<Map<String,Matcher>> TL_MATCHER_MAP
     = new ThreadLocal<Map<String,Matcher>>() {
        protected Map<String,Matcher> initialValue() {
            return new HashMap<String,Matcher>(50);
        }
    };
    
    /** global soft-cache of Patterns, by string key */
    private static final ConcurrentMap<String, Pattern> PATTERNS = new MapMaker()
        .concurrencyLevel(16)
        .softValues()
        .makeComputingMap(new Function<String, Pattern>() {
            public Pattern apply(String regex) {
                return Pattern.compile(regex);
            }
        });

    /**
     * Get a matcher object for a precompiled regex pattern.
     * 
     * This method tries to reuse Matcher objects for efficiency.
     * It can hold for recycling one Matcher per pattern per thread. 
     * 
     * Matchers retrieved should be returned for reuse via the
     * recycleMatcher() method, but no errors will occur if they
     * are not.
     * 
     * This method is a hotspot frequently accessed.
     *
     * @param pattern the string pattern to use
     * @param input the character sequence the matcher should be using
     * @return a matcher object loaded with the submitted character sequence
     */
    public static Matcher getMatcher(String pattern, CharSequence input) {
        if (pattern == null) {
            throw new IllegalArgumentException("String 'pattern' must not be null");
        }
        input = new InterruptibleCharSequence(input);
        final Map<String,Matcher> matchers = TL_MATCHER_MAP.get();
        Matcher m = (Matcher)matchers.get(pattern);
        if(m == null) {
            m = PATTERNS.get(pattern).matcher(input);
        } else {
            matchers.put(pattern,null);
            m.reset(input);
        }
        return m;
    }

    public static void recycleMatcher(Matcher m) {
        // while cached, eliminate reference to potentially-large prior 'input'
        m.reset(""); 
        final Map<String,Matcher> matchers = TL_MATCHER_MAP.get();
        matchers.put(m.pattern().pattern(),m);
    }
    
    /**
     * Utility method using a precompiled pattern instead of using the
     * replaceAll method of the String class. This method will also be reusing
     * Matcher objects.
     * 
     * @see java.util.regex.Pattern
     * @param pattern precompiled Pattern to match against
     * @param input the character sequence to check
     * @param replacement the String to substitute every match with
     * @return the String with all the matches substituted
     */
    public static String replaceAll(
            String pattern, CharSequence input, String replacement) {
        input = new InterruptibleCharSequence(input);
        Matcher m = getMatcher(pattern, input);
        String res = m.replaceAll(replacement);
        recycleMatcher(m);
        return res;
    }

    /**
     * Utility method using a precompiled pattern instead of using the
     * replaceFirst method of the String class. This method will also be reusing
     * Matcher objects.
     * 
     * @see java.util.regex.Pattern
     * @param pattern precompiled Pattern to match against
     * @param input the character sequence to check
     * @param replacement the String to substitute the first match with
     * @return the String with the first match substituted
     */
    public static String replaceFirst(
            String pattern, CharSequence input, String replacement) {
        input = new InterruptibleCharSequence(input);
        Matcher m = getMatcher(pattern, input);
        String res = m.replaceFirst(replacement);
        recycleMatcher(m);
        return res;
    }

    /**
     * Utility method using a precompiled pattern instead of using the matches
     * method of the String class. This method will also be reusing Matcher
     * objects.
     * 
     * @see java.util.regex.Pattern
     * @param pattern precompiled Pattern to match against
     * @param input the character sequence to check
     * @return true if character sequence matches
     */
    public static boolean matches(String pattern, CharSequence input) {
        input = new InterruptibleCharSequence(input);
        Matcher m = getMatcher(pattern, input);
        boolean res = m.matches();
        recycleMatcher(m);
        return res;
    }

    /**
     * Utility method using a precompiled pattern instead of using the split
     * method of the String class.
     * 
     * @see java.util.regex.Pattern
     * @param pattern precompiled Pattern to split by
     * @param input the character sequence to split
     * @return array of Strings split by pattern
     */
    public static String[] split(String pattern, CharSequence input) {
        input = new InterruptibleCharSequence(input);
        Matcher m = getMatcher(pattern,input);
        String[] retVal = m.pattern().split(input); 
        recycleMatcher(m);
        return retVal;
    }
    
    /**
     * @param s String to find first word in (Words are delimited by
     * whitespace).
     * @return First word in the passed string else null if no word found.
     */
    public static String getFirstWord(String s) {
        Matcher m = getMatcher(FIRSTWORD, s);
        String retVal = (m != null && m.matches())? m.group(1): null;
        recycleMatcher(m);
        return retVal;
    }

    /**
     * Escapes a string so that it can be passed as an argument to a javscript
     * in a JSP page. This method takes a string and returns the same string
     * with any single quote escaped by prepending the character with a
     * backslash. Linebreaks are also replaced with '\n'.  Also,
     * less-than signs and ampersands are replaced with HTML entities.
     * 
     * @param s The string to escape
     * @return The same string escaped.
     */
    public static String escapeForHTMLJavascript(String s) {
        return escapeForHTML(StringEscapeUtils.escapeJavaScript(s));
    }
    
    /**
     * Escapes a string so that it can be placed inside XML/HTML attribute.
     * Replaces ampersand, less-than, greater-than, single-quote, and 
     * double-quote with escaped versions.
     * @param s The string to escape
     * @return The same string escaped.
     */
    public static String escapeForMarkupAttribute(String s) {
        return StringEscapeUtils.escapeXml(s);
    }
    
    /**
     * Minimally escapes a string so that it can be placed inside XML/HTML
     * attribute.
     * Escapes lt and amp.
     * @param s The string to escape
     * @return The same string escaped.
     */
    public static String escapeForHTML(String s) {
        // TODO: do this in a single pass instead of creating 5 junk strings
        String escaped = s.replaceAll("&","&amp;");
        return escaped.replaceAll("<","&lt;");
    }

    /**
     * Utility method for writing a (potentially large) String to a JspWriter,
     * escaping it for HTML display, without constructing another large String
     * of the whole content. 
     * @param s String to write
     * @param out destination JspWriter
     * @throws IOException
     */
    public static void writeEscapedForHTML(String s, Writer w)
    throws IOException {
        PrintWriter out = new PrintWriter(w);
        BufferedReader reader = new BufferedReader(new StringReader(s));
        String line;
        while((line=reader.readLine()) != null){
            out.println(StringEscapeUtils.escapeHtml(line));
        }
    }
    
    /**
     * Replaces HTML Entity Encodings.
     * @param cs The CharSequence to remove html codes from
     * @return the same CharSequence or an escaped String.
     */
    public static CharSequence unescapeHtml(final CharSequence cs) {
        if (cs == null) {
            return cs;
        }
        
        return StringEscapeUtils.unescapeHtml(cs.toString());
    }
    
    /**
     * @param message Message to put at top of the string returned. May be
     * null.
     * @param e Exception to write into a string.
     * @return Return formatted string made of passed message and stack trace
     * of passed exception.
     */
    public static String exceptionToString(String  message, Throwable e) {
        StringWriter sw = new StringWriter();
        if (message == null || message.length() == 0) {
            sw.write(message);
            sw.write("\n");
        }
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
    
    /**
     * Exception- and warning-free URL-escaping utility method.
     * 
     * @param s String to escape
     * @return URL-escaped string
     */
    @SuppressWarnings("deprecation")
    public static String urlEscape(String s) {
        try {
            return URLEncoder.encode(s,"UTF8");
        } catch (UnsupportedEncodingException e) {
            // should be impossible; all JVMs must support UTF8
            // but have a fallback just in case
            return URLEncoder.encode(s); 
        }
    }

    /**
     * Exception- and warning-free URL-unescaping utility method.
     * 
     * @param s String do unescape
     * @return URL-unescaped String
     */
    @SuppressWarnings("deprecation")
    public static String urlUnescape(String s) {
        try {
            return URLDecoder.decode(s, "UTF8");
        } catch (UnsupportedEncodingException e) {
            // should be impossible; all JVMs must support UTF8
            // but have a fallback just in case
            return URLDecoder.decode(s);
        }
    }
}