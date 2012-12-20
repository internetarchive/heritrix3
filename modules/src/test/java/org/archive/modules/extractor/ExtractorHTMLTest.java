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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

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
        Link link = new Link(src, dest, context, hop);
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
        Link[] links = puri.getOutLinks().toArray(new Link[0]);
        assertTrue("did not find single link",links.length==1);
        assertTrue("expected link not found", 
                links[0].getDestination().toString().equals(expected));
    }
    
    /**
     * Test only extract FORM ACTIONS with METHOD GET 
     * 
     * [HER-1280] do not by default GET form action URLs declared as POST, 
     * because it can cause problems/complaints 
     * http://webteam.archive.org/jira/browse/HER-1280
     */
    public void testOnlyExtractFormGets() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
            "<form method=\"get\" action=\"http://www.example.com/ok1\"> "+
            "<form action=\"http://www.example.com/ok2\" method=\"get\"> "+
            "<form method=\"post\" action=\"http://www.example.com/notok\"> "+
            "<form action=\"http://www.example.com/ok3\"> ";
        getExtractor().extract(puri, cs);
        Link[] links = puri.getOutLinks().toArray(new Link[0]);
        // find exactly 3 (not the POST) action URIs
        assertTrue("incorrect number of links found",links.length==3);
    }
    
    /**
     * Test detection, respect of meta robots nofollow directive
     */
    public void testMetaRobots() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = 
            "Blah Blah "+
            "<meta name='robots' content='index,nofollow'>"+
            "<a href='blahblah'>blah</a> "+
            "blahblah";
        getExtractor().extract(puri, cs);
        assertEquals("meta robots content not extracted","index,nofollow",
                puri.getData().get(ExtractorHTML.A_META_ROBOTS));
        Link[] links = puri.getOutLinks().toArray(new Link[0]);
        assertTrue("link extracted despite meta robots",links.length==0);
    }
    
    /**
     * Test that relative URIs with late colons aren't misinterpreted
     * as absolute URIs with long, illegal scheme components. 
     * 
     * See http://webteam.archive.org/jira/browse/HER-1268
     * 
     * @throws URIException
     */
    public void testBadRelativeLinks() throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com"));
        CharSequence cs = "<a href=\"example.html;jsessionid=deadbeef:deadbeed?parameter=this:value\"/>"
                + "<a href=\"example.html?parameter=this:value\"/>";
        getExtractor().extract(curi, cs);

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((Link) object)
                        .getDestination()
                        .toString()
                        .indexOf(
                                "/example.html;jsessionid=deadbeef:deadbeed?parameter=this:value") >= 0;
            }
        }));

        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((Link) object).getDestination().toString().indexOf(
                        "/example.html?parameter=this:value") >= 0;
            }
        }));
    }
    
    /**
     * Test if scheme is maintained by speculative hops onto exact 
     * same host
     * 
     * [HER-1524] speculativeFixup in ExtractorJS should maintain URL scheme
     */
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
                        + ((Link) object).getDestination().toString()
                        + " and https://www.anotherexample.com/");
                return ((Link) object).getDestination().toString().equals(
                        "http://www.anotherexample.com/");
            }
        }));
        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((Link) object).getDestination().toString().equals(
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
     *   
     * @throws URIException
     */
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

    public void testOutLinksWithBaseHref() throws URIException {
        CrawlURI puri = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com/abc/index.html"));
        puri.setBaseURI(puri.getUURI());
        CharSequence cs = 
            "<base href=\"http://www.example.com/\">" + 
            "<a href=\"def/another1.html\">" + 
            "<a href=\"ghi/another2.html\">";
        getExtractor().extract(puri, cs);
        Link[] links = puri.getOutLinks().toArray(new Link[0]);
        Arrays.sort(links); 
        String dest1 = "http://www.example.com/def/another1.html";
        String dest2 = "http://www.example.com/ghi/another2.html";
        // ensure outlink from base href
        assertEquals("outlink1 from base href",dest1,
                links[1].getDestination().toString());
        assertEquals("outlink2 from base href",dest2,
                links[2].getDestination().toString());
    }
    
    protected Predicate destinationContainsPredicate(final String fragment) {
        return new Predicate() {
            public boolean evaluate(Object object) {
                return ((Link) object).getDestination().toString().indexOf(fragment) >= 0;
            }
        };
    }
    
    protected Predicate destinationsIsPredicate(final String value) {
        return new Predicate() {
            public boolean evaluate(Object object) {
                return ((Link) object).getDestination().toString().equals(value);
            }
        };
    }
    
    /**
     * HER-1728 
     * @throws URIException 
     */
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
        assertTrue("outlinks should contain: "+expected,
                CollectionUtils.exists(curi.getOutLinks(),destinationsIsPredicate(expected)));
    }
    
    /**
     * HER-1728 
     * @throws URIException 
     */
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
        assertTrue("outlinks should contain: "+expected,
                CollectionUtils.exists(curi.getOutLinks(),destinationsIsPredicate(expected)));
    }
    
    /**
     * HER-1998 
     * @throws URIException 
     */
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
        
        Link[] links = curi.getOutLinks().toArray(new Link[0]);
        Arrays.sort(links); 
        
        String dest1 = "http://www.example.com/foo.gif";
        String dest2 = "http://www.example.com/foo.js";

        assertEquals("outlink1 from conditional comment img src",dest1,
                links[0].getDestination().toString());
        assertEquals("outlink2 from conditional comment script src",dest2,
                links[1].getDestination().toString());
        
    }
        
}
