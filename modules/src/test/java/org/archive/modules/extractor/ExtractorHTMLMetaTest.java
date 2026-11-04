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

import java.util.Set;
import java.util.stream.Collectors;

import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExtractorHTMLMetaTest extends ContentExtractorTestBase {
    @Override
    protected Extractor makeExtractor() {
        ExtractorHTML html = new ExtractorHTML();
        html.setLoggerModule(new UnitTestUriLoggerModule());
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        html.setMetadata(metadata);
        html.afterPropertiesSet();
        return html;
    }

    @ParameterizedTest
    @ValueSource(strings = {"og:site_name", "og:title", "og:description", "og:type", "og:locale",
            "og:locale:alternate", "og:determiner", "twitter:domain", "twitter:title",
            "twitter:description", "twitter:card", "twitter:site", "twitter:creator",
            "author", "publisher", "description", "keywords", "generator", "application-name"})
    void doesNotGuessLinksFromKnownTextFields(String field) throws Exception {
        assertUrls("<meta name=\"" + field + "\" content=\"example.com\">"
                + "<meta property=\"" + field + "\" content=\"example.com\">");
    }

    @Test
    void handlesCaseWhitespaceAndAttributeOrder() throws Exception {
        assertUrls("<META CONTENT='example.com' PROPERTY=' OG:SITE_NAME '>"
                + "<meta content='example.com' NAME=' PuBliSher '>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"og:url", "og:image", "og:image:secure_url", "og:video", "og:audio",
            "twitter:image", "twitter:player", "custom-url"})
    void preservesUrlFieldsAndUnknownFields(String field) throws Exception {
        assertUrls("<meta property=\"" + field + "\" content=\"https://cdn.example.org/image.png\">",
                "https://cdn.example.org/image.png");
    }

    @Test
    void preservesUnknownNameAndUnnamedMetaContent() throws Exception {
        assertUrls("<meta name='custom-resource' content='/one.png'><meta content='/two.png'>",
                "https://example.org/one.png", "https://example.org/two.png");
    }

    @Test
    void preservesMetaRefreshEvenWithTextualName() throws Exception {
        assertUrls("<meta http-equiv='refresh' name='publisher' content='0;url=/next'>",
                "https://example.org/next");
    }

    private void assertUrls(String content, String... expected) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://example.org/articles/index.html"));
        curi.setContentType("text/html");
        curi.setRecorder(createRecorder(content, "UTF-8"));
        curi.setContentSize(content.length());
        curi.setFetchStatus(200);
        extractor.process(curi);
        assertEquals(Set.of(expected), curi.getOutLinks().stream().map(Object::toString).collect(Collectors.toSet()));
        assertNoSideEffects(curi);
    }
}
