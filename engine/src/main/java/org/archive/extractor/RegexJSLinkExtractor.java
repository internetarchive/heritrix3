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
package org.archive.extractor;

import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TextUtils;

/**
 * Uses regular expressions to find likely URIs inside Javascript.
 *
 * ROUGH DRAFT IN PROGRESS / incomplete... untested...
 * 
 * @author gojomo
 */
public class RegexJSLinkExtractor extends CharSequenceLinkExtractor {

    static final String AMP = "&";
    static final String ESCAPED_AMP = "&amp;";
    static final String WHITESPACE = "\\s";

    // finds whitespace-free strings in Javascript
    // (areas between paired ' or " characters, possibly backslash-quoted
    // on the ends, but not in the middle)
    static final Pattern JAVASCRIPT_STRING_EXTRACTOR = Pattern.compile(
        "(\\\\{0,8}+(?:\"|\'))(.+?)(?:\\1)");

    // determines whether a string is likely URI
    // (no whitespace or '<' '>',  has an internal dot or some slash,
    // begins and ends with either '/' or a word-char)
    static final Pattern STRING_URI_DETECTOR = Pattern.compile(
        "(?:\\w|[\\.]{0,2}/)[\\S&&[^<>]]*(?:\\.|/)[\\S&&[^<>]]*(?:\\w|/)");

    Matcher strings;
    LinkedList<Matcher> matcherStack = new LinkedList<Matcher>();

    protected boolean findNextLink() {
        if(strings==null) {
             strings = JAVASCRIPT_STRING_EXTRACTOR.matcher(sourceContent);
        }
        while(strings!=null) {
            while(strings.find()) {
                CharSequence subsequence =
                    sourceContent.subSequence(strings.start(2), strings.end(2));
                Matcher uri = STRING_URI_DETECTOR.matcher(subsequence);
                if ((subsequence.length() <= UURI.MAX_URL_LENGTH) && uri.matches()) {
                    String string = uri.group();
                    string = TextUtils.replaceAll(ESCAPED_AMP, string, AMP);
                    try {
                        Link link = new Link(source, UURIFactory.getInstance(
                                source, string), LinkContext.JS_MISC, Hop.SPECULATIVE);
                        next.add(link);
                        return true;
                    } catch (URIException e) {
                        extractErrorListener.noteExtractError(e,source,string);
                    }
                } else {
                   //  push current range
                   matcherStack.addFirst(strings);
                   // start looking inside string
                   strings = JAVASCRIPT_STRING_EXTRACTOR.matcher(subsequence);
                }
            }
            // continue at enclosing range, if available
            strings = (Matcher) (matcherStack.isEmpty() ? null : matcherStack.removeFirst());
        }
        return false;
    }


    /* (non-Javadoc)
     * @see org.archive.extractor.LinkExtractor#reset()
     */
    public void reset() {
        super.reset();
        matcherStack.clear();
        strings = null;
    }

    protected static CharSequenceLinkExtractor newDefaultInstance() {
        return new RegexJSLinkExtractor();
    }
}
