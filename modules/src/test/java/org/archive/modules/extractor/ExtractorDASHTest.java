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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;

public class ExtractorDASHTest extends ContentExtractorTestBase {

    private static final String MPD_START = "<MPD xmlns='urn:mpeg:dash:schema:mpd:2011' " +
            "type='static' mediaPresentationDuration='PT10S'>";

    @Override
    protected Extractor makeExtractor() {
        ExtractorDASH extractor = new ExtractorDASH();
        extractor.setLoggerModule(new UnitTestUriLoggerModule());
        return extractor;
    }

    @Test
    public void testNestedBaseURLs() throws Exception {
        assertEquals(List.of(
                        "E https://cdn.example.com/audio/en.mp4 BaseURL",
                        "E https://example.org/media/period1/video/1080.mp4 BaseURL",
                        "E https://example.org/media/period1/video/720.mp4 BaseURL"),
                extract(MPD_START +
                        "<BaseURL>media/</BaseURL>" +
                        "<Period><BaseURL>period1/</BaseURL>" +
                        "<AdaptationSet mimeType='video/mp4'><BaseURL>video/</BaseURL>" +
                        "<Representation id='1' bandwidth='5000000'>" +
                        "<BaseURL>1080.mp4</BaseURL>" +
                        "<SegmentBase indexRange='800-1999'>" +
                        "<Initialization range='0-799'/></SegmentBase>" +
                        "</Representation>" +
                        "<Representation id='2' bandwidth='2000000'>" +
                        "<BaseURL>720.mp4</BaseURL><BaseURL>https://backup.example/720.mp4" +
                        "</BaseURL></Representation>" +
                        "</AdaptationSet>" +
                        "<AdaptationSet mimeType='audio/mp4'>" +
                        "<BaseURL>https://cdn.example.com/audio/</BaseURL>" +
                        "<Representation id='a'><BaseURL>en.mp4</BaseURL></Representation>" +
                        "</AdaptationSet>" +
                        "</Period></MPD>"));
    }

