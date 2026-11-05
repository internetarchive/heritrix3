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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.lang3.StringEscapeUtils;
import org.archive.modules.CrawlURI;
import org.archive.url.URIException;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Extracts links from SVG images.
 *
 * <p>Handles responses with the content type {@code image/svg+xml}, and also {@code text/xml}
 * and {@code application/xml} responses that turn out to be SVG. Other XML documents are left
 * for later extractors, so this extractor should come before any other extractor that handles
 * XML, such as ExtractorXML.
 *
 * <p>Images, stylesheets, scripts, fonts and other resources the SVG loads are extracted as
 * embedded links, and {@code <a>} links as navigation links. Links in inline CSS are extracted
 * too. Inline scripts and HTML embedded in the SVG are only processed if the crawl has an
 * ExtractorJS and an ExtractorHTML bean, respectively, and use their settings.
 *
 * <p>As in browsers, nothing is extracted after an XML syntax error, or from a document without
 * the SVG namespace declaration. Only the first {@code maxSizeToParse} bytes are read.
 */
public class ExtractorSVG extends ContentExtractor {

    private static final Logger logger = Logger.getLogger(ExtractorSVG.class.getName());

    static final String SVG_NS = "http://www.w3.org/2000/svg";
    static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    /**
     * Elements (lower case) whose {@code href} references a resource that's loaded as part of
     * rendering the document, rather than navigated to.
     */
    static final Set<String> EMBED_HREF_ELEMENTS = Set.of(
            "audio", "cursor", "feimage", "filter", "font-face-uri", "image", "lineargradient",
            "mpath", "pattern", "radialgradient", "script", "textpath", "tref", "use",
            "video"); // audio and video are from SVG Tiny 1.2, which browsers don't implement

    /**
     * Presentation attributes (lower case) that can take a CSS {@code url(...)} value.
     */
    static final Set<String> URL_PRESENTATION_ATTRIBUTES = Set.of(
            "clip-path", "cursor", "fill", "filter", "marker-end", "marker-mid", "marker-start",
            "mask", "stroke");

