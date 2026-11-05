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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;

public class ExtractorSVGTest extends ContentExtractorTestBase {

    private static final String SVG_START = "<svg xmlns='http://www.w3.org/2000/svg' " +
            "xmlns:xlink='http://www.w3.org/1999/xlink'>";

    @Override
    protected Extractor makeExtractor() {
        ExtractorSVG extractor = new ExtractorSVG();
        extractor.setLoggerModule(new UnitTestUriLoggerModule());
        extractor.setExtractorJS(new ExtractorJS());

        ExtractorHTML extractorHTML = new ExtractorHTML();
        extractorHTML.setLoggerModule(new UnitTestUriLoggerModule());
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        extractorHTML.setMetadata(metadata);
        extractorHTML.setExtractorJS(new ExtractorJS());
        extractorHTML.afterPropertiesSet();
        extractor.setExtractorHTML(extractorHTML);
        return extractor;
    }

    @Test
    public void testHrefs() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/a.png image/@href",
                        "E https://example.org/b.svg use/@xlink:href",
                        "E https://example.org/c.png feimage/@href",
                        "E https://example.org/d.js script/@href",
                        "E https://example.org/e.svg lineargradient/@xlink:href",
                        "E https://example.org/f.svg pattern/@href",
                        "E https://example.org/g.svg filter/@href",
                        "E https://example.org/h.svg textpath/@href",
                        "E https://example.org/i.svg mpath/@href",
                        "E https://example.org/j.svg font-face-uri/@xlink:href",
                        "E https://example.org/k.mp3 audio/@xlink:href",
                        "E https://example.org/l.mp4 video/@xlink:href",
                        "L https://example.org/page.html a/@href",
                        "L https://example.org/page2.html a/@xlink:href"),
                extract(SVG_START +
                        "<image href='a.png'/>" +
                        "<use xlink:href='b.svg#icon'/>" +
                        "<filter><feImage href='c.png'/></filter>" +
                        "<script href='d.js'/>" +
                        "<linearGradient xlink:href='e.svg#grad'/>" +
                        "<pattern href='f.svg#p'/>" +
                        "<filter href='g.svg#f'/>" +
                        "<text><textPath href='h.svg#path'>x</textPath></text>" +
                        "<animateMotion><mpath href='i.svg#path'/></animateMotion>" +
                        "<font-face><font-face-src><font-face-uri xlink:href='j.svg#font'/>" +
                        "</font-face-src></font-face>" +
                        "<audio xlink:href='k.mp3'/><video xlink:href='l.mp4'/>" +
                        "<a href='page.html'><text>x</text></a>" +
                        "<a xlink:href='page2.html'><text>y</text></a>" +
                        "</svg>"));
    }

    @Test
    public void testSameDocumentReferencesIgnored() throws Exception {
        assertEquals(List.of(),
                extract(SVG_START +
                        "<linearGradient id='g'/>" +
                        "<use href='#icon'/>" +
                        "<rect fill='url(#g)' style='stroke: url(#g)'/>" +
                        "<a href='#top'><text>x</text></a>" +
                        "</svg>"));
    }

    @Test
    public void testObeyRelNofollow() throws Exception {
        String svg = SVG_START +
                "<a href='followed.html' rel='noopener'><text>x</text></a>" +
                "<a href='nofollow.html' rel='author NoFollow'><text>y</text></a>" +
                "<image rel='nofollow' href='image.png'/>" +
                "</svg>";
        assertEquals(List.of(
                        "E https://example.org/image.png image/@href",
                        "L https://example.org/followed.html a/@href",
                        "L https://example.org/nofollow.html a/@href"),
                extract(svg));

        ((ExtractorSVG) extractor).getExtractorHTML().setObeyRelNofollow(true);
        assertEquals(List.of(
                        "E https://example.org/image.png image/@href",
                        "L https://example.org/followed.html a/@href"),
                extract(svg));
    }

    @Test
    public void testHrefOnOtherElementsIgnored() throws Exception {
        // href isn't an attribute of <rect>, and other namespaces aren't xlink
        assertEquals(List.of(),
                extract(SVG_START + "<rect href='a.png' xmlns:x='urn:x' x:href='b.png'/></svg>"));
    }

    @Test
    public void testXmlStylesheet() throws Exception {
        assertEquals(List.of("E https://example.org/style.css?a=1&b=2 ?xml-stylesheet/@href"),
                extract("<?xml version='1.0'?>" +
                        "<?xml-stylesheet type='text/css' href='style.css?a=1&amp;b=2'?>" +
                        SVG_START + "</svg>"));
    }

    @Test
    public void testCss() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/clip.svg " + LinkContext.EMBED_MISC,
                        "E https://example.org/fill.svg " + LinkContext.EMBED_MISC,
                        "E https://example.org/font.woff " + LinkContext.EMBED_MISC,
                        "E https://example.org/marker.svg " + LinkContext.EMBED_MISC,
                        "E https://example.org/style.svg " + LinkContext.EMBED_MISC),
                extract(SVG_START +
                        "<style>@font-face { src: url(font.woff) }</style>" +
                        "<rect fill='url(fill.svg#g) red' clip-path='url(\"clip.svg#c\")' " +
                        "  marker-start='URL(marker.svg#m)' style='stroke: url(style.svg#s)'/>" +
                        "<rect width='url(not-a-presentation-attribute.svg)'/>" +
                        "</svg>"));
    }

    @Test
    public void testCdataStyleAndScript() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/bg.png " + LinkContext.EMBED_MISC,
                        "X https://example.org/script.html " + LinkContext.JS_MISC),
                extract(SVG_START +
                        "<style><![CDATA[ rect { fill: url(bg.png) } ]]></style>" +
                        "<script><![CDATA[ if (a < b) location = '/script.html'; ]]></script>" +
                        "</svg>"));
    }

    @Test
    public void testAnimateHref() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/1.png animate/@values",
                        "E https://example.org/2.png animate/@values",
                        "E https://example.org/3.png set/@to",
                        "E https://example.org/4.png animate/@from",
                        "E https://example.org/5.png animate/@to"),
                extract(SVG_START +
                        "<image href='#none'>" +
                        "<animate attributeName='href' values='1.png; 2.png;#x' dur='1s'/>" +
                        "<set attributeName='xlink:href' to='3.png'/>" +
                        "<animate attributeName='href' from='4.png' to='5.png'/>" +
                        "<animate attributeName='x' from='6.png' to='7.png'/>" +
                        "</image></svg>"));
    }

    @Test
    public void testForeignObject() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/after.png image/@href",
                        "E https://example.org/inner.png img/@src",
                        "E https://example.org/nested.png image/@href",
                        "L https://example.org/inner.html a/@href"),
                extract(SVG_START +
                        "<foreignObject width='100' height='100'>" +
                        "<div xmlns='http://www.w3.org/1999/xhtml'>" +
                        "<img src='inner.png' alt='a &lt; b'/><a href='inner.html'>x</a>" +
                        "<svg xmlns='http://www.w3.org/2000/svg'><image href='nested.png'/></svg>" +
                        "</div></foreignObject>" +
                        "<image href='after.png'/>" +
                        "</svg>"));
    }

    @Test
    public void testForeignObjectWithoutExtractorHTML() throws Exception {
        ((ExtractorSVG) extractor).setExtractorHTML(null);
        assertEquals(List.of(
                        "E https://example.org/after.png image/@href",
                        "E https://example.org/nested.png image/@href"),
                extract(SVG_START +
                        "<foreignObject><div xmlns='http://www.w3.org/1999/xhtml'>" +
                        "<img src='inner.png'/>" +
                        "<svg xmlns='http://www.w3.org/2000/svg'><image href='nested.png'/></svg>" +
                        "</div></foreignObject>" +
                        "<image href='after.png'/>" +
                        "</svg>"));
    }

    @Test
    public void testNonSvgNamespaceIgnored() throws Exception {
        assertEquals(List.of("E https://example.org/b.png image/@href"),
                extract(SVG_START + "<x:image xmlns:x='urn:x' href='a.png'/>" +
                        "<image href='b.png'/></svg>"));
    }

    @Test
    public void testEntitiesFromInternalSubset() throws Exception {
        // as written by Adobe Illustrator
        assertEquals(List.of("E https://example.org/a.png image/@xlink:href"),
                extract("<?xml version='1.0' encoding='utf-8'?>" +
                        "<!DOCTYPE svg PUBLIC '-//W3C//DTD SVG 1.1//EN' " +
                        "'http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd' [" +
                        "<!ENTITY ns_svg 'http://www.w3.org/2000/svg'>" +
                        "<!ENTITY ns_xlink 'http://www.w3.org/1999/xlink'>" +
                        "]><svg xmlns='&ns_svg;' xmlns:xlink='&ns_xlink;'>" +
                        "<image xlink:href='a.png'/></svg>"));
    }

    @Test
    public void testExternalEntityNotLoaded() throws Exception {
        assertEquals(List.of("E https://example.org/a.png image/@href"),
                extract("<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" +
                        SVG_START + "<text>&x;</text><image href='a.png'/></svg>"));
    }

    @Test
    public void testMalformedKeepsEarlierLinks() throws Exception {
        CrawlURI curi = makeCrawlURI(SVG_START + "<image href='a.png'/><oops attr=unquoted/>" +
                "<image href='b.png'/></svg>", "image/svg+xml");
        extractor.process(curi);
        assertEquals(List.of("E https://example.org/a.png image/@href"), links(curi));
        assertTrue(curi.hasBeenLinkExtracted());
        assertEquals(1, curi.getNonFatalFailures().size());
    }

    @Test
    public void testMaxSizeToParse() throws Exception {
        String start = SVG_START + "<image href='a.png'/>";
        ((ExtractorSVG) extractor).setMaxSizeToParse(start.length() + 10);
        CrawlURI curi = makeCrawlURI(start + "<image href='b.png'/></svg>", "image/svg+xml");
        extractor.process(curi);
        assertEquals(List.of("E https://example.org/a.png image/@href"), links(curi));
        assertTrue(curi.hasBeenLinkExtracted());
        assertTrue(curi.getNonFatalFailures().isEmpty(), curi.getNonFatalFailures().toString());
    }

    @Test
    public void testCharsetFromContentType() throws Exception {
        String svg = "<?xml version='1.0' encoding='iso-8859-1'?>" + SVG_START +
                "<image href='é.png'/></svg>";
        CrawlURI curi = makeCrawlURI(svg, "image/svg+xml; charset=UTF-8");
        extractor.process(curi);
        assertEquals(List.of("E https://example.org/%C3%A9.png image/@href"), links(curi));
    }

    @Test
    public void testShouldExtract() throws Exception {
        ExtractorSVG svg = (ExtractorSVG) extractor;
        String content = SVG_START + "</svg>";
        assertTrue(svg.shouldExtract(makeCrawlURI(content, "image/svg+xml")));
        assertTrue(svg.shouldExtract(makeCrawlURI(content, "IMAGE/SVG+XML; charset=utf-8")));
        assertTrue(svg.shouldExtract(makeCrawlURI(content, "text/xml")));
        assertTrue(svg.shouldExtract(makeCrawlURI(content, "application/xml")));
        assertFalse(svg.shouldExtract(makeCrawlURI(content, "text/html")));
    }

    @Test
    public void testGenericXmlWithSvgRoot() throws Exception {
        CrawlURI curi = makeCrawlURI("https://example.org/icon?id=3",
                SVG_START + "<image href='a.png'/></svg>", "text/xml");
        extractor.process(curi);
        assertEquals(List.of("E https://example.org/a.png image/@href"), links(curi));
        assertTrue(curi.hasBeenLinkExtracted());
    }

    @Test
    public void testNonSvgRootLeftToOtherExtractors() throws Exception {
        String[][] documents = {
                {"application/xml", "<?xml-stylesheet href='feed.css'?>" +
                        "<rss><channel><link>https://example.org/post</link></channel></rss>"},
                {"text/xml", "<x:svg xmlns:x='urn:x'><image href='a.png'/></x:svg>"},
                {"image/svg+xml", "<svg><image href='a.png'/></svg>"}, // no namespace
                {"image/svg+xml", "not xml"},
        };
        for (String[] document : documents) {
            CrawlURI curi = makeCrawlURI(document[1], document[0]);
            extractor.process(curi);
            assertEquals(List.of(), links(curi), document[1]);
            assertFalse(curi.hasBeenLinkExtracted(), document[1]);
            assertTrue(curi.getNonFatalFailures().isEmpty(), document[1]);
        }
    }

    private List<String> extract(String svg) throws Exception {
        CrawlURI curi = makeCrawlURI(svg, "image/svg+xml");
        extractor.process(curi);
        assertTrue(curi.getNonFatalFailures().isEmpty(), curi.getNonFatalFailures().toString());
        return links(curi);
    }

    private CrawlURI makeCrawlURI(String content, String contentType) throws Exception {
        return makeCrawlURI("https://example.org/image.svg", content, contentType);
    }

    private CrawlURI makeCrawlURI(String uri, String content, String contentType) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(uri));
        curi.setContentType(contentType);
        curi.setRecorder(createRecorder(content, "UTF-8"));
        curi.setContentSize(content.getBytes(StandardCharsets.UTF_8).length);
        curi.setFetchStatus(200);
        return curi;
    }

    /**
     * Outlinks formatted as "hop uri context", sorted.
     */
    private static List<String> links(CrawlURI curi) {
        List<String> links = new ArrayList<>();
        for (CrawlURI link : curi.getOutLinks()) {
            links.add(link.getLastHop() + " " + link.getURI() + " " + link.getViaContext());
        }
        Collections.sort(links);
        return links;
    }
}
