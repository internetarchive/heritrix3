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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.extractor.HTMLLinkContext;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;


/**
 * Basic link-extraction, from an HTML content-body,
 * using regular expressions.
 *
 * ROUGH DRAFT IN PROGRESS / incomplete... untested...
 *
 * @author gojomo
 */
public class RegexHTMLLinkExtractor extends CharSequenceLinkExtractor {
    private static Logger logger =
        Logger.getLogger(RegexHTMLLinkExtractor.class.getName());

    boolean honorRobots = true;
    boolean extractInlineCss = true;
    boolean extractInlineJs = true;

    protected LinkedList<Link> next = new LinkedList<Link>();
    protected Matcher tags;

    /* (non-Javadoc)
     * @see org.archive.extractor.CharSequenceLinkExtractor#findNextLink()
     */
    protected boolean findNextLink() {
        if (tags == null) {
            tags = TextUtils.getMatcher(RELEVANT_TAG_EXTRACTOR, sourceContent);
        }
        while(tags.find()) {
            if(Thread.interrupted()){
                // TODO: throw an exception, perhaps, rather than just clear & break?
                break;
            }
            if (tags.start(8) > 0) {
                // comment match
                // for now do nothing
            } else if (tags.start(7) > 0) {
                // <meta> match
                int start = tags.start(5);
                int end = tags.end(5);
                processMeta(sourceContent.subSequence(start, end));
            } else if (tags.start(5) > 0) {
                // generic <whatever> match
                int start5 = tags.start(5);
                int end5 = tags.end(5);
                int start6 = tags.start(6);
                int end6 = tags.end(6);
                processGeneralTag(sourceContent.subSequence(start6, end6),
                        sourceContent.subSequence(start5, end5));
            } else if (tags.start(1) > 0) {
                // <script> match
                int start = tags.start(1);
                int end = tags.end(1);
                processScript(sourceContent.subSequence(start, end),
                    tags.end(2) - start);
            } else if (tags.start(3) > 0){
                // <style... match
                int start = tags.start(3);
                int end = tags.end(3);
                processStyle(sourceContent.subSequence(start, end),
                    tags.end(4) - start);
            }
            if(!next.isEmpty()) {
                // at least one link found
                return true;
            }
        }
        // no relevant tags found
        return false;
    }

    /**
     * Compiled relevant tag extractor.
     *
     * <p>
     * This pattern extracts either:
     * <li> (1) whole &lt;script&gt;...&lt;/script&gt; or
     * <li> (2) &lt;style&gt;...&lt;/style&gt; or
     * <li> (3) &lt;meta ...&gt; or
     * <li> (4) any other open-tag with at least one attribute
     * (eg matches "&lt;a href='boo'&gt;" but not "&lt;/a&gt;" or "&lt;br&gt;")
     * <p>
     * groups:
     * <li> 1: SCRIPT SRC=foo&gt;boo&lt;/SCRIPT
     * <li> 2: just script open tag
     * <li> 3: STYLE TYPE=moo&gt;zoo&lt;/STYLE
     * <li> 4: just style open tag
     * <li> 5: entire other tag, without '<' '>'
     * <li> 6: element
     * <li> 7: META
     * <li> 8: !-- comment --
     */
    static final String RELEVANT_TAG_EXTRACTOR =
          "(?is)<(?:((script[^>]*+)>.*?</script)|((style[^>]*+)>[^<]*+</style)|(((meta)|(?:\\w+))\\s+[^>]*+)|(!--.*?--))>";

    // this pattern extracts attributes from any open-tag innards
    // matched by the above. attributes known to be URIs of various
    // sorts are matched specially
    static final String EACH_ATTRIBUTE_EXTRACTOR =
      "(?is)\\s((href)|(action)|(on\\w*)"
     +"|((?:src)|(?:lowsrc)|(?:background)|(?:cite)|(?:longdesc)"
     +"|(?:usemap)|(?:profile)|(?:datasrc)|(?:for))"
     +"|(codebase)|((?:classid)|(?:data))|(archive)|(code)"
     +"|(value)|([-\\w]+))"
     +"\\s*=\\s*"
     +"(?:(?:\"(.*?)(?:\"|$))"
     +"|(?:'(.*?)(?:'|$))"
     +"|(\\S+))";
    // groups:
    // 1: attribute name
    // 2: HREF - single URI relative to doc base, or occasionally javascript:
    // 3: ACTION - single URI relative to doc base, or occasionally javascript:
    // 4: ON[WHATEVER] - script handler
    // 5: SRC,LOWSRC,BACKGROUND,CITE,LONGDESC,USEMAP,PROFILE,DATASRC, or FOR
    //    single URI relative to doc base
    // 6: CODEBASE - a single URI relative to doc base, affecting other
    //    attributes
    // 7: CLASSID, DATA - a single URI relative to CODEBASE (if supplied)
    // 8: ARCHIVE - one or more space-delimited URIs relative to CODEBASE
    //    (if supplied)
    // 9: CODE - a single URI relative to the CODEBASE (is specified).
    // 10: VALUE - often includes a uri path on forms
    // 11: any other attribute
    // 12: double-quote delimited attr value
    // 13: single-quote delimited attr value
    // 14: space-delimited attr value


