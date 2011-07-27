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

import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

/**
 * Unit test for {@link ExtractorJS}.
 *
 * @contributor gojomo
 */
public class ExtractorJSTest extends StringExtractorTestBase {

    final public static String[] VALID_TEST_DATA = new String[] {
        "var foo = \"http://www.example.com/outlink\";",
        "http://www.example.com/outlink",
        
        "var foo = \"<a href=\\\"http://www.example.com/outlink\\\">link in html in string</a>\";",
        "http://www.example.com/outlink",
        
        "var foo = \"<a href=\\\"http:\\/\\/www.example.com\\/outlink\\\">link in html in string with gratuitous escaping</a>\";",
        "http://www.example.com/outlink",

        "'string with spaces','http://example.com/outlink'",
        "http://example.com/outlink"
    };
       
    @Override
    protected String[] getValidTestData() {
        return VALID_TEST_DATA;
    }
    
    @Override
    protected Extractor makeExtractor() {
        ExtractorJS result = new ExtractorJS();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        result.setLoggerModule(ulm);
        return result;
    }
    
    @Override
    protected Collection<TestData> makeData(String content, String destURI)
    throws Exception {
        List<TestData> result = new ArrayList<TestData>();
        UURI src = UURIFactory.getInstance("http://www.archive.org/dummy.js");
        CrawlURI euri = new CrawlURI(src, null, src, LinkContext.NAVLINK_MISC);
        Recorder recorder = createRecorder(content);
        euri.setContentType("text/javascript");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
                
        UURI dest = UURIFactory.getInstance(destURI);
        Link link = new Link(src, dest, LinkContext.JS_MISC, Hop.SPECULATIVE);
        result.add(new TestData(euri, link));
        
        return result;
    }
}
