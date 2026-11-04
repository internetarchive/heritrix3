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
package org.archive.modules.extractor;

import static org.archive.modules.extractor.Hop.SPECULATIVE;
import static org.archive.modules.extractor.LinkContext.JS_MISC;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.archive.url.URIException;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.text.translate.AggregateTranslator;
import org.apache.commons.lang3.text.translate.CharSequenceTranslator;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;
import org.archive.util.UriUtils;

/**
 * Processes Javascript files for strings that are likely to be
 * crawlable URIs.
 *
 * NOTE: This processor may open a ReplayCharSequence from the 
 * CrawlURI's Recorder, without closing that ReplayCharSequence, to allow
 * reuse by later processors in sequence. In the usual (Heritrix) case, a 
 * call after all processing to the Recorder's endReplays() method ensures
 * timely close of any reused ReplayCharSequences. Reuse of this processor
 * elsewhere should ensure a similar cleanup call to Recorder.endReplays()
 * occurs. 
 * 
 * TODO: Replace with a system for actually executing Javascript in a 
 * browser-workalike DOM, such as via HtmlUnit or remote-controlled 
 * browser engines. 
 * 
 * @author gojomo
 * @author nlevitt
 */
public class ExtractorJS extends ContentExtractor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    private static Logger LOGGER = 
            Logger.getLogger(ExtractorJS.class.getName());

    private static final Pattern CODE_POINT_ESCAPE = Pattern.compile("\\\\u\\{([0-9a-fA-F]+)\\}");

    // Translate in one pass so an escaped backslash cannot introduce a second escape.
    static final CharSequenceTranslator UNESCAPE_JAVASCRIPT = new AggregateTranslator(
            new CharSequenceTranslator() {
                @Override
                public int translate(CharSequence input, int index, Writer out) throws IOException {
                    if (input.charAt(index) != '\\') {
                        return 0;
                    }
                    Matcher matcher = CODE_POINT_ESCAPE.matcher(input).region(index, input.length());
                    if (!matcher.lookingAt()) {
                        return 0;
                    }
                    int codePoint = Integer.parseInt(matcher.group(1), 16);
                    if (!Character.isValidCodePoint(codePoint)) {
                        throw new IllegalArgumentException("Invalid JavaScript Unicode code point: "
                                + matcher.group(1));
                    }
                    out.write(Character.toChars(codePoint));
                    return matcher.end() - index;
                }
            }, StringEscapeUtils.UNESCAPE_ECMASCRIPT);

    // finds strings in Javascript
    // (areas between paired ' or " characters, possibly backslash-quoted
    // on the ends, but not in the middle)
    protected static final String JAVASCRIPT_STRING_EXTRACTOR =
    		"(\\\\{0,8}+(?:['\"]|u002[27]))([^'\"]{0,"+UURI.MAX_URL_LENGTH+"})(?:\\1)";

    private static final String JS_COMMENT = "/\\*[^*]*+(?:\\*(?!/)[^*]*+)*+\\*/|//[^\\r\\n]*";
    private static final String JS_SPACE = "(?:\\s++|" + JS_COMMENT + ")*+";
    private static final Pattern JS_TRIVIA = Pattern.compile(JS_SPACE);
    private static final String JS_STRING =
            "\"[^\"\\\\\\r\\n]*+(?:\\\\.[^\"\\\\\\r\\n]*+)*+\""
            + "|'[^'\\\\\\r\\n]*+(?:\\\\.[^'\\\\\\r\\n]*+)*+'";
    private static final String MODULE_BINDINGS =
            "(?:\\{[^}]*\\}|\\*" + JS_SPACE + "(?:as\\b" + JS_SPACE + "[\\w$]+)?"
            + "|[\\w$]+(?:" + JS_SPACE + "," + JS_SPACE
            + "(?:\\{[^}]*\\}|\\*" + JS_SPACE + "as\\b" + JS_SPACE + "[\\w$]+))?)";

    // Identify literal module references before the speculative string scan. Skip ordinary
    // strings and comments so text containing JavaScript is not mistaken for an import.
    private static final Pattern MODULE_REFERENCES = Pattern.compile(
            JS_COMMENT + "|(?<![\\w$.])(?:"
            + "(?:import\\b" + JS_SPACE + "(?:(?<dynamic>\\()" + JS_SPACE + "|"
            + MODULE_BINDINGS + JS_SPACE + "from\\b" + JS_SPACE + ")?"
            + "|export\\b" + JS_SPACE + MODULE_BINDINGS + JS_SPACE + "from\\b" + JS_SPACE + ")"
            + "(?<specifier>" + JS_STRING + ")"
            + "|new\\b" + JS_SPACE + "URL\\b" + JS_SPACE + "\\(" + JS_SPACE
            + "(?<asset>" + JS_STRING + ")" + JS_SPACE + "," + JS_SPACE
            + "import\\s*\\.\\s*meta\\s*\\.\\s*url\\b" + JS_SPACE + "\\))"
            + "|" + JS_STRING + "|`[^`\\\\]*+(?:\\\\.[^`\\\\]*+)*+`",
            Pattern.DOTALL);
    
    // GROUPS:
    // (G1) ' or " with optional leading backslashes
    // (G2) whitespace-free string delimited on boths ends by G1

    protected long numberOfCURIsHandled = 0;

    protected boolean shouldExtract(CrawlURI uri) {
        String contentType = uri.getContentType();
        if (contentType == null) {
            return false;
        }

        // If the content-type indicates js, we should process it.
        if (contentType.indexOf("javascript") >= 0) {
            return true;
        }
        if (contentType.indexOf("jscript") >= 0) {
            return true;
        }
        if (contentType.indexOf("ecmascript") >= 0) {
            return true;
        }

        if (contentType.startsWith("application/json")) {
            return true;
        }
        
        // If the filename indicates js, we should process it.
        if (uri.toString().toLowerCase().endsWith(".js")) {
            return true;
        }
        
        // If the viaContext indicates a script, we should process it.
        LinkContext context = uri.getViaContext();
        if (context == null) {
            return false;
        }
        String s = context.toString().toLowerCase();
        return s.startsWith("script");
    }
    

    @Override
    protected boolean innerExtract(CrawlURI curi) {
        this.numberOfCURIsHandled++;
        ReplayCharSequence cs = null;
        try {
            cs = curi.getRecorder().getContentReplayCharSequence();
            try {
                numberOfLinksExtracted.addAndGet(considerStrings(curi, cs));
            } catch (StackOverflowError e) {
                DevUtils.warnHandle(e, "ExtractorJS StackOverflowError");
            }
            // Set flag to indicate that link extraction is completed.
            return true;
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
        }
        return false;
    }

    protected long considerStrings(CrawlURI curi, CharSequence cs) {
        return considerStrings(this, curi, cs, true);
    }
    
    public long considerStrings(Extractor ext, 
            CrawlURI curi, CharSequence cs) {
        return considerStrings(ext, curi, cs, false);
    }
    public long considerStrings(Extractor ext,
            CrawlURI curi, CharSequence cs, String attributeContext) {
        return considerStrings(ext, curi, cs, false);
    }
    
    public long considerStrings(Extractor ext, 
            CrawlURI curi, CharSequence cs, boolean handlingJSFile) {
        long foundLinks = 0;
        Set<Integer> moduleReferences = new HashSet<>();
        if (handlingJSFile) {
            Matcher modules = MODULE_REFERENCES.matcher(cs);
            while (modules.find()) {
                if (modules.start("specifier") >= 0) {
                    if (modules.start("dynamic") >= 0) {
                        Matcher tail = JS_TRIVIA.matcher(cs).region(modules.end(), cs.length());
                        tail.lookingAt();
                        int next = tail.end();
                        if (next == cs.length() || (cs.charAt(next) != ')' && cs.charAt(next) != ',')) {
                            continue;
                        }
                    }
                    moduleReferences.add(modules.start("specifier"));
                } else if (modules.start("asset") >= 0) {
                    moduleReferences.add(modules.start("asset"));
                }
            }
        }
        
        Matcher strings =
            TextUtils.getMatcher(JAVASCRIPT_STRING_EXTRACTOR, cs);
        
        int startIndex = 0;
        while (strings.find(startIndex)) {
            CharSequence subsequence =
            		cs.subSequence(strings.start(2), strings.end(2));
            
            if (UriUtils.isPossibleUri(subsequence)) {
                // Module specifiers use the script URL, while other speculative links
                // in external scripts retain the historical resolution against via.
                boolean relativeToVia = handlingJSFile && !moduleReferences.contains(strings.start(1));
                if (considerString(ext, curi, relativeToVia, subsequence.toString())) {
                    foundLinks++;
                }
            }

            startIndex = strings.end(1);
        }
        TextUtils.recycleMatcher(strings);
        return foundLinks;
    }


    protected boolean considerString(Extractor ext, CrawlURI curi,
            boolean handlingJSFile, String candidate) {
        try {
            candidate = UNESCAPE_JAVASCRIPT.translate(candidate);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "problem unescaping some javascript", e);
        }
        candidate = UriUtils.speculativeFixup(candidate, curi.getUURI());

        if (shouldAddUri(curi, candidate)) {
            try {
                int max = ext.getExtractorParameters().getMaxOutlinks();
                if (handlingJSFile) {
                    addRelativeToVia(curi, max, candidate, JS_MISC, 
                            SPECULATIVE);
                    return true;
                } else {
                    addRelativeToBase(curi, max, candidate, JS_MISC, 
                            SPECULATIVE);
                    return true;
                }
            } catch (URIException e) {
                ext.logUriError(e, curi.getUURI(), candidate);
            }
        }
        
        return false;
    }
    
    protected boolean shouldAddUri(CrawlURI curi, String candidate) {
    	return UriUtils.isVeryLikelyUri(candidate);
    }
    
}
