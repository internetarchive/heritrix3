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

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.archive.url.URIException;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtractorHTMLTest extends StringExtractorTestBase {

    
    final public static String[] VALID_TEST_DATA = new String[] {
        "<a href=\"http://www.slashdot.org\">yellow journalism</a> A",
        "http://www.slashdot.org",

        "<a href='http://www.slashdot.org'>yellow journalism</a> A",
        "http://www.slashdot.org",

        "<a href=http://www.slashdot.org>yellow journalism</a> A",
        "http://www.slashdot.org",

        "<a href=\"http://www.slashdot.org\">yellow journalism A",
        "http://www.slashdot.org",

        "<a href='http://www.slashdot.org'>yellow journalism A",
        "http://www.slashdot.org",

        "<a href=http://www.slashdot.org>yellow journalism A",
        "http://www.slashdot.org",

        "<a href=\"http://www.slashdot.org\"/>yellow journalism A",
        "http://www.slashdot.org",

        "<a href='http://www.slashdot.org'/>yellow journalism A",
        "http://www.slashdot.org",

        "<a href=http://www.slashdot.org/>yellow journalism A",
        "http://www.slashdot.org",

        "<img src=\"foo.gif\"> IMG",
        "http://www.archive.org/start/foo.gif",
       
    };
    
    @Override
    protected String[] getValidTestData() {
        return VALID_TEST_DATA;
    }

    @Override
    protected Extractor makeExtractor() {
        ExtractorHTML result = new ExtractorHTML();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        result.setLoggerModule(ulm);
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        result.setMetadata(metadata);
        result.setExtractorJS(new ExtractorJS());
        result.afterPropertiesSet();
        return result;
    }
    
    protected ExtractorHTML getExtractor() {
        return (ExtractorHTML) extractor;
    }
    
    @Override
    protected Collection<TestData> makeData(String content, String destURI)
    throws Exception {
        List<TestData> result = new ArrayList<TestData>();
        UURI src = UURIFactory.getInstance("http://www.archive.org/start/");
        CrawlURI euri = new CrawlURI(src, null, null, 
                LinkContext.NAVLINK_MISC);
        Recorder recorder = createRecorder(content, "UTF-8");
        euri.setContentType("text/html");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
                
        UURI dest = UURIFactory.getInstance(destURI);
        LinkContext context = determineContext(content);
        Hop hop = determineHop(content);
        CrawlURI link = euri.createCrawlURI(dest, context, hop);
        result.add(new TestData(euri, link));
        
        euri = new CrawlURI(src, null, null, LinkContext.NAVLINK_MISC);
        recorder = createRecorder(content, "UTF-8");
        euri.setContentType("application/xhtml");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
        result.add(new TestData(euri, link));
        
        return result;
    }

    
    private static Hop determineHop(String s) {
        if (s.endsWith(" IMG")) {
            return Hop.EMBED;
        }
        return Hop.NAVLINK;
    }
    
    
    private static LinkContext determineContext(String s) {
        if (s.endsWith(" A")) {
            return HTMLLinkContext.get("a/@href");
        }
        if (s.endsWith(" IMG")) {
            return HTMLLinkContext.get("img/@src");
        }
        return LinkContext.NAVLINK_MISC;
    }

    /**
     * Test a missing whitespace issue found in form
     * 
     * [HER-1128] ExtractorHTML fails to extract FRAME SRC link without
     * whitespace before SRC http://webteam.archive.org/jira/browse/HER-1128
     */
    @Test
    public void testNoWhitespaceBeforeValidAttribute() throws URIException {
        expectSingleLink(
                "http://expected.example.com/",
                "<frame name=\"main\"src=\"http://expected.example.com/\"> ");
    }
    
    /**
     * Expect the extractor to find the single given URI in the supplied
     * source material. Fail if that one lik is not found. 
     * 
     * TODO: expand to capture expected Link instance characteristics 
     * (source, hop, context, etc?)
     * 
     * @param expected String target URI that should be extracted
     * @param source CharSequence source material to extract
     * @throws URIException
     */
    protected void expectSingleLink(String expected, CharSequence source) throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        getExtractor().extract(puri, source);
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        assertTrue(links.length==1, "did not find single link");
        assertTrue(links[0].getURI().equals(expected), "expected link not found");
    }
    
    /**
     * Test only extract FORM ACTIONS with METHOD GET 
     * 
     * [HER-1280] do not by default GET form action URLs declared as POST, 
     * because it can cause problems/complaints 
     * http://webteam.archive.org/jira/browse/HER-1280
     */
    @Test
    public void testOnlyExtractFormGets() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
            "<form method=\"get\" action=\"http://www.example.com/ok1\"> "+
            "<form action=\"http://www.example.com/ok2\" method=\"get\"> "+
            "<form method=\"post\" action=\"http://www.example.com/notok\"> "+
            "<form action=\"http://www.example.com/ok3\"> ";
        getExtractor().extract(puri, cs);
        // find exactly 3 (not the POST) action URIs
        assertEquals(3, puri.getOutLinks().size(), "incorrect number of links found");
    }

    /*
     * positive and negative tests for uris in meta tag's content attribute
     */
    @Test
    public void testMetaContentURI() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
                "<meta property=\"og:video\" content=\"http://www.example.com/absolute.mp4\" /> "+
                "<meta property=\"og:video\" content=\"/relative.mp4\" /> "+
                "<meta property=\"og:video:height\" content=\"333\" />"+
                "<meta property=\"og:video:type\" content=\"video/mp4\" />"+
                "<meta property=\"strangeproperty\" content=\"notaurl\" meaninglessurl=\"http://www.example.com/shouldnotbeextracted.html\" />";
        
        getExtractor().extract(puri, cs);
        
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);         
        String dest1 = "http://www.example.com/absolute.mp4";
        String dest2 = "http://www.example.com/relative.mp4";

        assertEquals(2, puri.getOutLinks().size(), "incorrect number of links found");
        assertEquals(dest1, links[0].getURI(), "expected uri in 'content' attribute of meta tag not found");
        assertEquals(dest2, links[1].getURI(), "expected uri in 'content' attribute of meta tag not found");
    }
    
    /**
     * Test detection, respect of meta robots nofollow directive
     */
    @Test
    public void testMetaRobots() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
            "Blah Blah "+
            "<meta name='robots' content='index,nofollow'>"+
            "<a href='blahblah'>blah</a> "+
            "blahblah";
        getExtractor().extract(puri, cs);
        assertEquals("index,nofollow", puri.getData().get(ExtractorHTML.A_META_ROBOTS), "meta robots content not extracted");
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        assertEquals(0, links.length, "link extracted despite meta robots");
    }
    
    /**
     * Test that relative URIs with late colons aren't misinterpreted
     * as absolute URIs with long, illegal scheme components. 
     * 
     * See http://webteam.archive.org/jira/browse/HER-1268
     * 
     * @throws URIException
     */
    @Test
    public void testBadRelativeLinks() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = "<a href=\"example.html;jsessionid=deadbeef:deadbeed?parameter=this:value\"/>"
                + "<a href=\"example.html?parameter=this:value\"/>";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI().contains("/example.html;jsessionid=deadbeef:deadbeed?parameter=this:value");
            }
        }));

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().contains("/example.html?parameter=this:value");
            }
        }));
    }

    @Test
    public void testDataUrisAreIgnored() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com"));
        CharSequence cs = "<img src='data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=='>";
        getExtractor().extract(curi, cs);
        assertEquals(0, curi.getOutLinks().size());
    }
    
    /**
     * Test that relative base href's are resolved correctly:
     */
    @Test
    public void testRelativeBaseHrefRelativeLinks() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.schmid-gartenpflanzen.de/forum/index.php/mv/msg/7627/216142/0/"));
        CharSequence cs = "<base href=\"/forum/\"/>\n" + 
                "<img src=\"index.php/fa/89652/0/\" border=\"0\" alt=\"index.php/fa/89652/0/\" />";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI().contains(".de/forum/index.php/fa/89652/0/");
            }
        }));
    }

    
    /**
     * Test that the first base href is used:
     */
    @Test
    public void testFirstBaseHrefRelativeLinks() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.schmid-gartenpflanzen.de/forum/index.php/mv/msg/7627/216142/0/"));
        CharSequence cs = "<base href=\"/first/\"/>\n" + "<base href=\"/forum/\"/>\n" + 
                "<img src=\"index.php/fa/89652/0/\" border=\"0\" alt=\"index.php/fa/89652/0/\" />";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI().contains(".de/first/index.php/fa/89652/0/");
            }
        }));
    }

    /**
     * Test that absolute base href's are resolved correctly:
     */
    @Test
    public void testAbsoluteBaseHrefRelativeLinks() throws URIException {

        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.schmid-gartenpflanzen.de/forum/index.php/mv/msg/7627/216142/0/"));
        CharSequence cs = "<base href=\"https://www.schmid-gartenpflanzen.de/forum/\"/>\n" + 
                "<img src=\"index.php/fa/89652/0/\" border=\"0\" alt=\"index.php/fa/89652/0/\" />";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object)
                        .getURI().contains(".de/forum/index.php/fa/89652/0/");
            }
        }));

    }
    
    /**
     * Test if scheme is maintained by speculative hops onto exact 
     * same host
     * 
     * [HER-1524] speculativeFixup in ExtractorJS should maintain URL scheme
     */
    @Test
    public void testSpeculativeLinkExtraction() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("https://www.example.com"));
        CharSequence cs = 
            "<script type=\"text/javascript\">_parameter=\"www.anotherexample.com\";"
                + "_anotherparameter=\"www.example.com/index.html\""
                + ";</script>";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                System.err.println("comparing: "
                        + ((CrawlURI) object).getURI()
                        + " and https://www.anotherexample.com/");
                return ((CrawlURI) object).getURI().equals(
                        "http://www.anotherexample.com/");
            }
        }));
        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().equals(
                        "https://www.example.com/index.html");
            }
        }));
    }
    
    
    /**
     * test to see if embedded <SCRIPT/> which writes script TYPE
     * creates any outlinks, e.g. "type='text/javascript'". 
     * 
     * [HER-1526] SCRIPT writing script TYPE common trigger of bogus links 
     *   (eg. 'text/javascript')
     */
    @Test
    public void testScriptTagWritingScriptType() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com/en/fiche/dossier/322/"));
        CharSequence cs = 
            "<script type=\"text/javascript\">"
            + "var gaJsHost = ((\"https:\" == document.location.protocol) "
            + "? \"https://ssl.\" : \"http://www.\");"
            + " document.write(unescape(\"%3Cscript src='\" + gaJsHost + "
            + "\"google-analytics.com/ga.js' "
            + "type='text/javascript'%3E%3C/script%3E\"));"
            + "</script>";
        getExtractor().extract(curi, cs);
        assertEquals(Collections.EMPTY_SET, curi.getOutLinks());
    }

    @Test
    public void testOutLinksWithBaseHref() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com/abc/index.html"));
        CharSequence cs = 
            "<base href=\"http://www.example.com/\">" + 
            "<a href=\"def/another1.html\">" + 
            "<a href=\"ghi/another2.html\">";
        getExtractor().extract(puri, cs);
        CrawlURI[] links = puri.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links); 
        String dest1 = "http://www.example.com/def/another1.html";
        String dest2 = "http://www.example.com/ghi/another2.html";
        // ensure outlink from base href
        assertEquals(dest1, links[1].getURI(), "outlink1 from base href");
        assertEquals(dest2, links[2].getURI(), "outlink2 from base href");
    }

    protected Predicate destinationContainsPredicate(final String fragment) {
        return new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().contains(fragment);
            }
        };
    }
    
    protected Predicate destinationsIsPredicate(final String value) {
        return new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().equals(value);
            }
        };
    }
    
    /**
     * HER-1728 
     */
    @Test
    public void testFlashvarsParamValue() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));
        CharSequence cs = 
            "<object classid=\"clsid:D27CDB6E-AE6D-11cf-96B8-444553540000\" codebase=\"http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,28,0\" id=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "    <param name=\"flashvars\" value=\"zoomifyXMLPath=ParamZoomifySlideshowViewer.xml\">\n" + 
            "    <param name=\"menu\" value=\"false\">\n" + 
            "    <param name=\"bgcolor\" value=\"#000000\">\n" + 
            "    <param name=\"src\" value=\"ZoomifySlideshowViewer.swf\">\n" + 
            "    <embed flashvars=\"zoomifyXMLPath=EmbedZoomifySlideshowViewer.xml\" src=\"ZoomifySlideshowViewer.swf\" menu=\"false\" bgcolor=\"#000000\" pluginspage=\"http://www.adobe.com/go/getflashplayer\" type=\"application/x-shockwave-flash\" name=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "</object> ";
        getExtractor().extract(curi, cs);
        String expected = "http://www.example.com/ParamZoomifySlideshowViewer.xml";
        assertTrue(CollectionUtils.exists(curi.getOutLinks(), destinationsIsPredicate(expected)),
                "outlinks should contain: " + expected);
    }
    
    /**
     * HER-1728 
     */
    @Test
    public void testFlashvarsEmbedAttribute() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));
        CharSequence cs = 
            "<object classid=\"clsid:D27CDB6E-AE6D-11cf-96B8-444553540000\" codebase=\"http://download.macromedia.com/pub/shockwave/cabs/flash/swflash.cab#version=9,0,28,0\" id=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "    <param name=\"flashvars\" value=\"zoomifyXMLPath=ParamZoomifySlideshowViewer.xml\">\n" + 
            "    <param name=\"menu\" value=\"false\">\n" + 
            "    <param name=\"bgcolor\" value=\"#000000\">\n" + 
            "    <param name=\"src\" value=\"ZoomifySlideshowViewer.swf\">\n" + 
            "    <embed flashvars=\"zoomifyXMLPath=EmbedZoomifySlideshowViewer.xml\" src=\"ZoomifySlideshowViewer.swf\" menu=\"false\" bgcolor=\"#000000\" pluginspage=\"http://www.adobe.com/go/getflashplayer\" type=\"application/x-shockwave-flash\" name=\"ZoomifySlideshowViewer\" height=\"372\" width=\"590\">\n" + 
            "</object> ";
        getExtractor().extract(curi, cs);
        String expected = "http://www.example.com/EmbedZoomifySlideshowViewer.xml";
        assertTrue(CollectionUtils.exists(curi.getOutLinks(),destinationsIsPredicate(expected)),
                "outlinks should contain: "+expected);
    }
    
    /**
     * HER-1998 
     */
    @Test
    public  void testConditionalComment1() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));
    
        CharSequence cs = 
            "<!--[if IE 6]><img src=\"foo.gif\"><![endif]-->" +
            "<!--[if IE 6]><script src=\"foo.js\"><![endif]-->";
 
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        getExtractor().setLoggerModule(ulm);
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        getExtractor().setMetadata(metadata);
        getExtractor().afterPropertiesSet();
        
        getExtractor().extract(curi, cs);
        
        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links); 
        
        String dest1 = "http://www.example.com/foo.gif";
        String dest2 = "http://www.example.com/foo.js";

        assertEquals(dest1, links[0].getURI(), "outlink1 from conditional comment img src");
        assertEquals(dest2, links[1].getURI(), "outlink2 from conditional comment script src");
        
    }

    @Test
    public void testImgSrcSetAttribute() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));

        CharSequence cs = "<img width=\"800\" height=\"1200\" src=\"/images/foo.jpg\" "
                + "class=\"attachment-full size-full\" alt=\"\" "
                + "srcset=\"a,b,c,,, /images/foo1.jpg 800w,data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7 700w, /images/foo2.jpg 480w(data:,foo, ,), /images/foo3.jpg 96w(x\" "
                + "sizes=\"(max-width: 800px) 100vw, 800px\">";

        getExtractor().extract(curi, cs);

        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);

        String[] dest = {
                "http://www.example.com/a,b,c",
                "http://www.example.com/images/foo.jpg",
                "http://www.example.com/images/foo1.jpg",
                "http://www.example.com/images/foo2.jpg",
                "http://www.example.com/images/foo3.jpg" };

        for (int i = 0; i < links.length; i++) {
            assertEquals(dest[i], links[i].getURI(), "outlink from img");
        }
    }

    @Test
    public void testSourceSrcSetAttribute() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.example.com/"));

        CharSequence cs = "<picture>"
                + "<source media=\"(min-width: 992px)\" srcset=\"images/foo1.jpg\"> "
                + "<source media=\"(min-width: 500px)\" SRCSET=\"images/foo2.jpg\"> "
                + "<source media=\"(min-width: 0px)\" srcSet=\"images/foo3-1x.jpg 1x, images/foo3-2x.jpg 2x\"> "
                + "<img src=\"images/foo.jpg\" alt=\"\"> "
                + "</picture>";

        getExtractor().extract(curi, cs);

        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);

        String[] dest = {
                "http://www.example.com/images/foo.jpg",
                "http://www.example.com/images/foo1.jpg",
                "http://www.example.com/images/foo2.jpg",
                "http://www.example.com/images/foo3-1x.jpg",
                "http://www.example.com/images/foo3-2x.jpg",
        };

        for (int i = 0; i < links.length; i++) {
            assertEquals(dest[i], links[i].getURI(), "outlink from picture");
        }

    }

    @Test
    public void testDataAttributes20Minutes() throws URIException {
        CrawlURI curi_src = new CrawlURI(UURIFactory.getInstance("https://www.20minutes.fr/"));

        CharSequence cs_src = "<img class=\"b-lazy\" width=\"120\" height=\"78\""
                + "data-src=\"https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg\""
                + "sizes=\"7.5em\" alt=\"Illustration d&#039;un avocat.\"/>";

        String[] dest_src = {
                "https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg"};

        genericCrawl(curi_src, cs_src, dest_src);
        
        CrawlURI curi_srcset = new CrawlURI(UURIFactory.getInstance("https://www.20minutes.fr/"));

        CharSequence cs_srcset = "<img class=\"b-lazy\" width=\"120\" height=\"78\""
                + "data-srcset=\"https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg 120w,https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/240x156_illustration-avocat.jpg 240w\""
                + "sizes=\"7.5em\" alt=\"Illustration d&#039;un avocat.\"/>";

        String[] dest_srcset = {
                "https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg",
        		"https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/240x156_illustration-avocat.jpg"};

        genericCrawl(curi_srcset, cs_srcset, dest_srcset);
        
        CrawlURI curi_srcset_one = new CrawlURI(UURIFactory.getInstance("https://www.20minutes.fr/"));

        CharSequence cs_srcset_one = "<img class=\"b-lazy\" width=\"120\" height=\"78\""
                + "data-srcset=\"https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg 120w\"";

        String[] dest_srcset_one = {
                "https://img.20mn.fr/shn9o66FT2-UHl5dl8D38Q/120x78_illustration-avocat.jpg"};

        genericCrawl(curi_srcset_one, cs_srcset_one, dest_srcset_one);

    }

    @Test
    public void testDataAttributesTelerama() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.telerama.fr/"));

        CharSequence cs = "<img itemprop=\"image\" src=\"https://www.telerama.fr/sites/tr_master/themes/tr/images/trans.gif\" " +
		  "data-original=\"https://www.telerama.fr/sites/tr_master/files/styles/m_640x314/public/standup.jpg?itok=w1aDSzBQsc=1012e84ed57e1b1e6ea74a47ec094242\"/>";

        String[] dest = {
                "https://www.telerama.fr/sites/tr_master/files/styles/m_640x314/public/standup.jpg?itok=w1aDSzBQsc=1012e84ed57e1b1e6ea74a47ec094242",
                "https://www.telerama.fr/sites/tr_master/themes/tr/images/trans.gif"};

        genericCrawl(curi, cs, dest);
        
    }

    @Test
    public void testDataAttributesNouvelObs() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.telerama.fr/"));

        CharSequence cs = "<source media=\"(min-width: 640px)\" data-original-set=\"http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg\"" +
        		"srcset=\"http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg\">";

        String[] dest = {
                "http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg",
                "http://focus.nouvelobs.com/2020/02/07/0/0/640/320/633/306/75/0/59de545_3vVuzAnVb95lp1Hm0OwQ_Jk2.jpeg"};

        genericCrawl(curi, cs, dest);
        
    }

    @Test
    public void testDataAttributesEuronews() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.euronews.com/"));

        CharSequence cs = "<img class=\"m-img lazyload\" src=\"/images/vector/fallback.svg\""+
    		   "data-src=\"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg\""+
    		   "data-srcset=\"https://static.euronews.com/articles/stories/04/54/38/12/100x56_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 100w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/150x84_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 150w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/300x169_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 300w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 400w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/600x338_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 600w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/750x422_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 750w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/1000x563_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 1000w, "+
    		   		"https://static.euronews.com/articles/stories/04/54/38/12/1200x675_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg 1200w\""+
    		   "data-sizes=\"(max-width: 768px) 33vw, (max-width: 1024px) 25vw, (max-width: 1280px) 17vw, 17vw\""+
    		   "title=\"La lutte contre l&#039;épidémie de Covid-19 continue en Europe et dans le monde\"/>";

        String[] dest = {
            	"https://static.euronews.com/articles/stories/04/54/38/12/1000x563_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/100x56_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/1200x675_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
                "https://static.euronews.com/articles/stories/04/54/38/12/150x84_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/300x169_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/400x225_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/600x338_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
            	"https://static.euronews.com/articles/stories/04/54/38/12/750x422_cmsv2_081071de-f9f4-5341-9512-94f5f494f45f-4543812.jpg",
                "https://www.euronews.com/images/vector/fallback.svg"
        };

        genericCrawl(curi, cs, dest);
        
    }  

    @Test
    public void testDataAttributesLeMonde() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.telerama.fr/"));

        CharSequence cs = "<img data-srcset=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg 198w, "+
        			"http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg 114w\" "+
        		"data-src=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg\" "+
        		"srcset=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg 198w, "+
        			"http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg 114w\" "+
        		"src=\"http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg\" >";

        String[] dest = {
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/114/0/95/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg",
                "http://img.lemde.fr/2020/02/29/0/0/4818/3212/384/0/60/0/c7dda5e_5282901-01-06.jpg"
                
        };

        genericCrawl(curi, cs, dest);
        
    }

    @Test
    public void testLinkRel() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.example.org/"));

        String html = "<link href='/pingback' rel='pingback'>" +
                "<link href='/style.css' rel=stylesheet>" +
                "<link rel='my stylesheet rocks' href=/style2.css>" +
                "<link rel=icon href=/icon.ico>" +
                "<link href='http://dns-prefetch.example.com/' rel=dns-prefetch>" +
                "<link href=/without-rel>" +
                "<link href=/empty-rel rel=>" +
                "<link href=/just-spaces rel='   '>" +
                "<link href=/canonical rel=canonical>" +
                "<link href=/unknown rel=unknown>";

        List<String> expectedLinks = Arrays.asList(
                "E https://www.example.org/icon.ico",
                "E https://www.example.org/style.css",
                "E https://www.example.org/style2.css",
                "L https://www.example.org/canonical",
                "L https://www.example.org/unknown"
        );

        getExtractor().extract(curi, html);
        List<String> actualLinks = new ArrayList<>();
        for (CrawlURI link: curi.getOutLinks()) {
            actualLinks.add(link.getLastHop() + " " + link.getURI());
        }
        Collections.sort(actualLinks);

        assertEquals(expectedLinks, actualLinks);
    }

    @Test
    public void testDisobeyRelNofollow() throws URIException {
        String html = "<a href=/normal><a href=/nofollow rel=nofollow><a href=/both><a href=/both rel=nofollow>";
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.example.org/"));
        getExtractor().setObeyRelNofollow(false);
        getExtractor().extract(curi, html);
        Set<String> links = curi.getOutLinks().stream().map(CrawlURI::getURI).collect(Collectors.toSet());
        assertEquals(Set.of("https://www.example.org/both",
                "https://www.example.org/normal",
                "https://www.example.org/nofollow"), links);
    }

    @Test
    public void testRelNofollow() throws URIException {
        String html = "<a href=/normal></a><a href=/nofollow rel=nofollow></a><a href=/both></a>" +
                      "<a href=/both rel=nofollow></a>" +
                      "<a href=/multi1 rel='noopener nofollow'></a>" +
                      "<a href=/multi2 rel=\"nofollow nopener\"></a>" +
                      "<a href=/multi3 rel='noopener nofollow noentry'></a>";
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://www.example.org/"));
        getExtractor().setObeyRelNofollow(true);
        getExtractor().extract(curi, html);
        Set<String> links = curi.getOutLinks().stream().map(CrawlURI::getURI).collect(Collectors.toSet());
        assertEquals(Set.of("https://www.example.org/both",
                "https://www.example.org/normal"), links);
    }

    private void genericCrawl(CrawlURI curi, CharSequence cs,String[] dest){
        getExtractor().extract(curi, cs);

        CrawlURI[] links = curi.getOutLinks().toArray(new CrawlURI[0]);
        Arrays.sort(links);

        for (int i = 0; i < links.length; i++) {
            assertEquals(dest[i], links[i].getURI(), "outlink from picture");
        }
    }
    
}
