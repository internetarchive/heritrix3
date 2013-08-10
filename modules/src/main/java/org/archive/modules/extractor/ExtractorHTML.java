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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.RobotsPolicy;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;
import org.archive.util.UriUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Basic link-extraction, from an HTML content-body,
 * using regular expressions.
 *
 * NOTE: This processor may open a ReplayCharSequence from the 
 * CrawlURI's Recorder, without closing that ReplayCharSequence, to allow
 * reuse by later processors in sequence. In the usual (Heritrix) case, a 
 * call after all processing to the Recorder's endReplays() method ensures
 * timely close of any reused ReplayCharSequences. Reuse of this processor
 * elsewhere should ensure a similar cleanup call to Recorder.endReplays()
 * occurs. 
 * 
 * TODO: Compare against extractors based on HTML parsing libraries for 
 * accuracy, completeness, and speed.
 * 
 * @contributor gojomo
 */
public class ExtractorHTML extends ContentExtractor implements InitializingBean {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 2L;

    private static Logger logger =
        Logger.getLogger(ExtractorHTML.class.getName());

    private final static String MAX_ELEMENT_REPLACE = "MAX_ELEMENT";
    
    private final static String MAX_ATTR_NAME_REPLACE = "MAX_ATTR_NAME";
    
    private final static String MAX_ATTR_VAL_REPLACE = "MAX_ATTR_VAL";

    public final static String A_META_ROBOTS = "meta-robots";
    
    public final static String A_FORM_OFFSETS = "form-offsets";
    
    {
        setMaxElementLength(64); 
    }
    public int getMaxElementLength() {
        return (Integer) kp.get("maxElementLength");
    }
    public void setMaxElementLength(int max) {
        kp.put("maxElementLength",max);
    }
      
    
    /**
     * Relevant tag extractor.
     * 
     * <p>
     * This pattern extracts either:
     * </p>
     * <ul>
     * <li>(1) whole &lt;script&gt;...&lt;/script&gt; or
     * <li>(2) &lt;style&gt;...&lt;/style&gt; or
     * <li>(3) &lt;meta ...&gt; or
     * <li>(4) any other open-tag with at least one attribute (eg matches
     * "&lt;a href='boo'&gt;" but not "&lt;/a&gt;" or "&lt;br&gt;")
     * </ul>
     * <p>
     * groups:
     * </p>
     * <ul>
     * <li>1: SCRIPT SRC=foo&gt;boo&lt;/SCRIPT
     * <li>2: just script open tag
     * <li>3: STYLE TYPE=moo&gt;zoo&lt;/STYLE
     * <li>4: just style open tag
     * <li>5: entire other tag, without '&lt;' '>'
     * <li>6: element
     * <li>7: META
     * <li>8: !-- comment --
     * </ul>
     * 
     * <p>
     * HER-1998 - Modified part 8 to allow conditional html comments.
     * Conditional HTML comment example:
     * "&lt;!--[if expression]> HTML &lt;![endif]-->"
     * </p>
     * 
     * <p>
     * This technique is commonly used to reference CSS &amp; JavaScript that
     * are designed to deal with the quirks of a specific version of Internet
     * Explorer. There is another syntax for conditional comments which already
     * gets parsed by the regex since it doesn't start with "&lt;!--" Ex.
     * &lt;!if expression> HTML &lt;!endif>
     * </p>
     * 
     * <p>
     * https://en.wikipedia.org/wiki/Conditional_Comments
     * </p>
     */
    // version w/ less unnecessary backtracking
    static final String RELEVANT_TAG_EXTRACTOR =
      "(?is)<(?:((script[^>]*+)>.*?</script)" + // 1, 2
      "|((style[^>]*+)>.*?</style)" + // 3, 4
      "|(((meta)|(?:\\w{1,"+MAX_ELEMENT_REPLACE+"}))\\s+[^>]*+)" + // 5, 6, 7
      "|(!--(?!\\[if).*?--))>"; // 8 

//    version w/ problems with unclosed script tags 
//    static final String RELEVANT_TAG_EXTRACTOR =
//    "(?is)<(?:((script.*?)>.*?</script)|((style.*?)>.*?</style)|(((meta)|(?:\\w+))\\s+.*?)|(!--.*?--))>";


      
//    // this pattern extracts 'href' or 'src' attributes from
//    // any open-tag innards matched by the above
//    static Pattern RELEVANT_ATTRIBUTE_EXTRACTOR = Pattern.compile(
//     "(?is)(\\w+)(?:\\s+|(?:\\s.*?\\s))(?:(href)|(src))\\s*=(?:(?:\\s*\"(.+?)\")|(?:\\s*'(.+?)')|(\\S+))");
//
//    // this pattern extracts 'robots' attributes
//    static Pattern ROBOTS_ATTRIBUTE_EXTRACTOR = Pattern.compile(
//     "(?is)(\\w+)\\s+.*?(?:(robots))\\s*=(?:(?:\\s*\"(.+)\")|(?:\\s*'(.+)')|(\\S+))");