    // much like the javascript likely-URI extractor, but
    // without requiring quotes -- this can indicate whether
    // an HTML tag attribute that isn't definitionally a
    // URI might be one anyway, as in form-tag VALUE attributes
    static final String LIKELY_URI_PATH =
     "(\\.{0,2}[^\\.\\n\\r\\s\"']*(\\.[^\\.\\n\\r\\s\"']+)+)";
    static final String ESCAPED_AMP = "&amp;";
    static final String AMP ="&";
    static final String WHITESPACE = "\\s";
    static final String CLASSEXT =".class";
    static final String APPLET = "applet";
    static final String BASE = "base";
    static final String LINK = "link";

    protected boolean processGeneralTag(CharSequence element, CharSequence cs) {

        Matcher attr = TextUtils.getMatcher(EACH_ATTRIBUTE_EXTRACTOR, cs);

        // Just in case it's an OBJECT or APPLET tag
        String codebase = null;
        ArrayList<String> resources = null;
        long tally = next.size();

        while (attr.find()) {
            int valueGroup =
                (attr.start(12) > -1) ? 12 : (attr.start(13) > -1) ? 13 : 14;
            int start = attr.start(valueGroup);
            int end = attr.end(valueGroup);
            CharSequence value = cs.subSequence(start, end);
            if (attr.start(2) > -1) {
                // HREF
                LinkContext context = new HTMLLinkContext(element, attr.group(2));
                if(element.toString().equalsIgnoreCase(LINK)) {
                    // <LINK> elements treated as embeds (css, ico, etc)
                    processEmbed(value, context);
                } else {
                    if (element.toString().equalsIgnoreCase(BASE)) {
                        try {
                            base = UURIFactory.getInstance(value.toString());
                        } catch (URIException e) {
                            extractErrorListener.noteExtractError(e,source,value);
                        }
                    }
                    // other HREFs treated as links
                    processLink(value, context);
                }
            } else if (attr.start(3) > -1) {
                // ACTION
                LinkContext context = new HTMLLinkContext(element, attr.group(3));
                processLink(value, context);
            } else if (attr.start(4) > -1) {
                // ON____
                processScriptCode(value); // TODO: context?
            } else if (attr.start(5) > -1) {
                // SRC etc.
                LinkContext context = new HTMLLinkContext(element, attr.group(5));
                processEmbed(value, context);
            } else if (attr.start(6) > -1) {
                // CODEBASE
                // TODO: more HTML deescaping?
                codebase = TextUtils.replaceAll(ESCAPED_AMP, value, AMP);
                LinkContext context = new HTMLLinkContext(element,attr.group(6));
                processEmbed(codebase, context);
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
                if (element.toString().toLowerCase().equals(APPLET) &&
                        !value.toString().toLowerCase().endsWith(CLASSEXT)) {
                    resources.add(value.toString() + CLASSEXT);
                } else {
                    resources.add(value.toString());
                }

            } else if (attr.start(10) > -1) {
                // VALUE
                if(TextUtils.matches(LIKELY_URI_PATH, value)) {
                    LinkContext context = new HTMLLinkContext(element, attr.group(10));
                    processLink(value, context);
                }

            } else if (attr.start(11) > -1) {
                // any other attribute
                // ignore for now
                // could probe for path- or script-looking strings, but
                // those should be vanishingly rare in other attributes,
                // and/or symptomatic of page bugs
            }
        }
        TextUtils.recycleMatcher(attr);

        // handle codebase/resources
        if (resources == null) {
            return (tally-next.size())>0;
        }
        Iterator<String> iter = resources.iterator();
        UURI codebaseURI = null;
        String res = null;
        try {
            if (codebase != null) {
                // TODO: Pass in the charset.
                codebaseURI = UURIFactory.getInstance(base, codebase);
            }
            while(iter.hasNext()) {
                res = iter.next().toString();
                // TODO: more HTML deescaping?
                res = TextUtils.replaceAll(ESCAPED_AMP, res, AMP);
                if (codebaseURI != null) {
                    res = codebaseURI.resolve(res).toString();
                }
                processEmbed(res, new HTMLLinkContext(element.toString())); // TODO: include attribute too
            }
        } catch (URIException e) {
            extractErrorListener.noteExtractError(e,source,codebase);
        } catch (IllegalArgumentException e) {
            DevUtils.logger.log(Level.WARNING, "processGeneralTag()\n" +
                "codebase=" + codebase + " res=" + res + "\n" +
                DevUtils.extraInfo(), e);
        }
        return (tally-next.size())>0;
    }

