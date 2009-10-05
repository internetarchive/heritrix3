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

import java.util.Collection;
import java.util.Collections;

import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Extractor;
import org.archive.modules.extractor.ExtractorCSS;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.StringExtractorTestBase;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;

import static org.archive.modules.extractor.LinkContext.EMBED_MISC;
import static org.archive.modules.extractor.LinkContext.NAVLINK_MISC;


/**
 * Unit test for ExtractorCSS.
 * 
 * @author pjack
 */
public class ExtractorCSSTest extends StringExtractorTestBase {

    
    /**
     * Test data. a[n] is sample CSS input, a[n + 1] is expected extracted URI
     */
    final public static String[] VALID_TEST_DATA = new String[] {
        "@import url(http://www.archive.org)", 
        "http://www.archive.org",

        "@import url('http://www.archive.org')", 
        "http://www.archive.org",

        "@import url(    \"  http://www.archive.org  \"   )", 
        "http://www.archive.org",

        "table { border: solid black 1px}\n@import url(style.css)", 
        "http://www.archive.org/start/style.css",

    };

    @Override
    protected Extractor makeExtractor() {
        ExtractorCSS result = new ExtractorCSS();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();
        result.setLoggerModule(ulm);
        return result;    
    }
 

    @Override
    protected Collection<TestData> makeData(String content, String uri) 
    throws Exception {
        UURI src = UURIFactory.getInstance("http://www.archive.org/start/");
        CrawlURI euri = new CrawlURI(src, null, null, NAVLINK_MISC);
        Recorder recorder = createRecorder(content);
        euri.setContentType("text/css");
        euri.setRecorder(recorder);
        euri.setContentSize(content.length());
        
        UURI dest = UURIFactory.getInstance(uri);
        Link link = new Link(src, dest, EMBED_MISC, Hop.EMBED);
        TestData td = new TestData(euri, link);
        return Collections.singleton(td);
    }


    @Override
    public String[] getValidTestData() {
        return VALID_TEST_DATA;
    }

}
