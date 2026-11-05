/*
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 * Licensed to the Internet Archive (IA) by one or more individual contributors.
 * The IA licenses this file to You under the Apache License, Version 2.0.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package org.archive.modules.extractor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlMetadata;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtractorWebManifestTest extends ContentExtractorTestBase {
    @Override
    protected Extractor makeExtractor() {
        ExtractorWebManifest result = new ExtractorWebManifest();
        result.setLoggerModule(new UnitTestUriLoggerModule());
        return result;
    }

    @Test
    void extractsPagesAndImagesWithManifestRelativeUrls() throws Exception {
        CrawlURI curi = manifest("https://cdn.example.org/app/manifest.webmanifest", "application/manifest+json", """
                {
                  "start_url": "launch",
                  "icons": [{"src": "icons/icon"}],
                  "screenshots": [{"src": "//images.example.org/screen.png"}],
                  "shortcuts": [{"url": "../compose", "icons": [{"src": "/shortcut.png"}]}],
                  "name": "example.com",
                  "description": "https://example.com/not-a-resource",
                  "id": "/identity", "scope": "/scope/"
                }
                """);
        curi.setBaseURI(UURIFactory.getInstance("https://wrong.example.org/"));
        extractor.process(curi);
        Map<String, String> hops = curi.getOutLinks().stream().collect(
                Collectors.toMap(Object::toString, CrawlURI::getPathFromSeed));
        assertEquals(Map.of(
                "https://cdn.example.org/app/launch", "L",
                "https://cdn.example.org/app/icons/icon", "E",
                "https://images.example.org/screen.png", "E",
                "https://cdn.example.org/compose", "L",
                "https://cdn.example.org/shortcut.png", "E"), hops);
        assertTrue(curi.getOutLinks().stream().allMatch(link -> link.getViaContext().toString().startsWith("manifest/")));
        assertEquals(5, extractor.numberOfLinksExtracted.get());
        assertTrue(curi.hasBeenLinkExtracted());
        assertNoSideEffects(curi);
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.org/api/manifest, application/manifest+json",
            "https://example.org/api/manifest, APPLICATION/MANIFEST+JSON; charset=utf-8",
            "https://example.org/app.WEBMANIFEST?v=2, text/plain",
            "https://example.org/app.webmanifest, ''"
    })
    void recognizesManifestMimeTypesAndFilenames(String url, String type) throws Exception {
        CrawlURI curi = manifest(url, type, "{\"icons\":[{\"src\":\"/icon\"}]}");
        extractor.process(curi);
        assertEquals(Set.of("https://example.org/icon"), urls(curi));
        assertTrue(curi.hasBeenLinkExtracted());
        assertNoSideEffects(curi);
    }

    @Test
    void leavesUnrelatedJsonToOtherExtractors() throws Exception {
        CrawlURI curi = manifest("https://example.org/data.json", "application/json",
                "{\"start_url\":\"/not-a-manifest\"}");
        extractor.process(curi);
        assertTrue(curi.getOutLinks().isEmpty());
        assertFalse(curi.hasBeenLinkExtracted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "[]", "null", "\"not an object\""})
    void leavesMalformedAndNonObjectJsonAvailableForFallback(String content) throws Exception {
        CrawlURI curi = manifest("https://example.org/app.webmanifest", "application/manifest+json", content);
        extractor.process(curi);
        assertTrue(curi.getOutLinks().isEmpty());
        assertFalse(curi.hasBeenLinkExtracted());
        assertNoSideEffects(curi);
    }

    @Test
    void ignoresInvalidMemberTypesAndNonHttpUrls() throws Exception {
        CrawlURI curi = manifest("https://example.org/app.webmanifest", "application/manifest+json", """
                {
                  "start_url": [],
                  "icons": [null, 3, {"src": false}, {"src": ""}, {"src": "   "},
                    {"src": "data:image/png;base64,AA=="}, {"src": "javascript:alert(1)"},
                    {"src": "file:///tmp/icon"}, {"src": " https://images.example.org/icon.png "}],
                  "screenshots": {"src": "/not-an-array.png"},
                  "shortcuts": [null, "bad", {"url": "mailto:test@example.org", "icons": {}}]
                }
                """);
        extractor.process(curi);
        assertEquals(Set.of("https://images.example.org/icon.png"), urls(curi));
        assertTrue(curi.hasBeenLinkExtracted());
        assertNoSideEffects(curi);
    }

    @Test
    void respectsMaximumOutlinks() throws Exception {
        extractor.setExtractorParameters(new ExtractorParameters() {
            public int getMaxOutlinks() { return 2; }
            public boolean getExtractIndependently() { return false; }
            public boolean getExtract404s() { return false; }
        });
        CrawlURI curi = manifest("https://example.org/app.webmanifest", "application/manifest+json", """
                {"start_url":"/start", "icons":[{"src":"/one.png"},{"src":"/two.png"}]}
                """);
        extractor.process(curi);
        assertEquals(Set.of("https://example.org/start", "https://example.org/one.png"), urls(curi));
        assertEquals(2, extractor.numberOfLinksExtracted.get());
        assertTrue(curi.hasBeenLinkExtracted());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void followsManifestDiscoveredInHtmlAndStopsSpeculativeJsonExtraction(boolean jericho) throws Exception {
        CrawlURI page = manifest("https://example.org/pages/index.html", "text/html",
                "<html><head><link rel=\"manifest\" href=\"/app/manifest.json\"></head></html>");
        ExtractorHTML html = jericho ? new JerichoExtractorHTML() : new ExtractorHTML();
        html.setLoggerModule(new UnitTestUriLoggerModule());
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        html.setMetadata(metadata);
        html.afterPropertiesSet();
        html.process(page);
        CrawlURI app = page.getOutLinks().stream().filter(link -> link.toString()
                .equals("https://example.org/app/manifest.json")).findFirst().orElseThrow();
        assertEquals("link[rel='manifest']/@href", app.getViaContext().toString());
        assertTrue(app.getPathFromSeed().endsWith("E"));
        setContent(app, "application/json", """
                {"start_url":"launch", "icons":[{"src":"icons/icon"}], "name":"example.com"}
                """);
        extractor.process(app);
        ExtractorJS js = new ExtractorJS();
        js.setLoggerModule(new UnitTestUriLoggerModule());
        js.process(app);
        assertEquals(Set.of("https://example.org/app/launch", "https://example.org/app/icons/icon"), urls(app));
        assertNoSideEffects(app);
    }

    private CrawlURI manifest(String url, String type, String content) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(url), "",
                UURIFactory.getInstance("https://page.example.org/index.html"), LinkContext.EMBED_MISC);
        setContent(curi, type, content);
        return curi;
    }

    private void setContent(CrawlURI curi, String type, String content) throws Exception {
        curi.setContentType(type);
        curi.setRecorder(createRecorder(content, "UTF-8"));
        curi.setContentSize(content.getBytes(StandardCharsets.UTF_8).length);
        curi.setFetchStatus(200);
    }

    private Set<String> urls(CrawlURI curi) {
        return curi.getOutLinks().stream().map(Object::toString).collect(Collectors.toSet());
    }
}