    private static final Pattern PSEUDO_ATTRIBUTE_HREF =
            Pattern.compile("(?:^|\\s)href\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    private static final XMLInputFactory INPUT_FACTORY = newInputFactory();
    private static final XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newFactory();

    private static XMLInputFactory newInputFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true);
        // the internal DTD subset is still processed, as some editors (e.g. Adobe Illustrator)
        // declare entities there for the namespace URIs
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        // never fetch the external DTD subset
        factory.setXMLResolver((publicId, systemId, baseUri, namespace) ->
                new ByteArrayInputStream(new byte[0]));
        return factory;
    }

    {
        setMaxSizeToParse(10 * 1024 * 1024L); // 10MB
    }
    public long getMaxSizeToParse() {
        return (Long) kp.get("maxSizeToParse");
    }
    /**
     * The maximum number of bytes of an SVG document to parse, after any content encoding such
     * as gzip is decoded. Links after this point are not extracted.
     */
    public void setMaxSizeToParse(long maxSizeToParse) {
        kp.put("maxSizeToParse", maxSizeToParse);
    }

    /**
     * Javascript extractor to use to process inline scripts. Autowired if available. If null,
     * links will not be extracted from inline scripts.
     */
    transient protected ExtractorJS extractorJS;
    public ExtractorJS getExtractorJS() {
        return extractorJS;
    }
    @Autowired(required = false)
    public void setExtractorJS(ExtractorJS extractorJS) {
        this.extractorJS = extractorJS;
    }

    /**
     * HTML extractor to use to process the content of foreignObject elements. Autowired if
     * available. If null, links will not be extracted from foreignObject elements.
     */
    transient protected ExtractorHTML extractorHTML;
    public ExtractorHTML getExtractorHTML() {
        return extractorHTML;
    }
    @Autowired(required = false)
    public void setExtractorHTML(ExtractorHTML extractorHTML) {
        this.extractorHTML = extractorHTML;
    }

    @Override
    protected boolean shouldExtract(CrawlURI curi) {
        // like browsers, also accept SVG served as generic XML; whether it's actually SVG is
        // decided by the root element
        return curi.hasContentType("image/svg+xml")
                || curi.hasContentType("text/xml")
                || curi.hasContentType("application/xml");
    }

    @Override
    protected boolean innerExtract(CrawlURI curi) {
        boolean isSvg = false;
        try (BoundedInputStream in = BoundedInputStream.builder()
                .setInputStream(curi.getRecorder().getContentReplayInputStream())
                .setMaxCount(getMaxSizeToParse())
                .get()) {
            try {
                XMLStreamReader reader;
                if (curi.containsContentTypeCharsetDeclaration()) {
                    // as in browsers, the charset from the HTTP headers wins
                    reader = INPUT_FACTORY.createXMLStreamReader(in,
                            curi.getRecorder().getCharset().name());
                } else {
                    reader = INPUT_FACTORY.createXMLStreamReader(in);
                }
                try {
                    isSvg = processRootElement(curi, reader);
                    if (isSvg) {
                        extract(curi, reader);
                    }
                } finally {
                    reader.close();
                }
            } catch (XMLStreamException e) {
                if (!isSvg) {
                    // not well-formed up to the root element, so leave it to other extractors
                    return false;
                }
                // like browsers, keep whatever came before the error
                if (in.getRemaining() == 0) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Stopped parsing SVG at maxSizeToParse: " + curi);
                    }
                } else {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Error parsing SVG " + curi + ": " + e.getMessage());
                    }
                    curi.getNonFatalFailures().add(e);
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed reading content of " + curi, e);
            curi.getNonFatalFailures().add(e);
            return false;
        }
        // if it's not SVG, leave it to other extractors
        return isSvg;
    }

    /**
     * Reads up to the root element. If it's an SVG {@code <svg>} element, extracts links from it
     * and from any {@code <?xml-stylesheet?>} processing instructions before it.
     *
     * @return false if the document isn't SVG
     */
    protected boolean processRootElement(CrawlURI curi, XMLStreamReader reader)
            throws XMLStreamException {
        List<String> stylesheets = new ArrayList<>();
        while (reader.hasNext()) {
            int event = reader.next();
            if (event == XMLStreamConstants.PROCESSING_INSTRUCTION
                    && "xml-stylesheet".equals(reader.getPITarget())) {
                stylesheets.add(reader.getPIData());
            } else if (event == XMLStreamConstants.START_ELEMENT) {
                if (!SVG_NS.equals(reader.getNamespaceURI())
                        || !reader.getLocalName().equals("svg")) {
                    return false;
                }
                for (String data : stylesheets) {
                    processXmlStylesheet(curi, data);
                }
                processAttributes(curi, reader, "svg");
                return true;
            }
        }
        return false;
    }

    protected void extract(CrawlURI curi, XMLStreamReader reader) throws XMLStreamException {
        StringBuilder text = null; // content of the current <style> or <script>
        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    if (!SVG_NS.equals(reader.getNamespaceURI())) {
                        break;
                    }
                    String element = reader.getLocalName();
                    if (element.equals("foreignObject")) {
                        processForeignObject(curi, reader);
                        break;
                    }
                    processAttributes(curi, reader, element);
                    if (element.equals("style") || element.equals("script")) {
                        text = new StringBuilder();
                    }
                    break;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    if (text != null) {
                        text.append(reader.getText());
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    if (text != null && SVG_NS.equals(reader.getNamespaceURI())) {
                        if (reader.getLocalName().equals("style")) {
                            numberOfLinksExtracted.addAndGet(
                                    ExtractorCSS.processStyleCode(this, curi, text));
                        } else if (reader.getLocalName().equals("script")) {
                            processScriptCode(curi, text);
                        }
                        text = null;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    protected void processAttributes(CrawlURI curi, XMLStreamReader reader, String element) {
        String elementLC = element.toLowerCase(Locale.ROOT);
        boolean animatesHref = false;
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String namespace = reader.getAttributeNamespace(i);
            String name = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            boolean noNamespace = namespace == null || namespace.isEmpty();
            if (name.equals("href") && (noNamespace || namespace.equals(XLINK_NS))) {
                String context = noNamespace ? "href" : "xlink:href";
                if (elementLC.equals("a")) {
                    addLink(curi, value, elementLC, context, Hop.NAVLINK);
                } else if (EMBED_HREF_ELEMENTS.contains(elementLC)) {
                    addLink(curi, value, elementLC, context, Hop.EMBED);
                }
            } else if (!noNamespace) {
                // other namespaced attributes aren't of interest
            } else if (name.equals("style") || URL_PRESENTATION_ATTRIBUTES.contains(name)) {
                numberOfLinksExtracted.addAndGet(ExtractorCSS.processStyleCode(this, curi, value));
            } else if (name.equals("attributeName")
                    && (value.trim().equals("href") || value.trim().equals("xlink:href"))) {
                animatesHref = true;
            }
        }

        if (animatesHref && (elementLC.equals("animate") || elementLC.equals("set"))) {
            for (String attribute : new String[] {"from", "to", "values"}) {
                String value = reader.getAttributeValue(null, attribute);
                if (value == null) {
                    continue;
                }
                for (String uri : value.split(";")) {
                    addLink(curi, uri, elementLC, attribute, Hop.EMBED);
                }
            }
        }
    }

    protected void processXmlStylesheet(CrawlURI curi, String data) {
        if (data == null) {
            return;
        }
        Matcher matcher = PSEUDO_ATTRIBUTE_HREF.matcher(data);
        if (matcher.find()) {
            String href = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            addLink(curi, StringEscapeUtils.unescapeXml(href), "?xml-stylesheet", "href",
                    Hop.EMBED);
        }
    }

    /**
     * Serializes the content of a foreignObject element and extracts links from it as HTML.
     * Leaves the reader positioned at the element's end tag. Without an HTML extractor, the
     * content is left for the caller, which ignores elements outside the SVG namespace.
     */
    protected void processForeignObject(CrawlURI curi, XMLStreamReader reader)
            throws XMLStreamException {
        ExtractorHTML html = getExtractorHTML();
        if (html == null) {
            return;
        }
        StringWriter buffer = new StringWriter();
        XMLStreamWriter writer = OUTPUT_FACTORY.createXMLStreamWriter(buffer);
        int depth = 1;
        while (depth > 0) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    depth++;
                    writer.writeStartElement(
                            qualifiedName(reader.getPrefix(), reader.getLocalName()));
                    for (int i = 0; i < reader.getAttributeCount(); i++) {
                        writer.writeAttribute(
                                qualifiedName(reader.getAttributePrefix(i),
                                        reader.getAttributeLocalName(i)),
                                reader.getAttributeValue(i));
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    depth--;
                    if (depth > 0) {
                        writer.writeEndElement();
                    }
                    break;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.SPACE:
                    writer.writeCharacters(reader.getText());
                    break;
                default:
                    break;
            }
        }
        writer.close();
        html.processSrcdoc(curi, buffer.toString());
    }

    private static String qualifiedName(String prefix, String localName) {
        return prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
    }

    protected void processScriptCode(CrawlURI curi, CharSequence code) {
        if (getExtractorJS() != null) {
            numberOfLinksExtracted.addAndGet(getExtractorJS().considerStrings(this, curi, code));
        }
    }

    protected void addLink(CrawlURI curi, String uri, String element, String attribute, Hop hop) {
        uri = uri.trim();
        if (uri.isEmpty() || uri.startsWith("#")) {
            // a reference to an element in this same document
            return;
        }
        try {
            HTMLLinkContext context = HTMLLinkContext.get(
                    ExtractorHTML.elementContext(element, attribute).toString());
            addRelativeToBase(curi, getExtractorParameters().getMaxOutlinks(), uri, context, hop);
            numberOfLinksExtracted.incrementAndGet();
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), uri);
        }
    }
}
