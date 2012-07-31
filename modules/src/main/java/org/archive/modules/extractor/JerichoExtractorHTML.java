/*RELICENSE_RESEARCH*/
/* JerichoExtractorHTML
 * 
 * Copyright (C) 2006 Olaf Freyer
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
 * $Id$
 */

package org.archive.modules.extractor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.RobotsPolicy;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.DevUtils;
import org.archive.util.TextUtils;

import au.id.jericho.lib.html.Attribute;
import au.id.jericho.lib.html.Attributes;
import au.id.jericho.lib.html.Element;
import au.id.jericho.lib.html.FormControl;
import au.id.jericho.lib.html.FormControlType;
import au.id.jericho.lib.html.FormField;
import au.id.jericho.lib.html.HTMLElementName;
import au.id.jericho.lib.html.Source;
import au.id.jericho.lib.html.StartTagType;

/**
 * Improved link-extraction from an HTML content-body using jericho-html parser.
 * This extractor extends ExtractorHTML and mimics its workflow - but has some
 * substantial differences when it comes to internal implementation. Instead
 * of heavily relying upon java regular expressions it uses a real html parser
 * library - namely Jericho HTML Parser (http://jerichohtml.sourceforge.net).
 * Using this parser it can better handle broken html (i.e. missing quotes)
 * and also offer improved extraction of HTML form URLs (not only extract
 * the action of a form, but also its default values).
 * Unfortunately this parser also has one major drawback - it has to read the
 * whole document into memory for parsing, thus has an inherent OOME risk.
 * This OOME risk can be reduced/eleminated by limiting the size of documents
 * to be parsed (i.e. using NotExceedsDocumentLengthTresholdDecideRule).
 * Also note that this extractor seems to have a lower overall memory 
 * consumption compared to ExtractorHTML. (still to be confirmed on a larger 
 * scale crawl) 
 * 
 * @author Olaf Freyer
 * @version $Date$ $Revision$
 */
@SuppressWarnings("unchecked")
public class JerichoExtractorHTML extends ExtractorHTML {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1684681316546343615L;

    final private static Logger logger = 
        Logger.getLogger(JerichoExtractorHTML.class.getName());

    protected AtomicLong numberOfFormsProcessed = new AtomicLong(0);

    /*
    public JerichoExtractorHTML(String name) {
        this(name, "Jericho-HTML extractor. Extracts links from HTML " +
                "documents using Jericho HTML Parser. Offers same " + 
                "basic functionality as ExtractorHTML but better " +
                "handles broken HTML and extraction of default " +
                "values from HTML forms. A word of warning: the used " +
                "parser, the Jericho HTML Parser, reads the whole " +
                "document into memory for " +
                "parsing - thus this extractor has an inherent OOME risk. " +
                "This OOME risk can be reduced/eliminated by limiting the " +
                "size of documents to be parsed (i.e. using " +
                "NotExceedsDocumentLengthTresholdDecideRule). ");
    }*/

    public JerichoExtractorHTML() {
        super();
    }

    private static List<Attribute> findOnAttributes(Attributes attributes) {
        List<Attribute> result = new LinkedList<Attribute>();
        for (Attribute attr : (Iterable<Attribute>)attributes) {
            if (attr.getKey().startsWith("on"))
                result.add(attr);
        }
        return result;
    }


