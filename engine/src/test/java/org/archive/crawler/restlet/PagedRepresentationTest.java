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

import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.restlet.Request;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Reference;
import org.restlet.representation.FileRepresentation;

import static org.junit.jupiter.api.Assertions.*;

public class PagedRepresentationTest {
    static final String LOG =
            "2024-01-15T12:00:00.123Z   200      12345 http://example.com/a.pdf L "
            + "http://example.com/ application/pdf #042 20240115120000000+150 sha1:A - -\n"
            + "2024-01-15T12:00:01.000Z   404        512 http://example.com/x?a=1&b=<2> L "
            + "http://example.com/ text/html #001 20240115120001000+20 sha1:B - -\n"
            + "2024-01-15T12:00:02.000Z   404        512 http://other.org/y L "
            + "http://example.com/ text/html #001 20240115120001000+20 sha1:C - -\n"
            + "2024-01-15T12:00:03.000Z   999          0 http://linkedin.example/z L "
            + "http://example.com/ text/html #001 20240115120001000+20 sha1:D - -\n"
            + "2024-01-15T12:00:04.000Z -9999          - http://example.com/w L "
            + "http://example.com/ unknown #001 - - - -\n";

    @TempDir
    File tempDir;

    String render(String fileName, String pos, String lines, String reverse,
            String q) throws Exception {
        File f = new File(tempDir, fileName);
        Files.write(f.toPath(), LOG.getBytes(StandardCharsets.UTF_8));
        EnhDirectoryResource resource = new EnhDirectoryResource();
        Request request = new Request(Method.GET,
                "http://localhost:8443/engine/job/j/jobdir/logs/" + fileName
                + "?format=paged&q=" + (q == null ? "" : Reference.encode(q)));
        request.setOriginalRef(request.getResourceRef());
        resource.setRequest(request);
        PagedRepresentation rep = new PagedRepresentation(
                new FileRepresentation(f, MediaType.TEXT_PLAIN), resource,
                pos, lines, reverse, q, null);
        StringWriter w = new StringWriter();
        rep.write(w);
        return w.toString();
    }

    @Test
    public void testUnfiltered() throws Exception {
        String html = render("crawl.log", null, null, null, null);
        assertTrue(html.contains("<abbr class='status-2xx' title='OK'>200</abbr>"), html);
        assertTrue(html.contains("a=1&amp;b=&lt;2&gt;"), html);
        assertFalse(html.contains("<mark>"));
        assertFalse(html.contains("searchSummary"));
        // codes unknown to both FetchStatusCodes and Jetty don't break rendering
        assertTrue(html.contains("<abbr class='' title='999'>999</abbr>"), html);
        assertTrue(html.contains("<abbr class='status-neg' title='-9999'>-9999</abbr>"), html);
        assertTrue(html.contains("&laquo;EOF&raquo;"), html);
    }

    @Test
    public void testFiltered() throws Exception {
        String html = render("crawl.log", null, null, null, "status:404 host:example.com b=<2");
        assertTrue(html.contains("1 matching lines in bytes 0-"), html);
        assertFalse(html.contains("a.pdf"));
        assertFalse(html.contains("other.org"));
        // only the bare word is highlighted, html escaped
        assertTrue(html.contains("<abbr class='status-4xx' title='S_NOT_FOUND'>404</abbr>"), html);
        assertTrue(html.contains("http://example.com/x?a=1&amp;<mark>b=&lt;2</mark>&gt;"), html);
        // query is echoed escaped and carried in navigation links
        assertTrue(html.contains("value='status:404 host:example.com b=&lt;2'"), html);
        assertTrue(html.contains("q=status%3A404"), html);
        assertTrue(html.contains("'>clear</a>"), html);
    }

    @Test
    public void testHighlightAcrossStatus() throws Exception {
        // a match spanning the status column is split around the abbr
        String html = render("crawl.log", null, null, null, "line~\"Z +404 +5\"");
        assertTrue(html.contains("2024-01-15T12:00:01.000<mark>Z   </mark>"
                + "<abbr class='status-4xx' title='S_NOT_FOUND'><mark>404</mark></abbr>"
                + "<mark>        5</mark>12 "), html);
    }

    @Test
    public void testFilteredReversed() throws Exception {
        String html = render("crawl.log", "-1", "-128", "y", "status:404");
        int other = html.indexOf("other.org");
        int example = html.indexOf("x?a=1");
        assertTrue(other > 0 && example > 0 && other < example, html);
        assertTrue(html.contains("2 matching lines"), html);
        assertTrue(html.contains("name='reverse' value='y'"), html);
    }

    @Test
    public void testInvalidQuery() throws Exception {
        String html = render("crawl.log", null, null, null, "url~(<b>");
        assertTrue(html.contains("Invalid search:</b> invalid regex &#39;(&lt;b&gt;&#39;"), html);
        assertFalse(html.contains("a.pdf"));
    }

    @Test
    public void testUnknownField() throws Exception {
        // searched for as text, without a warning
        String html = render("crawl.log", null, null, null, "stauts:404");
        assertTrue(html.contains("0 matching lines"), html);
    }

    @Test
    public void testFilteredLongLine() throws Exception {
        File f = new File(tempDir, "big.log");
        Files.write(f.toPath(), ("short\n" + "y".repeat(
                FilteredLineScanner.MAX_LINE_LENGTH + 10) + "\n")
                .getBytes(StandardCharsets.UTF_8));
        String html = renderFile(f, null, null, null, "y", null);
        assertTrue(html.contains("1 matching lines"), html);
    }

