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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

public class ExtractorPDFContentTest extends ContentExtractorTestBase {

   protected static final String TEST_RESOURCE_FILE_1 = "ExtractorPDFContentTest1.pdf";
   protected static final String TEST_RESOURCE_FILE_2 = "ExtractorPDFContentTest2.pdf";
   protected static final String TEST_RESOURCE_FILE_3 = "ExtractorPDFContentTest3.pdf";
   protected static final String TEST_RESOURCE_FILE_4 = "ExtractorPDFContentTest4.pdf";

    
    public void testA() throws URIException, UnsupportedEncodingException, IOException, InterruptedException{
        CrawlURI testUri = createTestUri("http://www.example.com/fake.pdf", TEST_RESOURCE_FILE_1);
        extractor.process(testUri);   

        Set<CrawlURI> expected = makeLinkSet(testUri, new String[]{"http://www.businessdictionary.com/definition/supervisor.html","http://management.about.com/od/policiesandprocedures/g/supervisor1.html"});
        assertTrue(testUri.getOutLinks().containsAll(expected));        
    }
    public void testEndingInDot() throws URIException, UnsupportedEncodingException, IOException, InterruptedException{
        CrawlURI testUri = createTestUri("http://www.example.com/fake.pdf", TEST_RESOURCE_FILE_2);
        extractor.process(testUri);   

        Set<CrawlURI> expected = makeLinkSet(testUri, new String[]{"http://www.fec.gov/data/CommitteeSummary.do",
                "http://www.opensecrets.org/bigpicture/elec_stats.php",
                "http://www.opensecrets.org/pacs"});
        assertTrue(testUri.getOutLinks().containsAll(expected));        
    }
    public void testUnderscoreInURL() throws URIException, UnsupportedEncodingException, IOException, InterruptedException{
        CrawlURI testUri = createTestUri("http://www.example.com/fake.pdf", TEST_RESOURCE_FILE_3);
        extractor.process(testUri);   

        Set<CrawlURI> expected = makeLinkSet(testUri, new String[]{"http://www.dot.gov/sites/dot.dev/files/docs/2014_February_ATCR.pdf"});
        assertTrue(testUri.getOutLinks().containsAll(expected));        
    }
    public void testParenthesis() throws URIException, UnsupportedEncodingException, IOException, InterruptedException{
        CrawlURI testUri = createTestUri("http://www.example.com/fake.pdf", TEST_RESOURCE_FILE_4);
        extractor.process(testUri);

        Set<CrawlURI> expected = makeLinkSet(testUri, new String[]{"http://www.unisys.com","http://www.myserver.mycorp.com/images/exttest.jpg","http://www.adobe.com/intro?100,200","http://www.w3.org/1999/xhtml","http://www.xfa.org/schema/xfa-data/1.0","http://www.adobe.com","http://www.adobe.com/getacro.gif","http://www.example.com/testOpeningParen"});
        assertTrue(testUri.getOutLinks().containsAll(expected));
    }
    public void testNewlineSeparatedURIs() throws URIException, UnsupportedEncodingException, IOException, InterruptedException{
        CrawlURI testUri = createTestUri("http://www.example.com/fake.pdf", TEST_RESOURCE_FILE_4);
        extractor.process(testUri);

        Set<CrawlURI> expected = makeLinkSet(testUri, new String[]{"http://www.unisys.com","http://www.myserver.mycorp.com/images/exttest.jpg","http://www.example.com/test","http://www.adobe.com/intro?100,200","http://www.w3.org/1999/xhtml","http://www.xfa.org/schema/xfa-data/1.0","http://www.adobe.com","http://www.adobe.com/getacro.gif"});
        assertTrue(testUri.getOutLinks().containsAll(expected));
    }


    
    @Override
    protected Extractor makeExtractor() {
        ExtractorPDFContent result = new ExtractorPDFContent();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule(); 
        result.setLoggerModule(ulm);
        return (Extractor)result;
    }
    private Set<CrawlURI> makeLinkSet(CrawlURI sourceUri, String[] urlStrs) throws URIException {
        HashSet<CrawlURI> linkSet = new HashSet<CrawlURI>();
        for (String urlStr : urlStrs) {
            CrawlURI link = sourceUri.createCrawlURI(urlStr, HTMLLinkContext.NAVLINK_MISC, Hop.NAVLINK);
            linkSet.add(link);
        }
        return linkSet;
    }
    private CrawlURI createTestUri(String urlStr, String resourceFileName) throws URIException,
    UnsupportedEncodingException, IOException {
        UURI testUuri = UURIFactory.getInstance(urlStr);
        CrawlURI testUri = new CrawlURI(testUuri, null, null, LinkContext.NAVLINK_MISC);
        

        File temp = File.createTempFile("test", ".tmp");
        Recorder recorder = new Recorder(temp, 1024, 1024);
        InputStream is = recorder.inputWrap(ExtractorPDFContentTest.class.getClassLoader().getResourceAsStream(resourceFileName));
        recorder.markContentBegin();
        for(int x = is.read(); x>=0; x=is.read());
        is.close();
        

        testUri.setContentType("application/pdf");
        testUri.setFetchStatus(200);
        testUri.setRecorder(recorder);
        testUri.setContentSize(recorder.getResponseContentLength());
        return testUri;
    }
   
 
}