    {
        setMaxAttributeNameLength(64); // 64 chars
    }

    public int getMaxAttributeNameLength() {
        return (Integer) kp.get("maxAttributeNameLength");
    }

    public void setMaxAttributeNameLength(int max) {
        kp.put("maxAttributeNameLength", max);
    }


    {
        setMaxAttributeValLength(2048); // 2K
    }

    public int getMaxAttributeValLength() {
        return (Integer) kp.get("maxAttributeValLength");
    }

    public void setMaxAttributeValLength(int max) {
        kp.put("maxAttributeValLength", max);
    }
      
    // TODO: perhaps cut to near MAX_URI_LENGTH
    
    // this pattern extracts attributes from any open-tag innards
    // matched by the above. attributes known to be URIs of various
    // sorts are matched specially
    static final String EACH_ATTRIBUTE_EXTRACTOR =
      "(?is)\\s?((href)|(action)|(on\\w*)" // 1, 2, 3, 4 
     +"|((?:src)|(?:lowsrc)|(?:background)|(?:cite)|(?:longdesc)" // ...
     +"|(?:usemap)|(?:profile)|(?:datasrc))" // 5
     +"|(codebase)|((?:classid)|(?:data))|(archive)|(code)" // 6, 7, 8, 9
     +"|(value)|(style)|(method)" // 10, 11, 12
     +"|([-\\w]{1,"+MAX_ATTR_NAME_REPLACE+"}))" // 13
     +"\\s*=\\s*"
     +"(?:(?:\"(.{0,"+MAX_ATTR_VAL_REPLACE+"}?)(?:\"|$))" // 14
     +"|(?:'(.{0,"+MAX_ATTR_VAL_REPLACE+"}?)(?:'|$))" // 15
     +"|(\\S{1,"+MAX_ATTR_VAL_REPLACE+"}))"; // 16
    // groups:
    // 1: attribute name
    // 2: HREF - single URI relative to doc base, or occasionally javascript:
    // 3: ACTION - single URI relative to doc base, or occasionally javascript:
    // 4: ON[WHATEVER] - script handler
    // 5: SRC,LOWSRC,BACKGROUND,CITE,LONGDESC,USEMAP,PROFILE, or DATASRC
    //    single URI relative to doc base
    // 6: CODEBASE - a single URI relative to doc base, affecting other
    //    attributes
    // 7: CLASSID, DATA - a single URI relative to CODEBASE (if supplied)
    // 8: ARCHIVE - one or more space-delimited URIs relative to CODEBASE
    //    (if supplied)
    // 9: CODE - a single URI relative to the CODEBASE (is specified).
    // 10: VALUE - often includes a uri path on forms
    // 11: STYLE - inline attribute style info
    // 12: METHOD - form GET/POST
    // 13: any other attribute
    // 14: double-quote delimited attr value
    // 15: single-quote delimited attr value
    // 16: space-delimited attr value

    
    static final String WHITESPACE = "\\s";
    static final String CLASSEXT =".class";
    static final String APPLET = "applet";
    static final String BASE = "base";
    static final String LINK = "link";
    static final String FRAME = "frame";
    static final String IFRAME = "iframe";

    
    /**
     * If true, FRAME/IFRAME SRC-links are treated as embedded resources (like
     * IMG, 'E' hop-type), otherwise they are treated as navigational links.
     * Default is true.
     */
    {
        setTreatFramesAsEmbedLinks(true);
    }
    public boolean getTreatFramesAsEmbedLinks() {
        return (Boolean) kp.get("treatFramesAsEmbedLinks");
    }
    public void setTreatFramesAsEmbedLinks(boolean asEmbeds) {
        kp.put("treatFramesAsEmbedLinks",asEmbeds);
    }
    
    /**
     * If true, URIs appearing as the ACTION attribute in HTML FORMs are
     * ignored. Default is false.
     */
    {
        setIgnoreFormActionUrls(false);
    }
    public boolean getIgnoreFormActionUrls() {
        return (Boolean) kp.get("ignoreFormActionUrls");
    }
    public void setIgnoreFormActionUrls(boolean ignoreActions) {
        kp.put("ignoreFormActionUrls",ignoreActions);
    }

