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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.httpclient.URIException;
import org.archive.net.UURI;
import org.archive.url.LaxURLCodec;


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

    // naive likely-uri test: 
    //    no whitespace or '<' or '>' 
    //    at least one '.' or '/';
    protected static final String NAIVE_LIKELY_URI_PATTERN = "[^<>\\s]*[\\./][^<>\\s]*";
    
    public static boolean isPossibleUri(CharSequence candidate) {
        return TextUtils.matches(NAIVE_LIKELY_URI_PATTERN, candidate);
    }
    
    /**
     * @deprecated produces too many false positives,
     *             {@link #isVeryLikelyUri(CharSequence)} is preferred
     */
    public static boolean isLikelyUri(CharSequence candidate) {
        return isPossibleUri(candidate) && !isLikelyFalsePositive(candidate);
    }

    protected final static String[] AUDIO_VIDEO_IMAGE_MIMETYPES = new String[] {
            "audio/aiff",
            "audio/asf",
            "audio/basic",
            "audio/m4a",
            "audio/mid",
            "audio/midi",
            "audio/mp3",
            "audio/mp4",
            "audio/mp4a-latm",
            "audio/mpeg",
            "audio/mpeg3",
            "audio/mpegurl",
            "audio/mpg",
            "audio/ogg",
            "audio/playlist",
            "audio/unknown",
            "audio/vnd.qcelp",
            "audio/vnd.rn-realaudio",
            "audio/wav",
            "audio/x-aiff",
            "audio/x-m4a",
            "audio/x-midi",
            "audio/x-mp3",
            "audio/x-mpeg",
            "audio/x-mpeg3",
            "audio/x-mpegurl",
            "audio/x-ms-wax",
            "audio/x-ms-wma",
            "audio/x-ms-wmv",
            "audio/x-pn-realaudio",
            "audio/x-pn-realaudio-plugin",
            "audio/x-realaudio",
            "audio/x-scpls",
            "audio/x-wav",
            "image/bitmap",
            "image/bmp",
            "image/BMP",
            "image/cur",
            "image/fits",
            "image/gif",
            "image/GIF",
            "image/ico",
            "image/icon",
            "image/jp2",
            "image/jpeg",
            "image/JPEG",
            "image/jpeg-cmyk",
            "image/jpg",
            "image/JPG",
            "image/pdf",
            "image/pict",
            "image/pjpeg",
            "image/png",
            "image/PNG",
            "image/svg+xml",
            "image/tiff",
            "image/vnd.adobe.photoshop",
            "image/vnd.djvu",
            "image/vnd.dwg",
            "image/vnd.dxf",
            "image/vnd.microsoft.icon",
            "image/vnd.ms-modi",
            "image/vnd.ms-photo",
            "image/vnd.wap.wbmp",
            "image/x-bitmap",
            "image/x-bmp",
            "image/x-citrix-pjpeg",
            "image/x-dcraw",
            "image/x-djvu",
            "image/x.djvu",
            "image/x-emf",
            "image/x-eps",
            "image/x-guffaw",
            "image/x-ico",
            "image/xicon",
            "image/x-icon",
            "image/x-jg",
            "image/x-ms-bmp",
            "image/x-MS-bmp",
            "image/x-pcx",
            "image/x-photoshop",
            "image/x-pict",
            "image/x-png",
            "image/x-portable-anymap",
            "image/x-portable-bitmap",
            "image/x-portable-graymap",
            "image/x-portable-pixmap",
            "image/x-psd",
            "image/x-quicktime",
            "image/x-rgb",
            "image/x-windows-bmp",
            "image/x-wmf",
            "image/x-xbitmap",
            "image/x-xbm",
            "image/x-xfig",
            "image/x-xpixmap",
            "video/3gpp",
            "video/asx",
            "video/avi",
            "video/f4v",
            "video/flv",
            "video/m4v",
            "video/mp4",
            "video/MP4",
            "video/mp4v-es",
            "video/mpeg",
            "video/mpeg3",
            "video/mpeg4",
            "video/mpg4",
            "video/msvideo",
            "video/ogg",
            "video/quicktime",
            "video/swf",
            "video/unknown",
            "video/vnd.objectvideo",
            "video/webm",
            "video/wmv",
            "video/x-dv",
            "video/x-flv",
            "video/x-m4v",
            "video/x-mp4",
            "video/x-mpeg",
            "video/x-ms-asf",
            "video/x-ms-asx",
            "video/x-msvideo",
            "video/x-ms-wm",
            "video/x-ms-wma",
            "video/x-ms-wmv",
            "video/x-ms-wmx",
            "video/x-ms-wvx",
            "video/x-pn-realaudio",
            "video/x-pn-realvideo",
            "video/x-sgi-movie",
            "video/x-swf"
    };
    protected static final Set<String> AUDIO_VIDEO_IMAGE_MIMETYPE_SET = new HashSet<String>();
    static {
        AUDIO_VIDEO_IMAGE_MIMETYPE_SET.addAll(Arrays.asList(AUDIO_VIDEO_IMAGE_MIMETYPES));
    }

    protected static boolean isLikelyFalsePositive(CharSequence candidate) {
        if (TextUtils.matches("(?:text|application)/[^/]+", candidate)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("rejected: looks like an application or text mimetype: " + candidate);
            }
            return true;
        }

        for (String s: AUDIO_VIDEO_IMAGE_MIMETYPES) {
            if (s.contentEquals(candidate)) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("rejected: looks like an audio video or image mimetype: " + candidate);
                }
                return true;
            }
        }
        
        if (TextUtils.matches("\\d+(?:\\.\\d+)*", candidate)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("rejected: looks like a decimal number: " + candidate);
            }
            return true;
        }

        if (TextUtils.matches(".*[$()'\"\\[\\]{}|].*", candidate)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("rejected: contains unusual characters: " + candidate);
            }
            return true;
        }
        
        // starting or ending with + particularly common because of string concatenation in javascript
        if (TextUtils.matches("^[,;+:].*|.*[.,;+:]$", candidate)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("rejected: starts or ends with an unusual starting or ending character: " + candidate);
            }
            return true;
        }
        if (candidate.charAt(0) == '.' && !TextUtils.matches("^\\.{1,2}/.*", candidate)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("rejected: starts with '.' (but not './' or '../'): " + candidate);
            }
            return true;
        }
        
        if (TextUtils.matches("^.*[^:]//.*$", candidate)) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("rejected: contains '//' (but not '://'): " + candidate);
            }
            return true;
        }
        
        // look for things that look like hostnames and not filenames?
        // look for too many dots but make sure we take into account that url may have hostname?

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("accepted: does not look like a false positive: " + candidate);
        }

        return false;
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
        if(TextUtils.matches("(?i)^https?%3A.*", retVal)) {
            try {
                retVal = LaxURLCodec.DEFAULT.decode(retVal);
            } catch (DecoderException e) {
                LOGGER.log(Level.INFO,"unable to decode",e);
            }
        }
        
        // TODO: more URI-decoding if there are %-encoded parts?
        
        // detect scheme-less intended-absolute-URI
        // intent: "opens with what looks like a dotted-domain, and 
        // last segment is a top-level-domain (eg "com", "org", etc)" 
        Matcher m = TextUtils.getMatcher("(?:[^./]+\\.)+([^./]+)(?:/.*)?", 
                retVal);
        if (m.matches()) {
            if (ArchiveUtils.isTld(m.group(1))) {
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

    protected static final Set<String> HTML_TAGS = new HashSet<String>();
    static {
        HTML_TAGS.addAll(Arrays.asList("a", "abbr", "acronym", "address",
                        "applet", "area", "article", "aside", "audio", "b",
                        "base", "basefont", "bdi", "bdo", "big", "blockquote",
                        "body", "br", "button", "canvas", "caption", "center",
                        "cite", "code", "col", "colgroup", "command",
                        "datalist", "dd", "del", "details", "dfn", "dir",
                        "div", "dl", "dt", "em", "embed", "fieldset",
                        "figcaption", "figure", "font", "footer", "form",
                        "frame", "frameset", "head", "header", "hgroup", "h1",
                        "h2", "h3", "h4", "h5", "h6", "hr", "html", "i",
                        "iframe", "img", "input", "ins", "kbd", "keygen",
                        "label", "legend", "li", "link", "map", "mark", "menu",
                        "meta", "meter", "nav", "noframes", "noscript",
                        "object", "ol", "optgroup", "option", "output", "p",
                        "param", "pre", "progress", "q", "rp", "rt", "ruby",
                        "s", "samp", "script", "section", "select", "small",
                        "source", "span", "strike", "strong", "style", "sub",
                        "summary", "sup", "table", "tbody", "td", "textarea",
                        "tfoot", "th", "thead", "time", "title", "tr", "track",
                        "tt", "u", "ul", "var", "video", "wbr"));
    }

    protected static final String QNV = "[a-zA-Z_]+=(?:[\\w-/.]|%[0-9a-fA-F]{2})+"; // name=value for query strings 
    protected static final String VERY_LIKELY_RELATIVE_URI_PATTERN = 
            "(?:\\.?/)?"                                    // may start with "/" or "./"
            + "(?:(?:[\\w-]+|\\.\\.)/)*"                    // may have path/segments/
            + "(?:[\\w-]+(?:\\.[a-zA-Z0-9]{2,5})?)?"        // may have a filename with or without an extension
            + "(?:\\?(?:"+ QNV + ")(?:&(?:" + QNV + "))*)?" // may have a ?query=string
            + "(?:#[\\w-]+)?";                              // may have a #fragment

    public static boolean isVeryLikelyUri(CharSequence candidate) {
        // must have a . or /
        if (!TextUtils.matches(NAIVE_LIKELY_URI_PATTERN, candidate)) {
            return false;
        }
        
        // absolute uri
        if (TextUtils.matches("^(?i)https?://[^<>\\s/]+\\.[^<>\\s/]+(?:/[^<>\\s]*)?", candidate)) {
            return true;
        }
        
        // "protocol-relative" uri
        if (TextUtils.matches("^//[^<>\\s/]+\\.[^<>\\s/]+(?:/[^<>\\s]*)?", candidate)) {
            return true;
        }
        
        // relative or server-relative uri
        if (!TextUtils.matches(VERY_LIKELY_RELATIVE_URI_PATTERN, candidate)) {
            return false;
        }
        
        /*
         * Remaining tests discard stuff that the
         * VERY_LIKELY_RELATIVE_URI_PATTERN can't catch
         */
        
        // text or application mimetype
        if (TextUtils.matches("(?:text|application)/[^/]+", candidate)) {
            return false;
        }

        // audio, video or image mimetype
        if (AUDIO_VIDEO_IMAGE_MIMETYPE_SET.contains(candidate)) {
            return false;
        }
        
        // decimal number
        if (TextUtils.matches("\\d+(?:\\.\\d+)*", candidate)) {
            return false;
        }
        
        // likely css class, e.g. "div.menu", "a.help", etc
        Matcher m = TextUtils.getMatcher("([^./]+)\\.([^./]+)", candidate);
        if (m.matches() && HTML_TAGS.contains(m.group(1).toLowerCase())) {
            return false;
        }
        
        return true;
    }


    
//
// legacy likely-URI test from ExtractorJS
//
    // determines whether a string is likely URI
    // (no whitespace or '<' '>',  has an internal dot or some slash,
    // begins and ends with either '/' or a word-char)
    protected static final String STRING_URI_DETECTOR =
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
    protected static final String LIKELY_URI_PATH =
     "(\\.{0,2}[^\\.\\n\\r\\s\"']*(\\.[^\\.\\n\\r\\s\"']+)+)";
	
	public static boolean isLikelyUriHtmlContextLegacy(CharSequence candidate) {
		return TextUtils.matches(LIKELY_URI_PATH, candidate);
	}
}
