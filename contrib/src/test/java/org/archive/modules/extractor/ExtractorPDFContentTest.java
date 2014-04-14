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

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import java.util.HashSet;

import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

import com.itextpdf.text.pdf.PdfReader;

public class ExtractorPDFContentTest extends ContentExtractorTestBase {

   protected static final String TEST_RESOURCE_FILE_NAME = "ExtractorPDFContentTest1.pdf";

    
    public void testA() throws URIException, UnsupportedEncodingException, IOException, InterruptedException{
        CrawlURI testUri = createTestUri("http://www.example.com/fake.pdf", TEST_RESOURCE_FILE_NAME);
        extractor.process(testUri);   
        
        Set<Link> expected = makeLinkSet(testUri, new String[]{"http://www.businessdictionary.com/definition/supervisor.html","http://management.about.com/od/policiesandprocedures/g/supervisor1.html"});
        assertTrue(testUri.getOutLinks().containsAll(expected));        
    }
    
    @Override
    protected Extractor makeExtractor() {
        ExtractorPDFContent result = new ExtractorPDFContent();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule(); 
        result.setLoggerModule(ulm);
        return (Extractor)result;
    }
    private Set<Link> makeLinkSet(CrawlURI sourceUri, String[] urlStrs) throws URIException {
        HashSet<Link> linkSet = new HashSet<Link>();
        for (String urlStr : urlStrs) {
            linkSet.add(new Link(sourceUri.getUURI(), 
                    UURIFactory.getInstance(urlStr),
                    HTMLLinkContext.NAVLINK_MISC, Hop.NAVLINK)
                    );
        }
        return linkSet;
    }
    private CrawlURI createTestUri(String urlStr, String resourceFileName) throws URIException,
    UnsupportedEncodingException, IOException {
        UURI testUuri = UURIFactory.getInstance(urlStr);
        CrawlURI testUri = new CrawlURI(testUuri, null, null, LinkContext.NAVLINK_MISC);
        
        
        //InputStream is = ExtractorPDFContentTest.class.getClassLoader().getResourceAsStream(resourceFileName);

        //BufferedInputStream reader = new BufferedInputStream(new InputStreamReader(is));

        File temp = File.createTempFile("test", ".tmp");
        Recorder recorder = new Recorder(temp, 1024, 1024);
        InputStream is = recorder.inputWrap(ExtractorPDFContentTest.class.getClassLoader().getResourceAsStream(resourceFileName));
        recorder.markContentBegin();
        for(int x = is.read(); x>=0; x=is.read());
        is.close();
        
//        
//        byte[] b = content.getBytes(charset);
//        ByteArrayInputStream bais = new ByteArrayInputStream(b);
//        InputStream is = recorder.inputWrap(bais);
//        recorder.markContentBegin();
//        for (int x = is.read(); x >= 0; x = is.read());
//        is.close();
//        return recorder;
        
        
        
//        StringBuilder content = new StringBuilder();
//        String line = "";
//        while ((line = reader.readLine()) != null) {
//            content.append(line);
//        }
//        Recorder recorder = createRecorder(content.toString(), "UTF-8");
//        IOUtils.closeQuietly(is);

        testUri.setContentType("application/pdf");
        testUri.setFetchStatus(200);
        testUri.setRecorder(recorder);
        testUri.setContentSize(recorder.getResponseContentLength());
        return testUri;
    }
   
 
}