    /**
     * If true, only ACTION URIs with a METHOD of GET (explicit or implied)
     * are extracted. Default is true.
     */
    {
        setExtractOnlyFormGets(true);
    }
    public boolean getExtractOnlyFormGets() {
        return (Boolean) kp.get("extractOnlyFormGets");
    }
    public void setExtractOnlyFormGets(boolean onlyGets) {
        kp.put("extractOnlyFormGets",onlyGets);
    }
    
    /**
     * If true, in-page Javascript is scanned for strings that
     * appear likely to be URIs. This typically finds both valid
     * and invalid URIs, and attempts to fetch the invalid URIs
     * sometimes generates webmaster concerns over odd crawler
     * behavior. Default is true.
     */
    {
        setExtractJavascript(true);
    }
    public boolean getExtractJavascript() {
        return (Boolean) kp.get("extractJavascript");
    }
    public void setExtractJavascript(boolean extractJavascript) {
        kp.put("extractJavascript",extractJavascript);
    }    

    /**
     * If true, strings that look like URIs found in unusual places (such as
     * form VALUE attributes) will be extracted. This typically finds both valid
     * and invalid URIs, and attempts to fetch the invalid URIs sometimes
     * generate webmaster concerns over odd crawler behavior. Default is true.
     */
    {
        setExtractValueAttributes(true);
    }
    public boolean getExtractValueAttributes() {
        return (Boolean) kp.get("extractValueAttributes");
    }
    public void setExtractValueAttributes(boolean extractValueAttributes) {
        kp.put("extractValueAttributes",extractValueAttributes);
    }    

    /**
     * If true, URIs which end in typical non-HTML extensions (such as .gif)
     * will not be scanned as if it were HTML. Default is true.
     */
    {
        setIgnoreUnexpectedHtml(true);
    }
    public boolean getIgnoreUnexpectedHtml() {
        return (Boolean) kp.get("ignoreUnexpectedHtml");
    }
    public void setIgnoreUnexpectedHtml(boolean ignoreUnexpectedHtml) {
        kp.put("ignoreUnexpectedHtml",ignoreUnexpectedHtml);
    }
    
    /**
     * CrawlMetadata provides the robots honoring policy to use when 
     * considering a robots META tag.
     */
    protected CrawlMetadata metadata;
    public CrawlMetadata getMetadata() {
        return metadata;
    }
    @Autowired
    public void setMetadata(CrawlMetadata provider) {
        this.metadata = provider;
    }
    
    /**
     * Javascript extractor to use to process inline javascript. Autowired if
     * available. If null, links will not be extracted from inline javascript.
     */
    transient protected ExtractorJS extractorJS;
    public ExtractorJS getExtractorJS() {
        return extractorJS;
    }
    @Autowired
    public void setExtractorJS(ExtractorJS extractorJS) {
        this.extractorJS = extractorJS;
    }
    
    // TODO: convert to Strings
    private String relevantTagPattern;
    private String eachAttributePattern;
 
    public ExtractorHTML() {
    }

    public void afterPropertiesSet() {
        String regex = RELEVANT_TAG_EXTRACTOR;
        regex = regex.replace(MAX_ELEMENT_REPLACE, 
                    Integer.toString(getMaxElementLength()));
        this.relevantTagPattern = regex;
        
        regex = EACH_ATTRIBUTE_EXTRACTOR;
        regex = regex.replace(MAX_ATTR_NAME_REPLACE, 
                    Integer.toString(getMaxAttributeNameLength()));
        regex = regex.replace(MAX_ATTR_VAL_REPLACE,
                    Integer.toString(getMaxAttributeValLength()));
        this.eachAttributePattern = regex;
    }
    

