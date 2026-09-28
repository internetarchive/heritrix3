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

import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test for ExtractorJson.
 *
 * <p>The same URI is presented twice: once as a plain object property, once
 * nested inside an array. {@link #testUriInObjectPropertyIsExtracted()} passes
 * and {@link #testUriInsideArrayIsExtracted()} does not, so the second result is
 * about array handling rather than about the fixture or the harness.
 */
public class ExtractorJsonTest {

    private static final String URI_IN_DOCUMENT = "http://example.org/target.json";

    @Test
    public void testUriInObjectPropertyIsExtracted() throws Exception {
        assertExtracted("{\"pointer\":{\"href\":\"" + URI_IN_DOCUMENT + "\"}}");
    }

    @Test
    public void testUriInsideArrayIsExtracted() throws Exception {
        assertExtracted("{\"pointers\":[{\"href\":\"" + URI_IN_DOCUMENT + "\"}]}");
    }

    @Test
    public void testBareUriStringInsideArrayIsExtracted() throws Exception {
        assertExtracted("{\"pointers\":[\"" + URI_IN_DOCUMENT + "\"]}");
    }

    private void assertExtracted(String json) throws Exception {
        CrawlURI curi = new CrawlURI(
                UURIFactory.getInstance("http://example.org/document.json"),
                null, null, LinkContext.NAVLINK_MISC);
        curi.setContentType("application/json");
        curi.setRecorder(ContentExtractorTestBase.createRecorder(json, "UTF-8"));
        curi.setContentSize(json.length());
        curi.setFetchStatus(200);

        ExtractorJson extractor = new ExtractorJson();
        extractor.setLoggerModule(new UnitTestUriLoggerModule());
        extractor.process(curi);

        // compared as strings rather than as CrawlURIs so that a failure names
        // the URI that was missed
        List<String> extracted = new ArrayList<String>();
        for (CrawlURI outlink : curi.getOutLinks()) {
            extracted.add(outlink.getURI());
        }
        Collections.sort(extracted);

        assertEquals(Collections.singletonList(URI_IN_DOCUMENT), extracted);
    }
}
