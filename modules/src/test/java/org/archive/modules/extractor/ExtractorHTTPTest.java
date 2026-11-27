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

import org.archive.modules.ProcessorTestBase;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test for {@link ExtractorHTTP}.
 *
 * @author pjack
 */
public class ExtractorHTTPTest extends ProcessorTestBase {

    @ParameterizedTest
    @CsvSource({
            "SourceMap, app.js.map, https://cdn.example.org/assets/app.js.map",
            "X-SourceMap, app.js.map, https://cdn.example.org/assets/app.js.map",
            "sOuRcEmAp, ../maps/app.js.map, https://cdn.example.org/maps/app.js.map",
            "x-sourcemap, /maps/app.css.map, https://cdn.example.org/maps/app.css.map",
            "SourceMap, //maps.example.org/app.js.map, https://maps.example.org/app.js.map",
            "SourceMap, https://maps.example.org/app.js.map?v=1, https://maps.example.org/app.js.map?v=1",
            "SourceMap, ?map=1, https://cdn.example.org/assets/app.js?map=1"
    })
    void discoversSourceMapHeaders(String header, String value, String expected) throws Exception {
        ExtractorHTTP extractor = new ExtractorHTTP();
        CrawlURI curi = script();
        curi.putHttpResponseHeader(header, value);
        extractor.process(curi);

        assertEquals(Set.of(expected, "https://cdn.example.org/favicon.ico"), urls(curi));
        CrawlURI map = curi.getOutLinks().stream().filter(link -> link.toString().equals(expected)).findFirst().orElseThrow();
        String canonicalHeader = header.equalsIgnoreCase("SourceMap") ? "SourceMap:" : "X-SourceMap:";
        assertEquals(canonicalHeader, map.getViaContext().toString());
        assertEquals("R", map.getPathFromSeed());
        assertEquals(1, extractor.numberOfLinksExtracted.get());
        assertFalse(curi.hasBeenLinkExtracted());
    }

    @Test
    void prefersStandardHeaderOverDeprecatedHeader() throws Exception {
        CrawlURI curi = script();
        curi.putHttpResponseHeader("SourceMap", "standard.map");
        curi.putHttpResponseHeader("X-SourceMap", "deprecated.map");
        new ExtractorHTTP().process(curi);
        assertEquals(Set.of("https://cdn.example.org/assets/standard.map",
                "https://cdn.example.org/favicon.ico"), urls(curi));
    }

    @Test
    void trimsHeaderWhitespace() throws Exception {
        CrawlURI curi = script();
        curi.putHttpResponseHeader("SourceMap", " \tapp.js.map\t ");
        new ExtractorHTTP().process(curi);
        assertEquals(Set.of("https://cdn.example.org/assets/app.js.map",
                "https://cdn.example.org/favicon.ico"), urls(curi));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t ", "data:application/json;base64,e30="})
    void skipsEmptyAndInlineSourceMaps(String value) throws Exception {
        ExtractorHTTP extractor = new ExtractorHTTP();
        CrawlURI curi = script();
        curi.putHttpResponseHeader("SourceMap", value);
        extractor.process(curi);
        assertEquals(Set.of("https://cdn.example.org/favicon.ico"), urls(curi));
        assertEquals(0, extractor.numberOfLinksExtracted.get());
    }

    @Test
    void retainsExistingHeaderDiscoveryWithoutSourceMaps() throws Exception {
        CrawlURI curi = script();
        curi.putHttpResponseHeader("Location", "redirect.js");
        curi.putHttpResponseHeader("Content-Location", "canonical.js");
        curi.putHttpResponseHeader("Refresh", "0;url=refresh.js");
        new ExtractorHTTP().process(curi);
        assertEquals(Set.of("https://cdn.example.org/assets/redirect.js",
                "https://cdn.example.org/assets/canonical.js",
                "https://cdn.example.org/assets/refresh.js",
                "https://cdn.example.org/favicon.ico"), urls(curi));
    }

    private CrawlURI script() throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://cdn.example.org/assets/app.js"), "",
                UURIFactory.getInstance("https://www.example.org/page/"), LinkContext.JS_MISC);
        curi.setFetchStatus(200);
        curi.setFetchType(FetchType.HTTP_GET);
        return curi;
    }

    private Set<String> urls(CrawlURI curi) {
        return curi.getOutLinks().stream().map(Object::toString).collect(Collectors.toSet());
    }

    @Override
    protected Object makeModule() {
        return new ExtractorHTTP();
    }
    
}