    protected void processGeneralTag(CrawlURI curi, CharSequence element,
            CharSequence cs) {

        Matcher attr = TextUtils.getMatcher(eachAttributePattern,cs);

        // Just in case it's an OBJECT or APPLET tag
        String codebase = null;
        ArrayList<String> resources = null;
        
        // Just in case it's a FORM
        CharSequence action = null;
        CharSequence actionContext = null;
        CharSequence method = null; 
        
        // Just in case it's a VALUE whose interpretation depends on accompanying NAME
        CharSequence valueVal = null; 
        CharSequence valueContext = null;
        CharSequence nameVal = null; 
        
        final boolean framesAsEmbeds = 
            getTreatFramesAsEmbedLinks();

        final boolean ignoreFormActions = 
            getIgnoreFormActionUrls();
        
        final boolean extractValueAttributes = 
            getExtractValueAttributes();
        
        final String elementStr = element.toString();

        while (attr.find()) {
            int valueGroup =
                (attr.start(14) > -1) ? 14 : (attr.start(15) > -1) ? 15 : 16;
            int start = attr.start(valueGroup);
            int end = attr.end(valueGroup);
            assert start >= 0: "Start is: " + start + ", " + curi;
            assert end >= 0: "End is :" + end + ", " + curi;
            CharSequence value = cs.subSequence(start, end);
            CharSequence attrName = cs.subSequence(attr.start(1),attr.end(1));
            value = TextUtils.unescapeHtml(value);
            if (attr.start(2) > -1) {
                // HREF
                CharSequence context = elementContext(element, attr.group(2));
                if(elementStr.equalsIgnoreCase(LINK)) {
                    // <LINK> elements treated as embeds (css, ico, etc)
                    processEmbed(curi, value, context);
                } else {
                    // other HREFs treated as links
                    processLink(curi, value, context);
                }
                if (elementStr.equalsIgnoreCase(BASE)) {
                    try {
                        UURI base = UURIFactory.getInstance(value.toString());
                        curi.setBaseURI(base);
                    } catch (URIException e) {
                        logUriError(e, curi.getUURI(), value);
                    }
                }
            } else if (attr.start(3) > -1) {
                // ACTION
                if (!ignoreFormActions) {
                    action = value; 
                    actionContext = elementContext(element, attr.group(3));
                    // handling finished only at end (after METHOD also collected)
                }
            } else if (attr.start(4) > -1) {
                // ON____
                processScriptCode(curi, value); // TODO: context?
            } else if (attr.start(5) > -1) {
                // SRC etc.
                CharSequence context = elementContext(element, attr.group(5));
                
                // true, if we expect another HTML page instead of an image etc.
                final Hop hop;
                
                if(!framesAsEmbeds
                    && (elementStr.equalsIgnoreCase(FRAME) || elementStr
                        .equalsIgnoreCase(IFRAME))) {
                    hop = Hop.NAVLINK;
                } else {
                    hop = Hop.EMBED;
                }
                processEmbed(curi, value, context, hop);
            } else if (attr.start(6) > -1) {
                // CODEBASE
                codebase = (value instanceof String)?
                    (String)value: value.toString();
                CharSequence context = elementContext(element,
                    attr.group(6));
                processEmbed(curi, codebase, context);
            } else if (attr.start(7) > -1) {
                // CLASSID, DATA
                if (resources == null) {
                    resources = new ArrayList<String>();
                }
                resources.add(value.toString());
            } else if (attr.start(8) > -1) {
                // ARCHIVE
                if (resources==null) {
                    resources = new ArrayList<String>();
                }
                String[] multi = TextUtils.split(WHITESPACE, value);
                for(int i = 0; i < multi.length; i++ ) {
                    resources.add(multi[i]);
                }
            } else if (attr.start(9) > -1) {
                // CODE
                if (resources==null) {
                    resources = new ArrayList<String>();
                }
                // If element is applet and code value does not end with
                // '.class' then append '.class' to the code value.
                if (elementStr.equalsIgnoreCase(APPLET) &&
                        !value.toString().toLowerCase().endsWith(CLASSEXT)) {
                    resources.add(value.toString() + CLASSEXT);
                } else {
                    resources.add(value.toString());
                }
            } else if (attr.start(10) > -1) {
                // VALUE, with possibility of URI
                // store value, context for handling at end
                valueVal = value; 
                valueContext = elementContext(element,attr.group(10));
            } else if (attr.start(11) > -1) {
                // STYLE inline attribute
                // then, parse for URIs
                numberOfLinksExtracted.addAndGet(ExtractorCSS.processStyleCode(
                        this, curi, value));        
            } else if (attr.start(12) > -1) {
                // METHOD
                method = value;
                // form processing finished at end (after ACTION also collected)
            } else if (attr.start(13) > -1) {
                if("NAME".equalsIgnoreCase(attrName.toString())) {
                    // remember 'name' for end-analysis
                    nameVal = value; 
                }
                if("FLASHVARS".equalsIgnoreCase(attrName.toString())) {
                    // consider FLASHVARS attribute immediately
                    valueContext = elementContext(element,attr.group(13));
                    considerQueryStringValues(curi, value, valueContext,Hop.SPECULATIVE);
                }
                // any other attribute
                // ignore for now
                // could probe for path- or script-looking strings, but
                // those should be vanishingly rare in other attributes,
                // and/or symptomatic of page bugs
            }
        }
        TextUtils.recycleMatcher(attr);

        // handle codebase/resources
        if (resources != null) {
            Iterator<String> iter = resources.iterator();
            UURI codebaseURI = null;
            String res = null;
            try {
                if (codebase != null) {
                    // TODO: Pass in the charset.
                    codebaseURI = UURIFactory.
                        getInstance(curi.getUURI(), codebase);
                }
                while(iter.hasNext()) {
                    res = iter.next().toString();
                    res = (String) TextUtils.unescapeHtml(res);
                    if (codebaseURI != null) {
                        res = codebaseURI.resolve(res).toString();
                    }
                    processEmbed(curi, res, element); // TODO: include attribute too
                }
            } catch (URIException e) {
                curi.getNonFatalFailures().add(e);
            } catch (IllegalArgumentException e) {
                DevUtils.logger.log(Level.WARNING, "processGeneralTag()\n" +
                    "codebase=" + codebase + " res=" + res + "\n" +
                    DevUtils.extraInfo(), e);
            }
        }
           
        // finish handling form action, now method is available
        if(action != null) {
            if(method == null || "GET".equalsIgnoreCase(method.toString()) 
                        || ! getExtractOnlyFormGets()) {
                processLink(curi, action, actionContext);
            }
        }
        
        // finish handling VALUE
        if(valueVal != null) {
            if ("PARAM".equalsIgnoreCase(elementStr) && nameVal != null
                    && "flashvars".equalsIgnoreCase(nameVal.toString())) {
                // special handling for <PARAM NAME='flashvars" VALUE="">
                String queryStringLike = valueVal.toString();
                // treat value as query-string-like "key=value[&key=value]*" pairings
                considerQueryStringValues(curi, queryStringLike, valueContext,Hop.SPECULATIVE);
            } else {
                // regular VALUE handling
                if (extractValueAttributes) {
                    considerIfLikelyUri(curi,valueVal,valueContext,Hop.NAVLINK);
                }
            }
        }
    }

