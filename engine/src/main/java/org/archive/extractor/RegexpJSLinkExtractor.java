/* Copyright (C) 2003 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Created on Nov 17, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
public class RegexpJSLinkExtractor extends CharSequenceLinkExtractor {
//    private static Logger logger =
//        Logger.getLogger(RegexpJSLinkExtractor.class.getName());

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
        return new RegexpJSLinkExtractor();
    }
}