    protected void processGeneralTag(CrawlURI curi, Element element,
            Attributes attributes) {
        Attribute attr;
        String attrValue;
        List<Attribute> attrList;
        String elementName = element.getName();

        // Just in case it's an OBJECT or APPLET tag
        String codebase = null;
        ArrayList<String> resources = null;

        final boolean framesAsEmbeds = getTreatFramesAsEmbedLinks();

        final boolean ignoreFormActions = getIgnoreFormActionUrls();
        
        final boolean overlyEagerLinkDetection = getExtractValueAttributes();

        // HREF
        if (((attr = attributes.get("href")) != null) &&
            ((attrValue = attr.getValue()) != null)) {
            CharSequence context = elementContext(elementName, attr
                    .getKey());
            if ("link".equals(elementName)) {
                // <LINK> elements treated as embeds (css, ico, etc)
                processEmbed(curi, attrValue, context);
            } else {
                // other HREFs treated as links
                processLink(curi, attrValue, context);
            }
            if ("base".equals(elementName)) {
                try {
                    UURI base = UURIFactory.getInstance(attrValue);
                    curi.setBaseURI(base);
                } catch (URIException e) {
                    logUriError(e, curi.getUURI(), attrValue);
                }
            }
        }
        // ACTION
        if (((attr = attributes.get("action")) != null) &&
                 ((attrValue = attr.getValue()) != null)) {
            if (!ignoreFormActions) {
                CharSequence context = elementContext(elementName, attr
                        .getKey());
                processLink(curi, attrValue, context);
            }
        }
        // ON_
        if ((attrList = findOnAttributes(attributes)).size() != 0) {
            for (Iterator<Attribute> attrIter = attrList.iterator(); attrIter.hasNext();) {
                attr = (Attribute) attrIter.next();
                CharSequence valueSegment = attr.getValueSegment();
                if (valueSegment != null)
                    processScriptCode(curi, valueSegment);

            }
        }
        // SRC atc.
        if ((((attr = attributes.get("src")) != null)
                || ((attr = attributes.get("lowsrc")) != null)
                || ((attr = attributes.get("background")) != null)
                || ((attr = attributes.get("cite")) != null)
                || ((attr = attributes.get("longdesc")) != null)
                || ((attr = attributes.get("usemap")) != null)
                || ((attr = attributes.get("profile")) != null)
                || ((attr = attributes.get("datasrc")) != null)) &&
                   ((attrValue = attr.getValue()) != null)) {

            final Hop hopType;
            CharSequence context = elementContext(elementName, attr.getKey());

            if (!framesAsEmbeds
                    && ("frame".equals(elementName) || "iframe"
                            .equals(elementName)))
                hopType = Hop.NAVLINK;
            else
                hopType = Hop.EMBED;

            processEmbed(curi, attrValue, context, hopType);
        }
        // CODEBASE
        if (((attr = attributes.get("codebase")) != null) &&
                 ((attrValue = attr.getValue()) != null)) {
            codebase = StringEscapeUtils.unescapeHtml(attrValue);
            CharSequence context = elementContext(elementName, attr.getKey());
            processEmbed(curi, codebase, context);
        }
        // CLASSID DATA
        if ((((attr = attributes.get("classid")) != null)
                || ((attr = attributes.get("data")) != null)) &&
                   ((attrValue = attr.getValue()) != null)) {
            if (resources == null)
                resources = new ArrayList<String>();
            resources.add(attrValue);
        }
        // ARCHIVE
        if (((attr = attributes.get("archive")) != null) &&
                 ((attrValue = attr.getValue()) != null)) {
            if (resources == null)
                resources = new ArrayList<String>();
            String[] multi = TextUtils.split(WHITESPACE, attrValue);
            for (int i = 0; i < multi.length; i++) {
                resources.add(multi[i]);
            }
        }
        // CODE
        if (((attr = attributes.get("code")) != null) &&
                 ((attrValue = attr.getValue()) != null)) {
            if (resources == null)
                resources = new ArrayList<String>();
            // If element is applet and code value does not end with
            // '.class' then append '.class' to the code value.
            if (APPLET.equals(elementName) && !attrValue.endsWith(CLASSEXT)) {
                resources.add(attrValue + CLASSEXT);
            } else {
                resources.add(attrValue);
            }
        }
        // VALUE
        if (((attr = attributes.get("value")) != null) &&
                 ((attrValue = attr.getValue()) != null)) {
            CharSequence valueContext = elementContext(elementName, attr.getKey());
            if("PARAM".equalsIgnoreCase(elementName) 
                    && "flashvars".equalsIgnoreCase(attributes.get("name").getValue())) {
                // special handling for <PARAM NAME='flashvars" VALUE="">
                String queryStringLike = attrValue.toString();
                // treat value as query-string-like "key=value[;key=value]*" pairings
                considerQueryStringValues(curi, queryStringLike, valueContext,Hop.SPECULATIVE);
            } else {
                // regular VALUE handling
                if (overlyEagerLinkDetection) {
                    considerIfLikelyUri(curi,attrValue,valueContext,Hop.NAVLINK);
                }
            }
        }
        // STYLE
        if (((attr = attributes.get("style")) != null) &&
                 ((attrValue = attr.getValue()) != null)) {
            // STYLE inline attribute
            // then, parse for URIs
            numberOfLinksExtracted.addAndGet(ExtractorCSS.processStyleCode(
                    this, curi, attrValue));
        }
        
        // FLASHVARS
        if (((attr = attributes.get("flashvars")) != null) &&
                ((attrValue = attr.getValue()) != null)) {
            // FLASHVARS inline attribute
            CharSequence valueContext = elementContext(elementName, attr.getKey());
            considerQueryStringValues(curi, attrValue, valueContext,Hop.SPECULATIVE);
       }

        // handle codebase/resources
        if (resources == null)
            return;

        Iterator<String> iter = resources.iterator();
        UURI codebaseURI = null;
        String res = null;
        try {
            if (codebase != null) {
                // TODO: Pass in the charset.
                codebaseURI = UURIFactory.getInstance(curi.getUURI(), codebase);
            }
            while (iter.hasNext()) {
                res = iter.next();
                res = StringEscapeUtils.unescapeHtml(res);
                if (codebaseURI != null) {
                    res = codebaseURI.resolve(res).toString();
                }
                processEmbed(curi, res, element); // TODO: include attribute
                                                    // too
            }
        } catch (URIException e) {
            curi.getNonFatalFailures().add(e);
//            curi.addLocalizedError(getName(), e, "BAD CODEBASE " + codebase);
        } catch (IllegalArgumentException e) {
            DevUtils.logger.log(Level.WARNING, "processGeneralTag()\n"
                    + "codebase=" + codebase + " res=" + res + "\n"
                    + DevUtils.extraInfo(), e);
        }
    }