    @Test
    public void testRepresentationWithoutBaseURLIgnored() throws Exception {
        // its only file would be the manifest itself
        assertEquals(List.of(),
                extract(MPD_START + "<Period><AdaptationSet><Representation id='1'/>" +
                        "</AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentList() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/v/init.mp4 Initialization/@sourceURL",
                        "E https://example.org/v/seg1.m4s SegmentURL/@media",
                        "E https://example.org/v/seg2.m4s SegmentURL/@media",
                        "E https://example.org/whole.mp4 SegmentURL/@media"),
                extract(MPD_START +
                        "<Period><AdaptationSet>" +
                        "<Representation id='1'><BaseURL>v/</BaseURL>" +
                        "<SegmentList duration='5'>" +
                        "<Initialization sourceURL='init.mp4'/>" +
                        "<SegmentURL media='seg1.m4s'/><SegmentURL media='seg2.m4s'/>" +
                        "</SegmentList></Representation>" +
                        "<Representation id='2'><BaseURL>whole.mp4</BaseURL>" +
                        "<SegmentList duration='5'>" +
                        "<SegmentURL mediaRange='0-999'/><SegmentURL mediaRange='1000-1999'/>" +
                        "</SegmentList></Representation>" +
                        "</AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentTemplateWithDuration() throws Exception {
        // 10 seconds in 4 second segments is 3 segments
        assertEquals(List.of(
                        "E https://example.org/v/720/init.mp4 SegmentTemplate/@initialization",
                        "E https://example.org/v/720/seg-00005.m4s SegmentTemplate/@media",
                        "E https://example.org/v/720/seg-00006.m4s SegmentTemplate/@media",
                        "E https://example.org/v/720/seg-00007.m4s SegmentTemplate/@media"),
                extract(MPD_START +
                        "<Period><AdaptationSet>" +
                        "<SegmentTemplate timescale='1000' duration='4000' startNumber='5' " +
                        "initialization='$RepresentationID$/init.mp4' " +
                        "media='$RepresentationID$/seg-$Number%05d$.m4s'/>" +
                        "<Representation id='720'><BaseURL>v/</BaseURL></Representation>" +
                        "</AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentTemplateWithDurationAndEndNumber() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/1.m4s SegmentTemplate/@media",
                        "E https://example.org/2.m4s SegmentTemplate/@media"),
                extract("<MPD xmlns='urn:mpeg:dash:schema:mpd:2011'>" +
                        "<Period><AdaptationSet><Representation id='1'>" +
                        "<SegmentTemplate duration='2' endNumber='2' media='$Number$.m4s'/>" +
                        "</Representation></AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentTemplateWithUnknownDuration() throws Exception {
        assertEquals(List.of("E https://example.org/init.mp4 SegmentTemplate/@initialization"),
                extract("<MPD xmlns='urn:mpeg:dash:schema:mpd:2011'>" +
                        "<Period><AdaptationSet><Representation id='1'>" +
                        "<SegmentTemplate duration='2' initialization='init.mp4' " +
                        "media='$Number$.m4s'/>" +
                        "</Representation></AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentTimeline() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/a/128000/0.m4a SegmentTemplate/@media",
                        "E https://example.org/a/128000/192000.m4a SegmentTemplate/@media",
                        "E https://example.org/a/128000/384000.m4a SegmentTemplate/@media",
                        "E https://example.org/a/128000/500000.m4a SegmentTemplate/@media",
                        "E https://example.org/a/128000/init.m4a SegmentTemplate/@initialization",
                        "E https://example.org/a/64000/0.m4a SegmentTemplate/@media",
                        "E https://example.org/a/64000/192000.m4a SegmentTemplate/@media",
                        "E https://example.org/a/64000/384000.m4a SegmentTemplate/@media",
                        "E https://example.org/a/64000/500000.m4a SegmentTemplate/@media",
                        "E https://example.org/a/64000/init.m4a SegmentTemplate/@initialization"),
                extract(MPD_START +
                        "<Period><AdaptationSet><BaseURL>a/</BaseURL>" +
                        "<SegmentTemplate timescale='48000' " +
                        "initialization='$Bandwidth$/init.m4a' media='$Bandwidth$/$Time$.m4a'>" +
                        "<SegmentTimeline>" +
                        "<S d='192000' r='1'/><S d='116000'/><S t='500000' d='100000'/>" +
                        "</SegmentTimeline></SegmentTemplate>" +
                        "<Representation id='hi' bandwidth='128000'/>" +
                        "<Representation id='lo' bandwidth='64000'/>" +
                        "</AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentTimelineNegativeRepeat() throws Exception {
        // repeats until the next S's start time, then until the end of the 10 second Period
        assertEquals(List.of(
                        "E https://example.org/0.m4s SegmentTemplate/@media",
                        "E https://example.org/2.m4s SegmentTemplate/@media",
                        "E https://example.org/4.m4s SegmentTemplate/@media",
                        "E https://example.org/6.m4s SegmentTemplate/@media",
                        "E https://example.org/9.m4s SegmentTemplate/@media"),
                extract(MPD_START +
                        "<Period><AdaptationSet><Representation id='1'>" +
                        "<SegmentTemplate media='$Time$.m4s'><SegmentTimeline>" +
                        "<S t='0' d='2' r='-1'/><S t='6' d='3' r='-1'/>" +
                        "</SegmentTimeline></SegmentTemplate>" +
                        "</Representation></AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentTimelineWithNumber() throws Exception {
        assertEquals(List.of(
                        "E https://example.org/s3.m4s SegmentTemplate/@media",
                        "E https://example.org/s4.m4s SegmentTemplate/@media",
                        "E https://example.org/s5.m4s SegmentTemplate/@media"),
                extract(MPD_START +
                        "<Period><AdaptationSet><Representation id='1'>" +
                        "<SegmentTemplate startNumber='3' media='s$Number$.m4s'>" +
                        "<SegmentTimeline><S d='2' r='2'/></SegmentTimeline>" +
                        "</SegmentTemplate></Representation></AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testSegmentTemplateInheritance() throws Exception {
        // the Representation's template overrides startNumber and inherits the rest
        assertEquals(List.of(
                        "E https://example.org/v/a/1.m4s SegmentTemplate/@media",
                        "E https://example.org/v/a/init.mp4 Initialization/@sourceURL",
                        "E https://example.org/v/b/7.m4s SegmentTemplate/@media",
                        "E https://example.org/v/b/init.mp4 Initialization/@sourceURL"),
                extract(MPD_START +
                        "<Period><BaseURL>v/</BaseURL>" +
                        "<SegmentTemplate media='$Number$.m4s' duration='10'>" +
                        "<Initialization sourceURL='init.mp4'/></SegmentTemplate>" +
                        "<AdaptationSet>" +
                        "<Representation id='a'><BaseURL>a/</BaseURL></Representation>" +
                        "<Representation id='b'><BaseURL>b/</BaseURL>" +
                        "<SegmentTemplate startNumber='7'/></Representation>" +
                        "</AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testMultiplePeriods() throws Exception {
        // 4 and 6 seconds long, from Period@duration and from mediaPresentationDuration
        assertEquals(List.of(
                        "E https://example.org/p1/1.m4s SegmentTemplate/@media",
                        "E https://example.org/p1/2.m4s SegmentTemplate/@media",
                        "E https://example.org/p2/1.m4s SegmentTemplate/@media",
                        "E https://example.org/p2/2.m4s SegmentTemplate/@media",
                        "E https://example.org/p2/3.m4s SegmentTemplate/@media"),
                extract(MPD_START +
                        "<Period id='p1' duration='PT4S'><BaseURL>p1/</BaseURL>" +
                        "<AdaptationSet><Representation id='1'>" +
                        "<SegmentTemplate duration='2' media='$Number$.m4s'/>" +
                        "</Representation></AdaptationSet></Period>" +
                        "<Period id='p2'><BaseURL>p2/</BaseURL>" +
                        "<AdaptationSet><Representation id='1'>" +
                        "<SegmentTemplate duration='2' media='$Number$.m4s'/>" +
                        "</Representation></AdaptationSet></Period>" +
                        "</MPD>"));
    }

    @Test
    public void testMaxUrlsPerManifest() throws Exception {
        ((ExtractorDASH) extractor).setMaxUrlsPerManifest(3);
        // a huge repeat count only generates up to the limit
        assertEquals(List.of(
                        "E https://example.org/init.mp4 SegmentTemplate/@initialization",
                        "E https://example.org/s1.m4s SegmentTemplate/@media",
                        "E https://example.org/s2.m4s SegmentTemplate/@media"),
                extract(MPD_START +
                        "<Period><AdaptationSet>" +
                        "<Representation id='1'>" +
                        "<SegmentTemplate initialization='init.mp4' media='s$Number$.m4s'>" +
                        "<SegmentTimeline><S d='1' r='2000000000'/></SegmentTimeline>" +
                        "</SegmentTemplate></Representation>" +
                        "<Representation id='2'><BaseURL>other.mp4</BaseURL></Representation>" +
                        "</AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testDuplicatesCountTowardsMaxUrlsPerManifest() throws Exception {
        ((ExtractorDASH) extractor).setMaxUrlsPerManifest(1000);
        // a template without $Number$ or $Time$ gives the same URL for every segment
        assertEquals(List.of("E https://example.org/same.m4s SegmentTemplate/@media"),
                extract(MPD_START +
                        "<Period><AdaptationSet><Representation id='1'>" +
                        "<SegmentTemplate media='same.m4s'>" +
                        "<SegmentTimeline><S d='1' r='2000000000'/></SegmentTimeline>" +
                        "</SegmentTemplate></Representation>" +
                        "<Representation id='2'><BaseURL>after-limit.mp4</BaseURL>" +
                        "</Representation></AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testDynamicManifestSkipped() throws Exception {
        CrawlURI curi = makeCrawlURI("<MPD xmlns='urn:mpeg:dash:schema:mpd:2011' " +
                "type='dynamic' availabilityStartTime='2026-01-01T00:00:00Z'>" +
                "<Period start='PT0S'><AdaptationSet><Representation id='1'>" +
                "<SegmentTemplate initialization='init.mp4' media='$Number$.m4s' " +
                "duration='2'/></Representation></AdaptationSet></Period></MPD>",
                "application/dash+xml");
        extractor.process(curi);
        assertEquals(List.of(), links(curi));
        assertTrue(curi.hasBeenLinkExtracted());
        assertTrue(curi.getNonFatalFailures().isEmpty());
    }

    @Test
    public void testOtherNamespacesIgnored() throws Exception {
        assertEquals(List.of("E https://example.org/a.mp4 BaseURL"),
                extract("<MPD xmlns='urn:mpeg:dash:schema:mpd:2011' xmlns:x='urn:x' " +
                        "mediaPresentationDuration='PT10S'><Period><AdaptationSet>" +
                        "<Representation id='1'><BaseURL>a.mp4</BaseURL>" +
                        "<x:BaseURL>b.mp4</x:BaseURL></Representation>" +
                        "<x:Representation id='2'><BaseURL>c.mp4</BaseURL></x:Representation>" +
                        "</AdaptationSet></Period></MPD>"));
    }

    @Test
    public void testNoNamespace() throws Exception {
        assertEquals(List.of("E https://example.org/a.mp4 BaseURL"),
                extract("<MPD><Period><AdaptationSet><Representation id='1'>" +
                        "<BaseURL>a.mp4</BaseURL></Representation></AdaptationSet></Period>" +
                        "</MPD>"));
    }

    @Test
    public void testExternalEntityNotLoaded() throws Exception {
        assertEquals(List.of("E https://example.org/a.mp4 BaseURL"),
                extract("<!DOCTYPE MPD [<!ENTITY x SYSTEM 'file:///etc/passwd'>]>" +
                        MPD_START + "<ProgramInformation><Title>&x;</Title>" +
                        "</ProgramInformation><Period><AdaptationSet><Representation id='1'>" +
                        "<BaseURL>a.mp4</BaseURL></Representation></AdaptationSet></Period>" +
                        "</MPD>"));
    }

    @Test
    public void testMalformedKeepsEarlierLinks() throws Exception {
        CrawlURI curi = makeCrawlURI(MPD_START + "<Period><AdaptationSet>" +
                "<Representation id='1'><BaseURL>a.mp4</BaseURL></Representation>" +
                "<Representation id='2'><BaseURL>b.mp4<oops attr=unquoted/></BaseURL>" +
                "</Representation></AdaptationSet></Period></MPD>",
                "application/dash+xml");
        extractor.process(curi);
        // the unfinished BaseURL isn't used
        assertEquals(List.of("E https://example.org/a.mp4 BaseURL"), links(curi));
        assertTrue(curi.hasBeenLinkExtracted());
        assertEquals(1, curi.getNonFatalFailures().size());
    }

    @Test
    public void testMaxSizeToParse() throws Exception {
        String start = MPD_START + "<Period><AdaptationSet>" +
                "<Representation id='1'><BaseURL>a.mp4</BaseURL></Representation>";
        ((ExtractorDASH) extractor).setMaxSizeToParse(start.length() + 10);
        CrawlURI curi = makeCrawlURI(start +
                "<Representation id='2'><BaseURL>b.mp4</BaseURL></Representation>" +
                "</AdaptationSet></Period></MPD>", "application/dash+xml");
        extractor.process(curi);
        assertEquals(List.of("E https://example.org/a.mp4 BaseURL"), links(curi));
        assertTrue(curi.hasBeenLinkExtracted());
        assertTrue(curi.getNonFatalFailures().isEmpty(), curi.getNonFatalFailures().toString());
    }

    @Test
    public void testShouldExtract() throws Exception {
        ExtractorDASH dash = (ExtractorDASH) extractor;
        String content = MPD_START + "</MPD>";
        assertTrue(dash.shouldExtract(makeCrawlURI(content, "application/dash+xml")));
        assertTrue(dash.shouldExtract(makeCrawlURI(content, "Application/DASH+XML; q=1")));
        assertTrue(dash.shouldExtract(makeCrawlURI(content, "video/vnd.mpeg.dash.mpd")));
        assertTrue(dash.shouldExtract(makeCrawlURI("https://example.org/a/Manifest.MPD",
                content, "application/octet-stream")));
        assertFalse(dash.shouldExtract(makeCrawlURI("https://example.org/feed.xml", content,
                "application/xml")));
    }

    @Test
    public void testNonMpdRootLeftToOtherExtractors() throws Exception {
        String[] documents = {
                "<rss><channel><link>https://example.org/post</link></channel></rss>",
                "<x:MPD xmlns:x='urn:x'><BaseURL>a.mp4</BaseURL></x:MPD>",
                "<html><body>Not found</body></html>",
                "not xml",
        };
        for (String document : documents) {
            CrawlURI curi = makeCrawlURI("https://example.org/manifest.mpd", document,
                    "text/xml");
            extractor.process(curi);
            assertEquals(List.of(), links(curi), document);
            assertFalse(curi.hasBeenLinkExtracted(), document);
            assertTrue(curi.getNonFatalFailures().isEmpty(), document);
        }
    }

    @Test
    public void testExpandTemplate() {
        assertEquals("v1/00042-90000-$.m4s", ExtractorDASH.expandTemplate(
                "v$RepresentationID$/$Number%05d$-$Time$-$$.m4s", "1", "800", 42L, 90000L));
        assertEquals("0000000800/1.m4s", ExtractorDASH.expandTemplate(
                "$Bandwidth%010d$/$Number%01d$.m4s", "1", "800", 1L, 0L));
        assertNull(ExtractorDASH.expandTemplate("$Number$.m4s", "1", "800", null, null));
        assertNull(ExtractorDASH.expandTemplate("$SubNumber$.m4s", "1", "800", 1L, 0L));
        assertNull(ExtractorDASH.expandTemplate("$RepresentationID%02d$", "1", "800", 1L, 0L));
        assertNull(ExtractorDASH.expandTemplate("$Bandwidth$", "1", null, 1L, 0L));
        assertNull(ExtractorDASH.expandTemplate("$Number%0999999999d$", "1", "800", 1L, 0L));
    }

    @Test
    public void testParseDuration() {
        assertEquals(634.566, ExtractorDASH.parseDuration("PT634.566S"), 1e-9);
        assertEquals(3723.5, ExtractorDASH.parseDuration("PT1H2M3.5S"), 1e-9);
        assertEquals(90061, ExtractorDASH.parseDuration("P1DT1H1M1S"), 1e-9);
        assertEquals(210, ExtractorDASH.parseDuration("P0Y0M0DT0H3M30.000S"), 1e-9);
        assertTrue(Double.isNaN(ExtractorDASH.parseDuration(null)));
        assertTrue(Double.isNaN(ExtractorDASH.parseDuration("P")));
        assertTrue(Double.isNaN(ExtractorDASH.parseDuration("PT")));
        assertTrue(Double.isNaN(ExtractorDASH.parseDuration("10 seconds")));
        assertTrue(Double.isNaN(ExtractorDASH.parseDuration("PT1.2.3S")));
    }

    private List<String> extract(String mpd) throws Exception {
        CrawlURI curi = makeCrawlURI(mpd, "application/dash+xml");
        extractor.process(curi);
        assertTrue(curi.getNonFatalFailures().isEmpty(), curi.getNonFatalFailures().toString());
        assertTrue(curi.hasBeenLinkExtracted());
        return links(curi);
    }

    private CrawlURI makeCrawlURI(String content, String contentType) throws Exception {
        return makeCrawlURI("https://example.org/manifest.mpd", content, contentType);
    }

    private CrawlURI makeCrawlURI(String uri, String content, String contentType) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(uri));
        curi.setContentType(contentType);
        curi.setRecorder(createRecorder(content, "UTF-8"));
        curi.setContentSize(content.getBytes(StandardCharsets.UTF_8).length);
        curi.setFetchStatus(200);
        return curi;
    }

    /**
     * Outlinks formatted as "hop uri context", sorted.
     */
    private static List<String> links(CrawlURI curi) {
        List<String> links = new ArrayList<>();
        for (CrawlURI link : curi.getOutLinks()) {
            links.add(link.getLastHop() + " " + link.getURI() + " " + link.getViaContext());
        }
        Collections.sort(links);
        return links;
    }
}