    String renderFile(File f, String pos, String lines, String reverse,
            String q, String all) throws Exception {
        EnhDirectoryResource resource = new EnhDirectoryResource();
        Request request = new Request(Method.GET,
                "http://localhost:8443/engine/job/j/jobdir/logs/" + f.getName()
                + "?format=paged");
        request.setOriginalRef(request.getResourceRef());
        resource.setRequest(request);
        StringWriter w = new StringWriter();
        new PagedRepresentation(new FileRepresentation(f, MediaType.TEXT_PLAIN),
                resource, pos, lines, reverse, q, all).write(w);
        return w.toString();
    }

    @Test
    public void testStatusTooLongForInt() throws Exception {
        File f = new File(tempDir, "crawl.log");
        Files.write(f.toPath(), "2024-01-15T12:00:00.123Z 99999999999 x\n"
                .getBytes(StandardCharsets.UTF_8));
        String html = renderFile(f, null, null, null, null, null);
        assertTrue(html.contains("2024-01-15T12:00:00.123Z 99999999999 x"), html);
        html = renderFile(f, null, null, null, "x", null);
        assertTrue(html.contains("2024-01-15T12:00:00.123Z 99999999999 <mark>x</mark>"), html);
    }

    @Test
    public void testCheckpointLogs() throws Exception {
        String[] lines = LOG.split("\n");
        File cp1 = new File(tempDir, "crawl.log.cp00001-20240115120000");
        File cp2 = new File(tempDir, "crawl.log.cp00002-20240115130000");
        File active = new File(tempDir, "crawl.log");
        Files.write(cp1.toPath(), (lines[0] + "\n" + lines[1] + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(cp2.toPath(), (lines[2] + "\n").getBytes(StandardCharsets.UTF_8));
        Files.write(active.toPath(), (lines[3] + "\n" + lines[4] + "\n").getBytes(StandardCharsets.UTF_8));
        long cpLength = cp1.length() + cp2.length();

        // single file view offers the toggle, carrying the position over
        String html = renderFile(active, "5", null, null, null, null);
        assertTrue(html.contains("all=y'>include 2 checkpoint logs</a>"), html);
        assertTrue(html.contains("pos=" + (cpLength + 5) + "&amp;all=y"), html);
        assertFalse(html.contains("fileMarker"));
        assertFalse(html.contains("a.pdf"));

        // combined view: all lines, each run labelled with its file
        html = renderFile(active, null, null, null, null, "y");
        assertTrue(html.contains("Including all 3 generations of this log:"), html);
        int m1 = html.indexOf("&#x2500;&#x2500; crawl.log.cp00001-20240115120000");
        int pdf = html.indexOf("a.pdf");
        int m2 = html.indexOf("&#x2500;&#x2500; crawl.log.cp00002-20240115130000");
        int other = html.indexOf("other.org/y");
        int m3 = html.indexOf("&#x2500;&#x2500; crawl.log &#x2500;");
        int last = html.indexOf("example.com/w");
        assertTrue(0 < m1 && m1 < pdf && pdf < m2 && m2 < other && other < m3
                && m3 < last, html);
        assertTrue(html.contains("bytes 0-" + (cpLength + active.length())), html);
        assertTrue(html.contains("'>view this file only</a>"), html);
        assertTrue(html.contains("name='all' value='y'"), html);

        // search spans the files; links keep the combined view
        html = renderFile(active, null, null, null, "status:404", "y");
        assertTrue(html.contains("2 matching lines"), html);
        assertTrue(html.contains("x?a=1") && html.contains("other.org"), html);
        assertTrue(html.contains("q=status%3A404&amp;all=y"), html);

        // reversed, newest first, still labelled
        html = renderFile(active, "-1", "-128", "y", null, "y");
        assertTrue(html.indexOf("example.com/w") < html.indexOf("a.pdf"), html);
        assertTrue(html.indexOf("&#x2500;&#x2500; crawl.log &#x2500;")
                < html.indexOf("&#x2500;&#x2500; crawl.log.cp00001"), html);

        // back to the single file, the position is converted back
        html = renderFile(active, Long.toString(cpLength + 5), null, null, null, "y");
        assertTrue(html.contains("?format=paged&amp;pos=5'>view this file only</a>"), html);

        // a rotated crawl.log is still treated as a crawl.log
        html = renderFile(cp1, null, null, null, "status:200", null);
        assertTrue(html.contains("<abbr class='status-2xx' title='OK'>200</abbr>"), html);
        assertTrue(html.contains("include 2 checkpoint logs"), html);
    }

    @Test
    public void testQuotesEscaped() throws Exception {
        // a quote in the query can't break out of the search box's value
        String html = render("crawl.log", null, null, null, "' autofocus onfocus=alert(1) x='");
        assertTrue(html.contains("value='&#39; autofocus onfocus=alert(1) x=&#39;'"), html);
        assertFalse(html.contains("' autofocus"), html);
    }

    @Test
    public void testOtherLog() throws Exception {
        String html = render("alerts.log", null, null, null, "other");
        assertTrue(html.contains("1 matching lines"), html);
        assertTrue(html.contains("http://<mark>other</mark>.org/y"), html);
        assertFalse(html.contains("<abbr"));
        String error = render("alerts.log", null, null, null, "status:404");
        assertTrue(error.contains("only available when viewing crawl.log"), error);
    }
}