    /**
     * Consider a query-string-like collections of key=value[&key=value]
     * pairs for URI-like strings in the values. Where URI-like strings are
     * found, add as discovered outlink. 
     * 
     * @param curi origin CrawlURI
     * @param queryString query-string-like string
     * @param valueContext page context where found
     */
    protected void considerQueryStringValues(CrawlURI curi,
            CharSequence queryString, CharSequence valueContext, Hop hop) {
        for (String pairString : queryString.toString().split("&")) {
            String[] encodedKeyVal = pairString.split("=");
            if (encodedKeyVal.length == 2) try {
                String value = URLDecoder.decode(encodedKeyVal[1], "UTF-8");
                considerIfLikelyUri(curi, value, valueContext, hop);
            } catch (IllegalArgumentException e) {
                // still consider values rejected by URLDecoder
                considerIfLikelyUri(curi, encodedKeyVal[1], valueContext, hop);
            } catch (UnsupportedEncodingException e) {
                throw new AssertionError("all jvms must support UTF-8, and yet somehow this happened: " + e);
            }
        }
    }


    /**
     * Consider whether a given string is URI-like. If so, add as discovered 
     * outlink. 
     * 
     * @param curi origin CrawlURI
     * @param queryString query-string-like string
     * @param valueContext page context where found

     */
    protected void considerIfLikelyUri(CrawlURI curi, CharSequence candidate, 
            CharSequence valueContext, Hop hop) {
        if(UriUtils.isVeryLikelyUri(candidate)) {
            addLinkFromString(curi,candidate,valueContext,hop);
        }
    }


    /**
     * Extract the (java)script source in the given CharSequence.
     *
     * @param curi  source CrawlURI
     * @param cs    CharSequence of javascript code
     */
    protected void processScriptCode(CrawlURI curi, CharSequence cs) {
        if (getExtractorJS() != null && getExtractJavascript()) {
            numberOfLinksExtracted.addAndGet(
                getExtractorJS().considerStrings(this, curi, cs));
        }
    }

    static final String JAVASCRIPT = "(?i)^javascript:.*";

