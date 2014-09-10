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

import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.StringExtractorTestBase.TestData;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

/**
 * Unit test for {@link ExtractorXML}.
 *
 * @author pjack
 */
public class ExtractorXMLTest extends StringExtractorTestBase {

    final public static String[] VALID_TEST_DATA = new String[] {
        "<link>http://conservation.org</link>",
        "http://conservation.org",

        "<CI:imagePath><![CDATA[http://sp10.conservation.org/CIFMGPhotos/790x444_skerry_gallery_02.jpg]]></CI:imagePath>",
        "http://sp10.conservation.org/CIFMGPhotos/790x444_skerry_gallery_02.jpg",
        
    };
    
    @Override
    protected String[] getValidTestData() {
        return VALID_TEST_DATA;
    }

    @Override
    protected Extractor makeExtractor() {
        ExtractorXML result = new ExtractorXML();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();  
        result.setLoggerModule(ulm);
        return result;
    }

    protected ExtractorXML getExtractor() {
        return (ExtractorXML) extractor;
    }
    
    @Override
    protected Collection<TestData> makeData(String content, String destURI)
    throws Exception {
        List<TestData> result = new ArrayList<TestData>();
        UURI src = UURIFactory.getInstance("http://www.archive.org/start/");
        CrawlURI euri = new CrawlURI(src, null, null, 
        		LinkContext.SPECULATIVE_MISC);
        Recorder recorder = createRecorder(content, "UTF-8");
        euri.setContentType("text/xml");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
                
        UURI dest = UURIFactory.getInstance(destURI);
        CrawlURI link = euri.createCrawlURI(dest, LinkContext.SPECULATIVE_MISC, Hop.SPECULATIVE);
        result.add(new TestData(euri, link));
        
        return result;
    }

}
