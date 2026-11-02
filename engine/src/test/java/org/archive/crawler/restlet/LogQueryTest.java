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

package org.archive.crawler.restlet;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LogQueryTest {
    static final String PDF = "2024-01-15T12:00:00.123Z   200      12345 "
            + "http://www.Example.com:8080/docs/a%20b.pdf LLE http://www.example.com/ "
            + "application/pdf #042 20240115120000000+150 sha1:ABCDEF - "
            + "duplicate:digest,3t {\"warcFilename\":\"x.warc.gz\"}";
    static final String NOT_FOUND = "2024-01-15T12:00:01.000Z   404        512 "
            + "https://other.org/missing.html L https://example.com/ text/html "
            + "#001 20240115120001000+20 sha1:XYZ - -";
    static final String DNS = "2024-01-15T12:00:02.000Z    -6          - "
            + "dns:example.com P http://example.com/ unknown #003 - - - -";
    static final String SEED = "2024-01-15T12:00:03.000Z     1         54 "
            + "dns:Example.COM P http://example.com/ text/dns #003 "
            + "20240115120003000+5 sha1:Q - -";

    static boolean matches(String query, String line) throws Exception {
        return LogQuery.parse(query, true).matches(line);
    }

    @Test
    public void testBareTerms() throws Exception {
        assertTrue(matches("example", PDF));
        assertTrue(matches("EXAMPLE pdf", PDF));
        assertFalse(matches("example nothere", PDF));
        assertTrue(matches("-nothere", PDF));
        assertFalse(matches("-pdf", PDF));
        assertTrue(matches("", PDF));
        assertTrue(matches("   ", NOT_FOUND));
    }

    @Test
    public void testQuotedPhrase() throws Exception {
        assertTrue(matches("\"pdf LLE\"", PDF));
        assertFalse(matches("\"LLE pdf\"", PDF));
        assertTrue(matches("url:\"/a%20b.pdf\"", PDF));
        assertTrue(matches("-\"LLE pdf\"", PDF));
        assertTrue(matches("\"say \\\"hi\\\"\"", "they say \"hi\""));
        assertTrue(matches("line~\"x\\.warc\"", PDF));
    }

    @Test
    public void testStatus() throws Exception {
        assertTrue(matches("status:200", PDF));
        assertTrue(matches("status:2xx", PDF));
        assertTrue(matches("status:200..299", PDF));
        assertTrue(matches("status:404,200", PDF));
        assertTrue(matches("status:>=200", PDF));
        assertFalse(matches("status:>200", PDF));
        assertTrue(matches("status:<0", DNS));
        assertTrue(matches("status:-6", DNS));
        assertTrue(matches("status:-9999..-1", DNS));
        assertTrue(matches("-status:2xx", NOT_FOUND));
        // '=' is the same as ':' for numeric fields
        assertTrue(matches("status=200,404", PDF));
        assertTrue(matches("status=4xx", NOT_FOUND));
    }

    @Test
    public void testStatusErrors() {
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("status:abc", true));
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("status~^2", true));
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("status:", true));
    }

    @Test
    public void testSize() throws Exception {
        assertTrue(matches("size:12345", PDF));
        assertTrue(matches("size:>12344", PDF));
        assertFalse(matches("size:>12345", PDF));
        assertTrue(matches("size:>=12345", PDF));
        assertTrue(matches("size:<1KB", NOT_FOUND));
        assertTrue(matches("size:<=512", NOT_FOUND));
        assertTrue(matches("size:10K..13K", PDF));
        assertTrue(matches("size:13KiB..10kb", PDF));
        assertFalse(matches("size:10K..12K", PDF));
        assertTrue(matches("size:54,512", NOT_FOUND));
        assertTrue(matches("size=54,512", SEED));
        assertTrue(matches("size=>500", NOT_FOUND));
        // units are powers of 1024, and fractions are allowed
        assertTrue(matches("size:>1MB", "2024-01-15T12:00:00.123Z 200 1048577 "
                + "http://example.com/big L - application/zip #1 - sha1:A - -"));
        assertFalse(matches("size:>1MB", "2024-01-15T12:00:00.123Z 200 1048576 "
                + "http://example.com/big L - application/zip #1 - sha1:A - -"));
        assertTrue(matches("size:>1.5g", "2024-01-15T12:00:00.123Z 200 1610612737 "
                + "http://example.com/big L - application/zip #1 - sha1:A - -"));
        // larger than an int
        assertTrue(matches("size:>2GB", "2024-01-15T12:00:00.123Z 200 5000000000 "
                + "http://example.com/big L - application/zip #1 - sha1:A - -"));
        // '-' (no content) never matches a numeric size
        assertFalse(matches("size:<1", DNS));
        assertFalse(matches("size:>=0", DNS));
        assertTrue(matches("-size:>=0", DNS));
    }

    @Test
    public void testSizeErrors() {
        for (String q : new String[] {"size:abc", "size:1XB", "size:5iB",
                "size:>", "size:..5", "size~^5", "size:", "size:99999999T",
                "size:-5"}) {
            assertThrows(LogQuery.QueryException.class,
                    () -> LogQuery.parse(q, true), q);
        }
        assertThrows(LogQuery.QueryException.class,
                () -> LogQuery.parse("size:>1MB", false));
    }

    @Test
    public void testDuration() throws Exception {
        // PDF took 150ms, NOT_FOUND 20ms; DNS has no fetch time
        assertTrue(matches("duration:150", PDF));
        assertTrue(matches("duration:>100", PDF));
        assertFalse(matches("duration:>100", NOT_FOUND));
        assertTrue(matches("duration:>=150ms", PDF));
        assertTrue(matches("duration:<0.2s", PDF));
        assertFalse(matches("duration:<0.15s", PDF));
        assertTrue(matches("duration:0.1s..1s", PDF));
        assertTrue(matches("duration:20,150", NOT_FOUND));
        assertTrue(matches("duration=150", PDF));
        String slow = "2024-01-15T12:00:00.123Z 200 1 http://example.com/ L - "
                + "text/html #1 20240115120000000+5400000 sha1:A - -";
        assertTrue(matches("duration:>1h", slow));
        assertTrue(matches("duration:90m", slow));
        assertTrue(matches("duration:90min", slow));
        assertFalse(matches("duration:>=0", DNS));
        assertTrue(matches("-duration:>=0", DNS));
        for (String q : new String[] {"duration:fast", "duration:5d",
                "duration:-1", "duration:>", "duration~\\+1"}) {
            assertThrows(LogQuery.QueryException.class,
                    () -> LogQuery.parse(q, true), q);
        }
    }

    @Test
    public void testDepth() throws Exception {
        // PDF's hop path is LLE, NOT_FOUND's L; DNS is P
        assertTrue(matches("depth:3", PDF));
        assertTrue(matches("depth:>2", PDF));
        assertFalse(matches("depth:>2", NOT_FOUND));
        assertTrue(matches("depth:1..2", NOT_FOUND));
        assertTrue(matches("depth=1,3", DNS));
        String seed = "2024-01-15T12:00:00.123Z 200 1 http://example.com/ - - "
                + "text/html #1 20240115120000000+5 sha1:A - -";
        assertTrue(matches("depth:0", seed));
        // over 50 hops: the number left out, '+' and the last 50
        String deep = "2024-01-15T12:00:00.123Z 200 1 http://example.com/ 12+"
                + "L".repeat(50) + " - text/html #1 20240115120000000+5 sha1:A - -";
        assertTrue(matches("depth:62", deep));
        for (String q : new String[] {"depth:deep", "depth:1.5", "depth~L"}) {
            assertThrows(LogQuery.QueryException.class,
                    () -> LogQuery.parse(q, true), q);
        }
    }

    @Test
    public void testComparisonWithoutOperator() throws Exception {
        assertTrue(matches("size>10K", PDF));
        assertFalse(matches("size>10K", NOT_FOUND));
        assertTrue(matches("size<=512", NOT_FOUND));
        assertTrue(matches("status<0", DNS));
        assertTrue(matches("status>=200 duration>100", PDF));
        assertTrue(matches("depth<2", NOT_FOUND));
        assertTrue(matches("-size>1KB", NOT_FOUND));
        // only numeric fields; elsewhere it's text, as is an unknown field
        assertTrue(matches("url>x", "a url>x b"));
        assertTrue(matches("foo>5", "foo>5"));
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("size>", true));
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("size>1MB", false));
    }

    @Test
    public void testHops() throws Exception {
        assertTrue(matches("hops=LLE", PDF));
        assertTrue(matches("hops:*E", PDF));
        assertFalse(matches("hops:*E", NOT_FOUND));
        assertTrue(matches("hops~^L+$", NOT_FOUND));
        assertTrue(matches("hops:p", DNS));
        String seed = "2024-01-15T12:00:00.123Z 200 1 http://example.com/ - - "
                + "text/html #1 20240115120000000+5 sha1:A - -";
        assertTrue(matches("hops=-", seed));
    }

    @Test
    public void testVia() throws Exception {
        // NOT_FOUND was discovered from https://example.com/, PDF from
        // http://www.example.com/; SEED's via is a URL too
        assertTrue(matches("via:example.com/", NOT_FOUND));
        assertTrue(matches("via=https://example.com/", NOT_FOUND));
        assertFalse(matches("via=https://example.com", NOT_FOUND));
        assertTrue(matches("via~^http://www\\.", PDF));
        // the url column isn't the via
        assertFalse(matches("via:other.org", NOT_FOUND));
        assertFalse(matches("via:/docs/", PDF));

        assertTrue(matches("viahost:example.com", PDF));
        assertTrue(matches("viahost:example.com", NOT_FOUND));
        assertFalse(matches("viahost=example.com", PDF));
        assertTrue(matches("viahost=WWW.example.com", PDF));
        assertTrue(matches("viahost:*.example.com", PDF));
        assertFalse(matches("viahost:other.org", NOT_FOUND));
        // a seed has no via
        String seed = "2024-01-15T12:00:00.123Z 200 1 http://example.com/ - - "
                + "text/html #1 20240115120000000+5 sha1:A - -";
        assertFalse(matches("viahost:example.com", seed));
        assertTrue(matches("-viahost:example.com", seed));
        assertTrue(matches("via=-", seed));
    }

    @Test
    public void testUrl() throws Exception {
        assertTrue(matches("url:/docs/", PDF));
        assertTrue(matches("url:*.pdf", PDF));
        assertFalse(matches("url:*.html", PDF));
        assertTrue(matches("url:*/DOCS/*", PDF));
        assertTrue(matches("url~\\.pdf$", PDF));
        // regex is case-sensitive, ':' is not
        assertFalse(matches("url~DOCS", PDF));
        assertTrue(matches("url~^https?://[^/]+/docs/", PDF));
        assertTrue(matches("url=https://other.org/missing.html", NOT_FOUND));
        assertFalse(matches("url=other.org", NOT_FOUND));
        // via column isn't the url
        assertFalse(matches("url:www.example.com/", NOT_FOUND));
    }

    @Test
    public void testHost() throws Exception {
        assertTrue(matches("host:example.com", PDF));
        assertTrue(matches("host:www.example.com", PDF));
        assertTrue(matches("host:.example.com", PDF));
        assertFalse(matches("host:ample.com", PDF));
        assertFalse(matches("host:8080", PDF));
        assertTrue(matches("host=www.example.com", PDF));
        assertFalse(matches("host=example.com", PDF));
        assertTrue(matches("host:*.example.*", PDF));
        assertTrue(matches("host:example.com", DNS));
        assertTrue(matches("host=example.com", SEED));
        assertFalse(matches("host:example.com", NOT_FOUND));
        assertTrue(matches("-host:example.com", NOT_FOUND));
    }

    @Test
    public void testType() throws Exception {
        assertTrue(matches("type:pdf", PDF));
        assertTrue(matches("type:PDF", PDF));
        assertTrue(matches("type:html,pdf", PDF));
        assertFalse(matches("type:html,image", PDF));
        assertTrue(matches("type:image/*,application/*", PDF));
        assertTrue(matches("-type:html", PDF));
        assertFalse(matches("-type:html", NOT_FOUND));
        assertTrue(matches("type=text/html", NOT_FOUND));
    }

    @Test
    public void testAnnot() throws Exception {
        assertTrue(matches("annot:duplicate", PDF));
        assertTrue(matches("annot=3t", PDF));
        assertFalse(matches("annot=3", PDF));
        assertFalse(matches("annot:duplicate", NOT_FOUND));
        assertTrue(matches("-annot:duplicate", NOT_FOUND));
        assertFalse(matches("annot:-", NOT_FOUND));
    }

    @Test
    public void testCombined() throws Exception {
        String q = "status:2xx host:example.com -type:html annot:duplicate";
        assertTrue(matches(q, PDF));
        assertFalse(matches(q, NOT_FOUND));
        assertFalse(matches(q, DNS));
    }

    @Test
    public void testUrlsAsBareTerms() throws Exception {
        LogQuery q = LogQuery.parse("https://other.org/", true);
        assertTrue(q.matches(NOT_FOUND));
        assertFalse(q.matches(PDF));
        assertTrue(matches("dns:example.com", DNS));
        assertTrue(matches("/~user/", "GET /~user/ 200"));
    }

    @Test
    public void testUnknownField() throws Exception {
        // searched for as text
        LogQuery q = LogQuery.parse("stauts:404", true);
        assertFalse(q.matches(NOT_FOUND));
        assertTrue(q.matches("stauts:404 typo"));
    }

    @Test
    public void testNonCrawlLog() throws Exception {
        assertTrue(LogQuery.parse("foo line~ba+r", false).matches("foo baaar"));
        LogQuery.QueryException e = assertThrows(LogQuery.QueryException.class,
                () -> LogQuery.parse("status:404", false));
        assertTrue(e.getMessage().contains("crawl.log"));
    }

    @Test
    public void testFieldTermsOnNonCrawlLogLine() throws Exception {
        assertFalse(matches("status:200", "some other line"));
        assertTrue(matches("-status:200", "some other line"));
    }

    @Test
    public void testSyntaxErrors() {
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("url~(", true));
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("\"unclosed", true));
        assertThrows(LogQuery.QueryException.class, () -> LogQuery.parse("url:", true));
    }

    @Test
    public void testDeadline() throws Exception {
        LogQuery q = LogQuery.parse("line~(a+)+\\1b", false);
        q.setDeadline(System.nanoTime() + 50_000_000L);
        String evil = "a".repeat(40) + "c";
        assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                assertThrows(LogQuery.DeadlineExceededException.class,
                        () -> q.matches(evil)));
    }

    @Test
    public void testHighlights() throws Exception {
        // only positive whole-line terms are highlighted
        LogQuery q = LogQuery.parse("docs host:example.com status:2xx -type:pdf"
                + " -nothere line~a%20b", true);
        List<int[]> spans = q.highlights(PDF);
        assertEquals("docs", text(PDF, spans.get(0)));
        assertEquals("a%20b", text(PDF, spans.get(1)));
        assertEquals(2, spans.size());
        assertTrue(LogQuery.parse("annot:3t", true).highlights(PDF).isEmpty());
        // every occurrence, and empty regex matches are ignored
        assertEquals(2, LogQuery.parse("example", true).highlights(PDF).size());
        assertTrue(LogQuery.parse("line~x*", true).highlights("abc").isEmpty());

        // overlapping matches are merged
        spans = LogQuery.parse("exam ample", false).highlights("example");
        assertEquals(1, spans.size());
        assertArrayEquals(new int[] {0, 7}, spans.get(0));
    }

    static String text(String line, int[] span) {
        return line.substring(span[0], span[1]);
    }
}
