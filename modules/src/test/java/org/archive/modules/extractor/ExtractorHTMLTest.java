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
import java.util.Collection;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.DefaultProcessorURI;
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
        result.setExtractorParameters(Extractor.DEFAULT_PARAMETERS);
        result.afterPropertiesSet();
        return result;
    }
    
    @Override
    protected Collection<TestData> makeData(String content, String destURI)
    throws Exception {
        List<TestData> result = new ArrayList<TestData>();
        UURI src = UURIFactory.getInstance("http://www.archive.org/start/");
        DefaultProcessorURI euri = new DefaultProcessorURI(src, 
                LinkContext.NAVLINK_MISC);
        Recorder recorder = createRecorder(content);
        euri.setContentType("text/html");
        euri.setRecorder(recorder);
        euri.setContentLength(content.length());
                
        UURI dest = UURIFactory.getInstance(destURI);
        LinkContext context = determineContext(content);
        Hop hop = determineHop(content);
        Link link = new Link(src, dest, context, hop);
        result.add(new TestData(euri, link));
        
        euri = new DefaultProcessorURI(src, LinkContext.NAVLINK_MISC);
        recorder = createRecorder(content);
        euri.setContentType("application/xhtml");
        euri.setRecorder(recorder);
        euri.setContentLength(content.length());
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
            return new HTMLLinkContext("a/@href");
        }
        if (s.endsWith(" IMG")) {
            return new HTMLLinkContext("img/@src");
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
        DefaultProcessorURI puri = new DefaultProcessorURI(UURIFactory
                .getInstance("http://www.example.com"), null);
        ExtractorHTML extractor = (ExtractorHTML)makeExtractor();
        extractor.extract(puri, source);
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
        DefaultProcessorURI puri = new DefaultProcessorURI(UURIFactory
                .getInstance("http://www.example.com"),null);
        CharSequence cs = 
            "<form method=\"get\" action=\"http://www.example.com/ok1\"> "+
            "<form action=\"http://www.example.com/ok2\" method=\"get\"> "+
            "<form method=\"post\" action=\"http://www.example.com/notok\"> "+
            "<form action=\"http://www.example.com/ok3\"> ";
        ExtractorHTML extractor = (ExtractorHTML)makeExtractor();
        extractor.extract(puri, cs);
        Link[] links = puri.getOutLinks().toArray(new Link[0]);
        // find exactly 3 (not the POST) action URIs
        assertTrue("incorrect number of links found",links.length==3);
    }
    
    /**
     * Test detection, respect of meta robots nofollow directive
     */
    public void testMetaRobots() throws URIException {
        DefaultProcessorURI puri = new DefaultProcessorURI(UURIFactory
                .getInstance("http://www.example.com"),null);
        CharSequence cs = 
            "Blah Blah "+
            "<meta name='robots' content='index,nofollow'>"+
            "<a href='blahblah'>blah</a> "+
            "blahblah";
        ExtractorHTML extractor = (ExtractorHTML)makeExtractor();
        extractor.extract(puri, cs);
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
        DefaultProcessorURI curi = new DefaultProcessorURI(UURIFactory
                .getInstance("http://www.example.com"), null);
        CharSequence cs = "<a href=\"example.html;jsessionid=deadbeef:deadbeed?parameter=this:value\"/>"
                + "<a href=\"example.html?parameter=this:value\"/>";
        ExtractorHTML extractor = (ExtractorHTML)makeExtractor();
        extractor.extract(curi, cs);

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
        DefaultProcessorURI curi = new DefaultProcessorURI(UURIFactory
                .getInstance("https://www.example.com"), null);
        CharSequence cs = 
            "<script type=\"text/javascript\">_parameter=\"www.anotherexample.com\";"
                + "_anotherparameter=\"www.example.com/index.html\""
                + ";</script>";
        ExtractorHTML extractor = (ExtractorHTML)makeExtractor();
        extractor.extract(curi, cs);

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
        DefaultProcessorURI curi = new DefaultProcessorURI(UURIFactory
                .getInstance("http://www.example.com/en/fiche/dossier/322/"), null);
        CharSequence cs = 
            "<script type=\"text/javascript\">"
            + "var gaJsHost = ((\"https:\" == document.location.protocol) "
            + "? \"https://ssl.\" : \"http://www.\");"
            + "document.write(unescape(\"%3Cscript src='\" + gaJsHost + "
            + "\"google-analytics.com/ga.js' "
            + "type='text/javascript'%3E%3C/script%3E\"));"
            + "</script>";
        ExtractorHTML extractor = (ExtractorHTML)makeExtractor();
        extractor.extract(curi, cs);
        assertTrue("outlinks should be empty",curi.getOutLinks().isEmpty());
                
    }

    public void testOutLinksWithBaseHref() throws URIException {
        DefaultProcessorURI puri = new DefaultProcessorURI(UURIFactory
                .getInstance("http://www.example.com/abc/index.html"),null);
        puri.setBaseURI(puri.getUURI());
        CharSequence cs = 
            "<base href=\"http://www.example.com/\">" + 
            "<a href=\"def/another1.html\">" + 
            "<a href=\"ghi/another2.html\">";
        ExtractorHTML extractor = (ExtractorHTML)makeExtractor();
        extractor.extract(puri, cs);
        Link[] links = puri.getOutLinks().toArray(new Link[0]);
        String dest1 = "http://www.example.com/def/another1.html";
        String dest2 = "http://www.example.com/ghi/another2.html";
        // ensure outlink from base href
        assertEquals("outlink1 from base href",dest1,
                links[1].getDestination().toString());
        assertEquals("outlink2 from base href",dest2,
                links[2].getDestination().toString());
    }
        
}
