/* UriUtils
 * 
 * $Id: MimetypeUtils.java 3119 2005-02-17 20:39:21Z stack-sf $
 * 
 * Created on April 15, 2010
 *
 * Copyright (C) 2010 Internet Archive.
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
 */
package org.archive.util;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.httpclient.URIException;
import org.archive.net.LaxURLCodec;
import org.archive.net.UURI;


/**
 * URI-related utilities. 
 * 
 * Primarily, a place to centralize and better document and test certain URI-related heuristics
 * that may be useful in many places. 
 * 
 * The choice of when to consider a string likely enough to be a URI that we try crawling it 
 * is, so far, based on rather arbitrary rules-of-thumb. We have not quantitatively tested 
 * how often the strings that pass these tests yield meaningful (not 404, non-soft-404, 
 * non-garbage) replies. We are willing to accept some level of mistaken requests, knowing
 * that their cost is usually negligible, if that allows us to discover meaningful content
 * that could be not be discovered via other heuristics. 
 * 
 *  Our intuitive understanding so far is that: strings that appear to have ./.. relative-path
 *  prefixes, dot-extensions,  or path-slashes are good candidates for trying as URIs, even 
 *  though with some Javascript/HTML-VALUE-attributes, this yields a lot of false positives. 
 *  
 *  We want to get strings like....
 *  
 *    photo.jpg
 *    /photos
 *    /photos/
 *    ./photos
 *    ../../photos
 *    photos/index.html
 *  
 *  ...but we will thus also sometimes try strings that were other kinds of variables/
 *  parameters, like...
 *  
 *    rectangle.x
 *    11.2px
 *    text/xml
 *    width:6.33
 * 
 *  Until better rules, exception-blacklists or even site-sensitive dynamic adjustment of 
 *  heuristics (eg: this site, guesses are yield 200s, keep guessing; this site, guesses are
 *  all 404s, stop guessing) are developed, crawl operators should monitor their crawls 
 *  (and contact email) for cases where speculative crawling are generating many errors, and
 *  use settings like ExtractorHTML's 'extract-javascript' and 'extract-value-attributes' or
 *  disable of ExtractorJS entirely when they want to curtail those errors. 
 *  
 *  The 'legacy' tests are those used in H1 at least through 1.14.4. They have
 *  some known problems, but are not yet being dropped until more experience 
 *  with the 'new' isLikelyUri() test is collected (in H3). Enable the 'xest'
 *  methods of the UriUtilsTest class for details. 
 *  
 * @contributor gojomo
 */
public class UriUtils {
    private static final Logger LOGGER = Logger.getLogger(UriUtils.class.getName());

//
// new combined test
//
    // naive likely-uri test: 
    //    no whitespace or '<' or '>'; 
    //    at least one '.' or '/';
    //    not ending with '.'
    static final String NAIVE_LIKELY_URI_PATTERN = "[^<>\\s]*[\\./][^<>\\s]*(?<!\\.)";
    
    // blacklist of strings that NAIVE_LIKELY_URI_PATTERN picks up as URIs,
    // which are known to be problematic, and NOT to be tried as URIs
    protected final static String[] NAIVE_URI_EXCEPTIONS = {
        "text/javascript"
        };
    
    public static boolean isLikelyUri(CharSequence candidate) {
        // naive test
        if(!TextUtils.matches(NAIVE_LIKELY_URI_PATTERN, candidate)) {
            return false; 
        }
        // eliminate common false-positives: by blacklist
        for (String s : NAIVE_URI_EXCEPTIONS) {
            if (s.contentEquals(candidate)) 
                return false;
        }
        // ...and simple numbers
        if(TextUtils.matches("\\d+\\.\\d+", candidate)) {
            return false; 
        }
        return true; 
    }
    
    
    /**
     * Perform additional fixup of likely-URI Strings
     * 
     * @param string detected candidate String
     * @return String changed/decoded to increase likelihood it is a 
     * meaningful non-404 URI
     */
    public static String speculativeFixup(String candidate, UURI base) {
        String retVal = candidate;
        
        // unescape ampersands
        retVal = TextUtils.replaceAll("&amp;", retVal, "&");
        
        // uri-decode if begins with encoded 'http(s)?%3A'
        Matcher m = TextUtils.getMatcher("(?i)^https?%3A.*",retVal); 
        if(m.matches()) {
            try {
                retVal = LaxURLCodec.DEFAULT.decode(retVal);
            } catch (DecoderException e) {
                LOGGER.log(Level.INFO,"unable to decode",e);
            }
        }
        TextUtils.recycleMatcher(m);
        
        // TODO: more URI-decoding if there are %-encoded parts?
        
        // detect scheme-less intended-absolute-URI
        // intent: "opens with what looks like a dotted-domain, and 
        // last segment is a top-level-domain (eg "com", "org", etc)" 
        m = TextUtils.getMatcher(
                "^[^\\./:\\s%]+\\.[^/:\\s%]+\\.([^\\./:\\s%]+)(/.*|)$", 
                retVal);
        if(m.matches()) {
            if(ArchiveUtils.isTld(m.group(1))) { 
                String schemePlus = "http://";       
                // if on exact same host preserve scheme (eg https)
                try {
                    if (retVal.startsWith(base.getHost())) {
                        schemePlus = base.getScheme() + "://";
                    }
                } catch (URIException e) {
                    // error retrieving source host - ignore it
                }
                retVal = schemePlus + retVal; 
            }
        }
        TextUtils.recycleMatcher(m);
        
        return retVal; 
    }
    
    
//
// legacy likely-URI test from ExtractorJS
//
    // determines whether a string is likely URI
    // (no whitespace or '<' '>',  has an internal dot or some slash,
    // begins and ends with either '/' or a word-char)
    static final String STRING_URI_DETECTOR =
        "(?:\\w|[\\.]{0,2}/)[\\S&&[^<>]]*(?:\\.|/)[\\S&&[^<>]]*(?:\\w|/)";

 
    // blacklist of strings that STRING_URI_DETECTOR picks up as URIs,
    // which are known to be problematic, and NOT to be 
    // added to outLinks
    protected final static String[] STRING_URI_DETECTOR_EXCEPTIONS = {
        "text/javascript"
        };
    
    public static boolean isLikelyUriJavascriptContextLegacy(CharSequence candidate) {
    	if(!TextUtils.matches(STRING_URI_DETECTOR,candidate)) {
    		return false; 
    	}
    	for (String s : STRING_URI_DETECTOR_EXCEPTIONS) {
            if (s.contentEquals(candidate)) 
                return false;
        }
    	// matches detector and not an exception: so a likely URI
    	return true; 
    	
    }
    
//
// legacy likely-URI test from ExtractorHTML
// 
	
    // much like the javascript likely-URI extractor, but
    // without requiring quotes -- this can indicate whether
    // an HTML tag attribute that isn't definitionally a
    // URI might be one anyway, as in form-tag VALUE attributes
    static final String LIKELY_URI_PATH =
     "(\\.{0,2}[^\\.\\n\\r\\s\"']*(\\.[^\\.\\n\\r\\s\"']+)+)";
	
	public static boolean isLikelyUriHtmlContextLegacy(CharSequence candidate) {
		return TextUtils.matches(LIKELY_URI_PATH, candidate);
	}
}
