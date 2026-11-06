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

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExtractorHLSTest extends ContentExtractorTestBase {
    @Override
    protected Extractor makeExtractor() {
        ExtractorHLS result = new ExtractorHLS();
        result.setLoggerModule(new UnitTestUriLoggerModule());
        return result;
    }

    @Test
    void extractsSegmentsKeysAndInitializationSections() throws Exception {
        CrawlURI curi = playlist("https://cdn.example.org/video/media.m3u8", "application/vnd.apple.mpegurl", """
                #EXTM3U
                #EXT-X-TARGETDURATION:10
                #EXT-X-KEY:METHOD=AES-128,URI="../keys/key"
                #EXT-X-MAP:URI="init.mp4",BYTERANGE="720@0"
                #EXTINF:10,
                segments/one
                #EXTINF:10,
                //other.example.org/two.ts?token=123
                #EXT-X-BYTERANGE:100@0
                segments/one
                #EXT-X-ENDLIST
                """);
        curi.setBaseURI(UURIFactory.getInstance("https://wrong.example.org/"));
        extractor.process(curi);
        assertEquals(Set.of("https://cdn.example.org/keys/key", "https://cdn.example.org/video/init.mp4",
                "https://cdn.example.org/video/segments/one", "https://other.example.org/two.ts?token=123"), urls(curi));
        assertTrue(curi.getOutLinks().stream().allMatch(link -> link.getPathFromSeed().endsWith("E")
                && link.getViaContext().toString().startsWith("hls/")));
        assertTrue(curi.hasBeenLinkExtracted());
        assertNoSideEffects(curi);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXT-X-KEY", "EXT-X-MAP", "EXT-X-MEDIA", "EXT-X-I-FRAME-STREAM-INF",
            "EXT-X-SESSION-DATA", "EXT-X-SESSION-KEY"})
    void extractsQuotedUriAttributesWithoutSplittingQuotedCommas(String tag) throws Exception {
        CrawlURI curi = playlist("https://example.org/master.m3u8", "audio/mpegurl",
                "#EXTM3U\n#" + tag + ":NAME=\"not,URI=bogus\",URI=\"resource?part=1,2\",DEFAULT=YES\n");
        extractor.process(curi);
        assertEquals(Set.of("https://example.org/resource?part=1,2"), urls(curi));
        assertNoSideEffects(curi);
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.org/api/playlist, application/vnd.apple.mpegurl",
            "https://example.org/api/playlist, AUDIO/MPEGURL; charset=utf-8",
            "https://example.org/api/playlist, application/x-mpegurl",
            "https://example.org/api/playlist, audio/x-mpegurl",
            "https://example.org/video.M3U8?v=2, text/plain",
            "https://example.org/video.m3u, ''"
    })
    void recognizesMimeTypesAndFilenames(String url, String type) throws Exception {
        CrawlURI curi = playlist(url, type, "\ufeff#EXTM3U\r\n#EXTINF:1,\r\n/segment.ts\r\n");
        extractor.process(curi);
        assertEquals(Set.of("https://example.org/segment.ts"), urls(curi));
        assertTrue(curi.hasBeenLinkExtracted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a playlist", "<html>error</html>", "#EXTM3U-invalid\nsegment.ts",
            "#EXTINF:1,\nsegment.ts"})
    void requiresPlaylistHeaderBeforeExtracting(String content) throws Exception {
        CrawlURI curi = playlist("https://example.org/video.m3u8", "audio/mpegurl", content);
        extractor.process(curi);
        assertTrue(curi.getOutLinks().isEmpty());
        assertFalse(curi.hasBeenLinkExtracted());
        assertNoSideEffects(curi);
    }

    @Test
    void skipsCommentsInvalidAttributesNonHttpUrlsAndUnexpandedVariables() throws Exception {
        CrawlURI curi = playlist("https://example.org/video.m3u8", "audio/mpegurl", """
                #EXTM3U
                # a comment mentioning https://example.org/not-a-resource
                #EXT-X-UNKNOWN:URI="unknown.ts"
                #EXT-X-MAP:X-URI="not-uri.ts"
                #EXT-X-MAP:URI=unquoted.ts
                #EXT-X-MAP:URI="broken.ts",BROKEN
                #EXT-X-MAP:NAME="unterminated,URI=not-real.ts
                #EXT-X-KEY:METHOD=AES-128,URI="data:application/octet-stream;base64,AA=="
                #EXT-X-MEDIA:URI="{$base}/audio.m3u8"
                {$base}/segment.ts
                javascript:alert(1)
                file:///tmp/local.ts
                https://cdn.example.org/real.ts
                """);
        extractor.process(curi);
        assertEquals(Set.of("https://cdn.example.org/real.ts"), urls(curi));
        assertNoSideEffects(curi);
    }

    @Test
    void respectsMaximumOutlinks() throws Exception {
        extractor.setExtractorParameters(new ExtractorParameters() {
            public int getMaxOutlinks() { return 2; }
            public boolean getExtractIndependently() { return false; }
            public boolean getExtract404s() { return false; }
        });
        CrawlURI curi = playlist("https://example.org/list.m3u8", "audio/mpegurl", "#EXTM3U\none.ts\ntwo.ts\nthree.ts\n");
        extractor.process(curi);
        assertEquals(Set.of("https://example.org/one.ts", "https://example.org/two.ts"), urls(curi));
        assertEquals(2, extractor.numberOfLinksExtracted.get());
    }

    @Test
    void followsHtmlToMasterPlaylistToMediaPlaylistToSegments() throws Exception {
        CrawlURI page = playlist("https://example.org/watch", "text/html",
                "<html><video src='https://cdn.example.org/video/master.m3u8'></video></html>");
        ExtractorHTML html = new ExtractorHTML();
        html.setLoggerModule(new UnitTestUriLoggerModule());
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        html.setMetadata(metadata);
        html.afterPropertiesSet();
        html.process(page);
        CrawlURI master = page.getOutLinks().stream().filter(link -> link.toString()
                .equals("https://cdn.example.org/video/master.m3u8")).findFirst().orElseThrow();
        setContent(master, "audio/mpegurl", "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1280000\nquality/media.m3u8\n");
        extractor.process(master);
        CrawlURI media = master.getOutLinks().iterator().next();
        assertEquals("https://cdn.example.org/video/quality/media.m3u8", media.toString());
        setContent(media, "audio/mpegurl", "#EXTM3U\n#EXTINF:10,\n../segments/one.ts\n");
        extractor.process(media);
        assertEquals(Set.of("https://cdn.example.org/video/segments/one.ts"), urls(media));
        assertNoSideEffects(media);
    }

    private CrawlURI playlist(String url, String type, String content) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(url), "",
                UURIFactory.getInstance("https://page.example.org/watch"), LinkContext.EMBED_MISC);
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