    /**
     * @param cs
     */
    protected void processScriptCode(CharSequence cs) {
        RegexJSLinkExtractor.extract(cs, source, base, next,
                extractErrorListener);
    }

    static final String JAVASCRIPT = "(?i)^javascript:.*";

    /**
     * @param value
     * @param context
     */
    protected void processLink(CharSequence value, LinkContext context) {
        String link = TextUtils.replaceAll(ESCAPED_AMP, value, "&");

        if(TextUtils.matches(JAVASCRIPT, link)) {
            processScriptCode(value.subSequence(11, value.length()));
        } else {
            addLinkFromString(link, context, Hop.NAVLINK);
        }
    }

    /**
     * @param uri
     * @param context
     */
    private void addLinkFromString(String uri, LinkContext context, Hop hop) {
        try {
            Link link = new Link(source, UURIFactory.getInstance(
                    base, uri), context, hop);
            next.addLast(link);
        } catch (URIException e) {
           extractErrorListener.noteExtractError(e,source,uri);
        }
    }

    protected long processEmbed(CharSequence value, LinkContext context) {
        String embed = TextUtils.replaceAll(ESCAPED_AMP, value, "&");
        addLinkFromString(embed, context, Hop.EMBED);
        return 1;
    }

    static final String NON_HTML_PATH_EXTENSION =
        "(?i)(gif)|(jp(e)?g)|(png)|(tif(f)?)|(bmp)|(avi)|(mov)|(mp(e)?g)"+
        "|(mp3)|(mp4)|(swf)|(wav)|(au)|(aiff)|(mid)";

    protected void processScript(CharSequence sequence, int endOfOpenTag) {
        // first, get attributes of script-open tag
        // as per any other tag
        processGeneralTag(sequence.subSequence(0,6),
            sequence.subSequence(0,endOfOpenTag));

        // then, apply best-effort string-analysis heuristics
        // against any code present (false positives are OK)
        processScriptCode(sequence.subSequence(endOfOpenTag, sequence.length()));
    }

    protected void processMeta(CharSequence cs) {
        Matcher attr = TextUtils.getMatcher(EACH_ATTRIBUTE_EXTRACTOR, cs);

        String name = null;
        String httpEquiv = null;
        String content = null;

        while (attr.find()) {
            int valueGroup =
                (attr.start(12) > -1) ? 12 : (attr.start(13) > -1) ? 13 : 14;
            CharSequence value =
                cs.subSequence(attr.start(valueGroup), attr.end(valueGroup));
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
            if (getHonorRobots())  {
            String contentLower = content.toLowerCase();
                if ((contentLower.indexOf("nofollow") >= 0
                        || contentLower.indexOf("none") >= 0)) {
                    // if 'nofollow' or 'none' is specified and we
                    // are honoring robots, end html extraction
                    logger.fine("HTML extraction skipped due to robots meta-tag for: "
                                    + source);
                    cancelFurtherExtraction();
                    return;
                }
            }
        } else if ("refresh".equalsIgnoreCase(httpEquiv) && content != null) {
            String refreshUri = content.substring(content.indexOf("=") + 1);
            try {
                Link refreshLink = new Link(
                        source, 
                        UURIFactory.getInstance(base,refreshUri), 
                        new HTMLLinkContext("meta", httpEquiv),
                        Hop.REFER);
                next.addLast(refreshLink);
            } catch (URIException e) {
                extractErrorListener.noteExtractError(e,source,refreshUri);
            }
        }
    }

    /**
     * @return whether to honor internal robots directives (eg meta robots)
     */
    private boolean getHonorRobots() {
        return honorRobots;
    }

    /**
     * Ensure no further Links are extracted (by setting matcher up to fail)
     */
    private void cancelFurtherExtraction() {
        // java 1.5 only:
        // tags.region(tags.regionEnd(),tags.regionEnd());
        tags.reset(""); 
    }

    /**
     * @param sequence
     * @param endOfOpenTag
     */
    protected void processStyle(CharSequence sequence,
            int endOfOpenTag)
    {
        // First, get attributes of script-open tag as per any other tag.
        processGeneralTag(sequence.subSequence(0,6),
            sequence.subSequence(0,endOfOpenTag));

        // then, parse for URIs
        RegexCSSLinkExtractor.extract(sequence.subSequence(endOfOpenTag,
                sequence.length()), source, base, next, extractErrorListener);
    }

    /**
     * Discard all state. Another setup() is required to use again.
     */
    public void reset() {
        super.reset();
        TextUtils.recycleMatcher(tags);
        tags = null;
    }

    protected static CharSequenceLinkExtractor newDefaultInstance() {
        return new RegexHTMLLinkExtractor();
    }
}