    /**
     * Handle generic HREF cases.
     * 
     * @param curi
     * @param value
     * @param context
     */
    protected void processLink(CrawlURI curi, final CharSequence value,
            CharSequence context) {
        if (TextUtils.matches(JAVASCRIPT, value)) {
            processScriptCode(curi, value. subSequence(11, value.length()));
        } else {    
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("link: " + value.toString() + " from " + curi);
            }
            addLinkFromString(curi, value, context, Hop.NAVLINK);
            numberOfLinksExtracted.incrementAndGet();
        }
    }

    protected void addLinkFromString(CrawlURI curi, CharSequence uri,
            CharSequence context, Hop hop) {
        try {
            // We do a 'toString' on context because its a sequence from
            // the underlying ReplayCharSequence and the link its about
            // to become a part of is expected to outlive the current
            // ReplayCharSequence.
            HTMLLinkContext hc = HTMLLinkContext.get(context.toString());
            int max = getExtractorParameters().getMaxOutlinks();
            Link.addRelativeToBase(curi, max, uri.toString(), hc, hop);
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), uri);
        }
    }

    protected final void processEmbed(CrawlURI curi, CharSequence value,
            CharSequence context) {
        processEmbed(curi, value, context, Hop.EMBED);
    }

    protected void processEmbed(CrawlURI curi, final CharSequence value,
            CharSequence context, Hop hop) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.finest("embed (" + hop.getHopChar() + "): " + value.toString() +
                " from " + curi);
        }
        addLinkFromString(curi,
            (value instanceof String)?
                (String)value: value.toString(),
            context, hop);
        numberOfLinksExtracted.incrementAndGet();
    }

    
    protected boolean shouldExtract(CrawlURI uri) {
        if (getIgnoreUnexpectedHtml()) {
            try {
                // HTML was not expected (eg a GIF was expected) so ignore
                // (as if a soft 404)
                if (!isHtmlExpectedHere(uri)) {
                    return false;
                }
            } catch (URIException e) {
                logger.severe("Failed expectedHTML test: " + e.getMessage());
                // assume it's okay to extract
            }
        }
        
        String mime = uri.getContentType().toLowerCase();
        return mime.startsWith("text/html")
                || mime.startsWith("application/xhtml")
                || mime.startsWith("text/vnd.wap.wml")
                || mime.startsWith("application/vnd.wap.wml")
                || mime.startsWith("application/vnd.wap.xhtml");
    }
    
    
    public boolean innerExtract(CrawlURI curi) {
        if (!curi.containsContentTypeCharsetDeclaration()) {
            String contentPrefix = curi.getRecorder().getContentReplayPrefixString(1000);
            Charset contentDeclaredEncoding = getContentDeclaredCharset(curi,contentPrefix);
            if(!curi.getRecorder().getCharset().equals(contentDeclaredEncoding) && contentDeclaredEncoding!=null) {
                String newContentPrefix = curi.getRecorder().getContentReplayPrefixString(1000,contentDeclaredEncoding); 
                Charset reflexiveCharset = getContentDeclaredCharset(curi, newContentPrefix);
                if(contentDeclaredEncoding.equals(reflexiveCharset)) {
                    // content-declared charset is self-consistent; use
                    curi.getAnnotations().add("usingCharsetInHTML:"+contentDeclaredEncoding);
                    curi.getRecorder().setCharset(contentDeclaredEncoding);
                } else {
                    // error: declared charset not evident once put into effect
                    curi.getAnnotations().add("inconsistentCharsetInHTML:"+contentDeclaredEncoding);
                    // so, ignore in favor of original default
                }
            }
        }

        try {
            ReplayCharSequence cs = curi.getRecorder().getContentReplayCharSequence();
           // Extract all links from the charsequence
           extract(curi, cs);
           if(cs.getDecodeExceptionCount()>0) {
               curi.getNonFatalFailures().add(cs.getCodingException()); 
           }
           // Set flag to indicate that link extraction is completed.
           return true;
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            logger.log(Level.WARNING,"Failed get of replay char sequence in " +
                Thread.currentThread().getName(), e);
        }
        return false;
    }
    
    // 1. look for <meta http-equiv="content-type"...>
    // 2. if not found then look for <meta charset="">
    // 3. if not found then <?xml encoding=""...?>
    protected Charset getContentDeclaredCharset(CrawlURI curi, String contentPrefix) {
        String charsetName = null; 
        // <meta http-equiv="content-type" content="text/html; charset=iso-8859-1">
        Matcher matcher = TextUtils.getMatcher("(?is)<meta\\s+[^>]*http-equiv\\s*=\\s*['\"]content-type['\"][^>]*>", contentPrefix);
        if (matcher.find()) {
            String metaContentType = matcher.group();
            TextUtils.recycleMatcher(matcher); 
            matcher = TextUtils.getMatcher("charset=([^'\";\\s>]+)", metaContentType);
            if (matcher.find()) {
                charsetName = matcher.group(1); 
            }
            TextUtils.recycleMatcher(matcher); 
        }

        if(charsetName==null) {
            // <meta charset="utf-8">
            matcher = TextUtils.getMatcher("(?si)<meta\\s+[^>]*charset=['\"]([^'\";\\s>]+)['\"]", contentPrefix);
            if (matcher.find()) {
                charsetName = matcher.group(1); 
                TextUtils.recycleMatcher(matcher); 
            } else {
                // <?xml version="1.0" encoding="utf-8"?>
                matcher = TextUtils.getMatcher("(?is)<\\?xml\\s+[^>]*encoding=['\"]([^'\"]+)['\"]", contentPrefix);
                if (matcher.find()) {
                    charsetName = matcher.group(1); 
                } else {
                    return null; // none found
                }
                TextUtils.recycleMatcher(matcher); 
            }
        }
        try {
            return Charset.forName(charsetName); 
        } catch (IllegalArgumentException iae) {
            logger.log(Level.INFO,"Unknown content-encoding '"+charsetName+"' declared; using default");  
            curi.getAnnotations().add("unsatisfiableCharsetInHTML:"+charsetName);
            return null; 
        } 
    }

    /**
     * Run extractor.
     * This method is package visible to ease testing.
     * @param curi CrawlURI we're processing.
     * @param cs Sequence from underlying ReplayCharSequence. This
     * is TRANSIENT data. Make a copy if you want the data to live outside
     * of this extractors' lifetime.
     */
    protected void extract(CrawlURI curi, CharSequence cs) {
        Matcher tags = TextUtils.getMatcher(relevantTagPattern,cs);
        while(tags.find()) {
            if(Thread.interrupted()){
                break;
            }
            if (tags.start(8) > 0) {
                // comment match
                // for now do nothing
            } else if (tags.start(7) > 0) {
                // <meta> match
                int start = tags.start(5);
                int end = tags.end(5);
                assert start >= 0: "Start is: " + start + ", " + curi;
                assert end >= 0: "End is :" + end + ", " + curi;
                if (processMeta(curi,
                    cs.subSequence(start, end))) {

                    // meta tag included NOFOLLOW; abort processing
                    break;
                }
            } else if (tags.start(5) > 0) {
                // generic <whatever> match
                int start5 = tags.start(5);
                int end5 = tags.end(5);
                assert start5 >= 0: "Start is: " + start5 + ", " + curi;
                assert end5 >= 0: "End is :" + end5 + ", " + curi;
                int start6 = tags.start(6);
                int end6 = tags.end(6);
                assert start6 >= 0: "Start is: " + start6 + ", " + curi;
                assert end6 >= 0: "End is :" + end6 + ", " + curi;
                String element = cs.subSequence(start6, end6).toString();
                CharSequence attributes = cs.subSequence(start5, end5);
                processGeneralTag(curi,
                    element,
                    attributes);
                // remember FORM to help later extra processing
                if ("form".equalsIgnoreCase(element)) {
                    curi.getDataList(A_FORM_OFFSETS).add((Integer)(start6-1));
                }
               

            } else if (tags.start(1) > 0) {
                // <script> match
                int start = tags.start(1);
                int end = tags.end(1);
                assert start >= 0: "Start is: " + start + ", " + curi;
                assert end >= 0: "End is :" + end + ", " + curi;
                assert tags.end(2) >= 0: "Tags.end(2) illegal " + tags.end(2) +
                    ", " + curi;
                processScript(curi, cs.subSequence(start, end),
                    tags.end(2) - start);

            } else if (tags.start(3) > 0){
                // <style... match
                int start = tags.start(3);
                int end = tags.end(3);
                assert start >= 0: "Start is: " + start + ", " + curi;
                assert end >= 0: "End is :" + end + ", " + curi;
                assert tags.end(4) >= 0: "Tags.end(4) illegal " + tags.end(4) +
                    ", " + curi;
                processStyle(curi, cs.subSequence(start, end),
                    tags.end(4) - start);
            }
        }
        TextUtils.recycleMatcher(tags);
    }


    static final String NON_HTML_PATH_EXTENSION =
        "(?i)(gif)|(jp(e)?g)|(png)|(tif(f)?)|(bmp)|(avi)|(mov)|(mp(e)?g)"+
        "|(mp3)|(mp4)|(swf)|(wav)|(au)|(aiff)|(mid)";

    /**
     * Test whether this HTML is so unexpected (eg in place of a GIF URI)
     * that it shouldn't be scanned for links.
     *
     * @param curi CrawlURI to examine.
     * @return True if HTML is acceptable/expected here
     * @throws URIException
     */
    protected boolean isHtmlExpectedHere(CrawlURI curi) throws URIException {
        String path = curi.getUURI().getPath();
        if(path==null) {
            // no path extension, HTML is fine
            return true;
        }
        int dot = path.lastIndexOf('.');
        if (dot < 0) {
            // no path extension, HTML is fine
            return true;
        }
        if(dot<(path.length()-5)) {
            // extension too long to recognize, HTML is fine
            return true;
        }
        String ext = path.substring(dot+1);
        return ! TextUtils.matches(NON_HTML_PATH_EXTENSION, ext);
    }

    protected void processScript(CrawlURI curi, CharSequence sequence,
            int endOfOpenTag) {
        // first, get attributes of script-open tag
        // as per any other tag
        processGeneralTag(curi,sequence.subSequence(0,6),
            sequence.subSequence(0,endOfOpenTag));

        // then, apply best-effort string-analysis heuristics
        // against any code present (false positives are OK)
        processScriptCode(
            curi, sequence.subSequence(endOfOpenTag, sequence.length()));
    }

    /**
     * Process metadata tags.
     * @param curi CrawlURI we're processing.
     * @param cs Sequence from underlying ReplayCharSequence. This
     * is TRANSIENT data. Make a copy if you want the data to live outside
     * of this extractors' lifetime.
     * @return True robots exclusion metatag.
     */
    protected boolean processMeta(CrawlURI curi, CharSequence cs) {
        Matcher attr = TextUtils.getMatcher(eachAttributePattern,cs);
        String name = null;
        String httpEquiv = null;
        String content = null;
        while (attr.find()) {
            int valueGroup =
                (attr.start(14) > -1) ? 14 : (attr.start(15) > -1) ? 15 : 16;
            CharSequence value =
                cs.subSequence(attr.start(valueGroup), attr.end(valueGroup));
            value = TextUtils.unescapeHtml(value);
            if (attr.group(1).equalsIgnoreCase("name")) {
                name = value.toString();
            } else if (attr.group(1).equalsIgnoreCase("http-equiv")) {
                httpEquiv = value.toString();
            } else if (attr.group(1).equalsIgnoreCase("content")) {
                content = value.toString();
            }
            // TODO: handle other stuff
        }
        TextUtils.recycleMatcher(attr);

        // Look for the 'robots' meta-tag
        if("robots".equalsIgnoreCase(name) && content != null ) {
            curi.getData().put(A_META_ROBOTS, content);
            RobotsPolicy policy = metadata.getRobotsPolicy();
            String contentLower = content.toLowerCase();
            if (policy.obeyMetaRobotsNofollow()
                && (contentLower.indexOf("nofollow") >= 0
                    || contentLower.indexOf("none") >= 0)) {
                // if 'nofollow' or 'none' is specified and the
                // honoring policy is not IGNORE or CUSTOM, end html extraction
                logger.fine("HTML extraction skipped due to robots meta-tag for: "
                                + curi.toString());
                return true;
            }
        } else if ("refresh".equalsIgnoreCase(httpEquiv) && content != null) {
            int urlIndex = content.indexOf("=") + 1;
            if(urlIndex>0) {
                String refreshUri = content.substring(urlIndex);
                try {
                    int max = getExtractorParameters().getMaxOutlinks();
                    Link.addRelativeToBase(curi, max, refreshUri, 
                            HTMLLinkContext.META, Hop.REFER);
                } catch (URIException e) {
                    logUriError(e, curi.getUURI(), refreshUri);
                }
            }
        }
        return false;
    }

    /**
     * Process style text.
     * @param curi CrawlURI we're processing.
     * @param sequence Sequence from underlying ReplayCharSequence. This
     * is TRANSIENT data. Make a copy if you want the data to live outside
     * of this extractors' lifetime.
     * @param endOfOpenTag
     */
    protected void processStyle(CrawlURI curi, CharSequence sequence,
            int endOfOpenTag) {
        // First, get attributes of script-open tag as per any other tag.
        processGeneralTag(curi, sequence.subSequence(0,6),
            sequence.subSequence(0,endOfOpenTag));

        // then, parse for URIs
        numberOfLinksExtracted.addAndGet(ExtractorCSS.processStyleCode(
                this,
                curi, 
                sequence.subSequence(endOfOpenTag,sequence.length())));
    }   
    
    /**
     * Create a suitable XPath-like context from an element name and optional
     * attribute name. 
     * 
     * @param element
     * @param attribute
     * @return CharSequence context
     */
    public static CharSequence elementContext(CharSequence element, CharSequence attribute) {
        return attribute == null? "": element + "/@" + attribute;
    }

}