    protected boolean processMeta(CrawlURI curi, Element element) {
        String name = element.getAttributeValue("name");
        String httpEquiv = element.getAttributeValue("http-equiv");
        String content = element.getAttributeValue("content");

        if ("robots".equals(name) && content != null) {
            curi.getData().put(A_META_ROBOTS, content);
            RobotsPolicy policy = metadata.getRobotsPolicy();
            String contentLower = content.toLowerCase();
            if (policy.obeyMetaRobotsNofollow()
                 && (contentLower.indexOf("nofollow") >= 0 
                 || contentLower.indexOf("none") >= 0)) {
                // if 'nofollow' or 'none' is specified and the
                // honoring policy is not IGNORE or CUSTOM, end html extraction
                logger.fine("HTML extraction skipped due to robots meta-tag " +
                    "for: " + curi.toString());
                return true;
            }
        }
        if ("refresh".equals(httpEquiv) && content != null) {
            String refreshUri = content.substring(content.indexOf("=") + 1);
            try {
                int max = getExtractorParameters().getMaxOutlinks();
                Link.addRelativeToBase(curi, max, refreshUri, 
                        HTMLLinkContext.META, Hop.REFER);
            } catch (URIException e) {
                logUriError(e, curi.getUURI(), refreshUri);
            }
        }
        return false;
    }

    protected void processScript(CrawlURI curi, Element element) {
        // first, get attributes of script-open tag
        // as per any other tag
        processGeneralTag(curi, element, element.getAttributes());

        // then, apply best-effort string-analysis heuristics
        // against any code present (false positives are OK)
        processScriptCode(curi, element.getContent());

    }

    protected void processStyle(CrawlURI curi, Element element) {
        // First, get attributes of script-open tag as per any other tag.
        processGeneralTag(curi, element, element.getAttributes());

        // then, parse for URIs
        numberOfLinksExtracted.addAndGet(ExtractorCSS.processStyleCode(
                this, curi, element.getContent()));
    }

    protected void processForm(CrawlURI curi, Element element) {
        String action = element.getAttributeValue("action");
        String name = element.getAttributeValue("name");
        String queryURL = "";

        final boolean ignoreFormActions = getIgnoreFormActionUrls();

        if (ignoreFormActions) {
            return;
        }

        // method-sensitive extraction
        String method = StringUtils.defaultIfEmpty(
                element.getAttributeValue("method"), "GET");
        if(getExtractOnlyFormGets() 
                 && ! "GET".equalsIgnoreCase(method)) {
             return;
        }
        numberOfFormsProcessed.incrementAndGet();

        // get all form fields
        for (FormField formField : (Iterable<FormField>)element.findFormFields()) {
            // for each form control
            for (FormControl formControl : (Iterable<FormControl>)formField.getFormControls()) {
                // get name of control element (and URLEncode it)
                String controlName = formControl.getName();

                // retrieve list of values - submit needs special handling
                Collection<String> controlValues;
                if (!(formControl.getFormControlType() ==
                        FormControlType.SUBMIT)) {
                    controlValues = formControl.getValues();
                } else {
                    controlValues = formControl.getPredefinedValues();
                }

                if (controlValues.size() > 0) {
                    // for each value set
                    for (String value : controlValues) {
                        queryURL += "&" + controlName + "=" + value;
                    }
                } else {
                    queryURL += "&" + controlName + "=";
                }
            }
        }

        // clean up url
        if (action == null) {
            queryURL = queryURL.replaceFirst("&", "?");
        } else {
            if (!action.contains("?"))
                queryURL = queryURL.replaceFirst("&", "?");
            queryURL = action + queryURL;
        }

        CharSequence context = elementContext(element.getName(),
            "name=" + name);
        processLink(curi, queryURL, context);

    }

    /**
     * Run extractor. This method is package visible to ease testing.
     * 
     * @param curi
     *            CrawlURI we're processing.
     * @param cs
     *            Sequence from underlying ReplayCharSequence.
     */
    protected void extract(CrawlURI curi, CharSequence cs) {
        Source source = new Source(cs);
        List<Element> elements = source.findAllElements(StartTagType.NORMAL);
        for (Element element : elements) {
            String elementName = element.getName();
            Attributes attributes;
            if (elementName.equals(HTMLElementName.META)) {
                if (processMeta(curi, element)) {
                    // meta tag included NOFOLLOW; abort processing
                    break;
                }
            } else if (elementName.equals(HTMLElementName.SCRIPT)) {
                processScript(curi, element);
            } else if (elementName.equals(HTMLElementName.STYLE)) {
                processStyle(curi, element);
            } else if (elementName.equals(HTMLElementName.FORM)) {
                processForm(curi, element);
            } else if (!(attributes = element.getAttributes()).isEmpty()) {
                processGeneralTag(curi, element, attributes);
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.archive.crawler.framework.Processor#report()
     */
    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append(super.report());
        ret.append("  " + this.numberOfFormsProcessed + " forms processed\n");
        return ret.toString();
    }
}
