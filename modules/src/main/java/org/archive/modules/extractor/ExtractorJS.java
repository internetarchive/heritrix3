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

import static org.archive.modules.extractor.Hop.EMBED;
import static org.archive.modules.extractor.Hop.SPECULATIVE;
import static org.archive.modules.extractor.LinkContext.JS_IMPORT;
import static org.archive.modules.extractor.LinkContext.JS_MISC;

import java.io.IOException;
import java.io.Writer;
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
import org.archive.modules.extractor.LinkContext.SimpleLinkContext;
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
    
    // GROUPS:
    // (G1) ' or " with optional leading backslashes
    // (G2) whitespace-free string delimited on boths ends by G1

    // text before a string that makes it a static module specifier:
    // import "x", import ... from "x", export ... from "x"
    private static final Pattern STATIC_IMPORT_BEFORE =
            Pattern.compile("(?<![\\w$.])(?:import|from)\\s*$");

    // text before and after a string that makes it a dynamic module
    // specifier: import("x") or import("x", options), but not
    // import("x" + y)
    private static final Pattern DYNAMIC_IMPORT_BEFORE =
            Pattern.compile("(?<![\\w$.])import\\s*\\(\\s*$");
    private static final Pattern WHOLE_ARGUMENT_AFTER =
            Pattern.compile("^\\s*[,)]");

    // a module specifier that is a URL rather than a bare name like "lodash":
    // relative ("/x", "./x", "../x") or with a scheme ("https://...")
    private static final Pattern URL_LIKE_SPECIFIER =
            Pattern.compile("\\.{0,2}/|[a-zA-Z][a-zA-Z0-9+.-]*:");

    // text before a string that makes it relative to the module:
    // import.meta.resolve("x")
    private static final Pattern IMPORT_META_RESOLVE_BEFORE =
            Pattern.compile("(?<![\\w$.])import\\.meta\\.resolve\\s*\\(\\s*$");

    // text before and after a string that makes it relative to the module:
    // new URL("x", import.meta.url)
    private static final Pattern NEW_URL_BEFORE =
            Pattern.compile("(?<![\\w$.])new\\s+URL\\s*\\(\\s*$");
    private static final Pattern IMPORT_META_URL_AFTER =
            Pattern.compile("^\\s*,\\s*import\\.meta\\.url\\b");

    // how far around a string to look for import syntax
    private static final int IMPORT_LOOKBEHIND = 64;

    // returned by scriptRelativeContext() for bare module specifiers like
    // "lodash", which can only be resolved with the page's import map
    private static final LinkContext BARE_SPECIFIER =
            new SimpleLinkContext("=BARE_SPECIFIER");

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
        
        Matcher strings =
            TextUtils.getMatcher(JAVASCRIPT_STRING_EXTRACTOR, cs);
        
        int startIndex = 0;
        while (strings.find(startIndex)) {
            CharSequence subsequence =
            		cs.subSequence(strings.start(2), strings.end(2));
            
            if (UriUtils.isPossibleUri(subsequence)) {
                LinkContext scriptRelativeContext = handlingJSFile
                        ? scriptRelativeContext(cs, strings) : null;
                if (scriptRelativeContext != BARE_SPECIFIER
                        && considerString(ext, curi, handlingJSFile,
                                scriptRelativeContext, subsequence.toString())) {
                    foundLinks++;
                }
            }

            startIndex = strings.end(1);
        }
        TextUtils.recycleMatcher(strings);
        return foundLinks;
    }


    /**
     * Checks whether the string literal just matched by {@code strings} is one
     * that browsers resolve against the URL of the script itself rather than
     * the page that included it.
     *
     * @return {@link LinkContext#JS_IMPORT} for a relative static or dynamic
     *         import, {@link LinkContext#JS_MISC} for
     *         {@code import.meta.resolve("./x")} or
     *         {@code new URL("x", import.meta.url)}, which don't necessarily
     *         load the URL, {@link #BARE_SPECIFIER} for an import of a bare
     *         specifier like "lodash", which should be ignored, or null if it
     *         is none of these
     */
    private static LinkContext scriptRelativeContext(CharSequence cs, Matcher strings) {
        if (strings.group(1).length() != 1) {
            // escaped quotes: this is a string inside another string
            return null;
        }
        String specifier = strings.group(2);
        int start = strings.start(1);
        int end = strings.end();

        int lookbehindStart = Math.max(0, start - IMPORT_LOOKBEHIND);
        int lookaheadEnd = Math.min(cs.length(), end + IMPORT_LOOKBEHIND);
        if (matches(NEW_URL_BEFORE, cs, lookbehindStart, start)
                && matches(IMPORT_META_URL_AFTER, cs, end, lookaheadEnd)) {
            return JS_MISC;
        }

        boolean urlLike = URL_LIKE_SPECIFIER.matcher(specifier).lookingAt();
        if (matches(STATIC_IMPORT_BEFORE, cs, lookbehindStart, start)
                || (matches(DYNAMIC_IMPORT_BEFORE, cs, lookbehindStart, start)
                    && matches(WHOLE_ARGUMENT_AFTER, cs, end, lookaheadEnd))) {
            return urlLike ? JS_IMPORT : BARE_SPECIFIER;
        }
        if (matches(IMPORT_META_RESOLVE_BEFORE, cs, lookbehindStart, start)) {
            return urlLike ? JS_MISC : BARE_SPECIFIER;
        }
        return null;
    }

    private static boolean matches(Pattern pattern, CharSequence cs, int from, int to) {
        return pattern.matcher(cs).region(from, to).useTransparentBounds(true).find();
    }

    protected boolean considerString(Extractor ext, CrawlURI curi,
            boolean handlingJSFile, String candidate) {
        return considerString(ext, curi, handlingJSFile, null, candidate);
    }

    /**
     * @param scriptRelativeContext if not null, resolve the candidate against
     *        the URL of curi itself, rather than its via or base, as browsers
     *        do for module imports, and add it with this context
     */
    protected boolean considerString(Extractor ext, CrawlURI curi,
            boolean handlingJSFile, LinkContext scriptRelativeContext,
            String candidate) {
        try {
            candidate = UNESCAPE_JAVASCRIPT.translate(candidate);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "problem unescaping some javascript", e);
        }
        candidate = UriUtils.speculativeFixup(candidate, curi.getUURI());

        if (shouldAddUri(curi, candidate)) {
            try {
                int max = ext.getExtractorParameters().getMaxOutlinks();
                if (scriptRelativeContext != null) {
                    addRelativeTo(curi, max, curi.getUURI(), candidate, scriptRelativeContext,
                            scriptRelativeContext == JS_IMPORT ? EMBED : SPECULATIVE);
                    return true;
                } else if (handlingJSFile) {
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
