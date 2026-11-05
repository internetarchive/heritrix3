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
package org.archive.modules.processor;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.modules.net.CrawlServer;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class Soft404DetectorTest {

    @TempDir
    Path tempDir;

    private DefaultServerCache serverCache;
    private Soft404Detector detector;

    @BeforeEach
    void setUp() {
        serverCache = new DefaultServerCache();
        detector = new Soft404Detector();
        detector.setServerCache(serverCache);
    }

    @Test
    void schedulesOneProbePerServerOnFirstSuccess() throws Exception {
        CrawlURI robots = curi("https://example.com/robots.txt", 200);
        detector.process(robots);

        assertEquals(1, robots.getOutLinks().size());
        CrawlURI probe = robots.getOutLinks().iterator().next();
        assertTrue(probe.getURI().matches("https://example\\.com"
                + Soft404Detector.PROBE_PATH_PREFIX + "[a-z0-9]+/[a-z0-9]+"));
        assertEquals("P", probe.getPathFromSeed());
        assertEquals(SchedulingConstants.HIGH, probe.getSchedulingDirective());
        assertTrue(server().isSoft404ProbeScheduled());
        assertFalse(server().hasSoft404Fingerprint());

        CrawlURI page = curi("https://example.com/page", 200);
        detector.process(page);
        assertTrue(page.getOutLinks().isEmpty());
        assertEquals(1, detector.getProbesScheduled());
    }

    @Test
    void waitsForSuccessfulResponseBeforeProbing() throws Exception {
        CrawlURI robots = curi("https://example.com/robots.txt", 404);
        detector.process(robots);

        assertTrue(robots.getOutLinks().isEmpty());
        assertFalse(server().isSoft404ProbeScheduled());
    }

    @Test
    void probeWithNotFoundResponseRecordsNoFingerprint() throws Exception {
        CrawlURI probe = probe();
        probe.setFetchStatus(404);
        recordResponse(probe, "<h1>Not Found</h1>");
        detector.process(probe);

        assertFalse(server().hasSoft404Fingerprint());
        assertTrue(probe.getAnnotations().contains(Soft404Detector.PROBE_ANNOTATION));

        CrawlURI page = curi("https://example.com/a/b", 200);
        recordResponse(page, "<h1>Not Found</h1>");
        detector.process(page);
        assertFalse(page.getAnnotations().contains(Soft404Detector.ANNOTATION));
        assertTrue(page.getOutLinks().isEmpty());
        assertEquals(1, detector.getProbesScheduled());
    }

    @Test
    void detectsSoft404WithVolatileTokensAndEchoedPath() throws Exception {
        CrawlURI probe = probe();
        recordResponse(probe, spaShell(probe.getUURI().getPath(),
                "v1.eyJwcm9qZWN0X2lkIjoibG92cF8xcDNh.ULHj8Snk4GlGG5Teb57oUauBA3QoDzudjWFrtVjiBUg"));
        assertEquals(ProcessResult.FINISH, detector.process(probe));
        assertTrue(server().hasSoft404Fingerprint());

        CrawlURI page = curi("https://example.com/_ignition/horizon/api/jobs"
                + "/_ignition/salt-edge/get-consent", 200);
        recordResponse(page, spaShell(page.getUURI().getPath(),
                "v1.eyJwcm9qZWN0X2lkIjoibG92cF8xcDNh.qvhP-YaN5PMgQ03zCjfJXPjxuudXmaa8koEqIeJ4nVc"));
        assertEquals(ProcessResult.FINISH, detector.process(page));

        assertTrue(page.getAnnotations().contains(Soft404Detector.ANNOTATION));
        assertEquals(1, detector.getSoft404sDetected());
    }

    @Test
    void annotatesWithoutSkippingExtractionWhenDisabled() throws Exception {
        detector.setSkipExtraction(false);
        String body = "<html><title>Not here</title><a href=\"x/y\">x</a></html>";
        CrawlURI probe = probe();
        recordResponse(probe, body);
        assertEquals(ProcessResult.PROCEED, detector.process(probe));

        CrawlURI page = curi("https://example.com/a/b", 200);
        recordResponse(page, body);
        assertEquals(ProcessResult.PROCEED, detector.process(page));

        assertTrue(page.getAnnotations().contains(Soft404Detector.ANNOTATION));
        assertEquals(1, detector.getSoft404sDetected());
    }

    @Test
    void detectsSoft404EchoingPercentEncodedUrl() throws Exception {
        // e.g. www.meetup.com: <a href="/login/?returnUri=https%3A%2F%2Fwww.meetup.com%2F...">
        CrawlURI probe = probe();
        recordResponse(probe, loginLink(probe));
        detector.process(probe);

        CrawlURI page = curi("https://example.com/a/nonexistent-article/", 200);
        recordResponse(page, loginLink(page));
        detector.process(page);

        assertTrue(page.getAnnotations().contains(Soft404Detector.ANNOTATION));
    }

    @Test
    void probeRedirectThatKeepsPathIsTreatedAsProbe() throws Exception {
        // e.g. www.spotify.com: /missing -> 302 /kr-ko/missing/ -> 200
        CrawlURI probe = probe();
        probe.setFetchStatus(302);
        probe.putHttpResponseHeader("Location",
                "https://example.com/kr-ko" + probe.getUURI().getPath() + "/");
        // must proceed so ExtractorHTTP follows the redirect
        assertEquals(ProcessResult.PROCEED, detector.process(probe));
        assertFalse(server().hasSoft404Fingerprint());

        String body = "<html><title>Home</title><a href=\"premium/\">Premium</a></html>";
        CrawlURI target = curi("https://example.com/kr-ko" + probe.getUURI().getPath() + "/", 200);
        recordResponse(target, body);
        assertEquals(ProcessResult.FINISH, detector.process(target));
        assertTrue(server().hasSoft404Fingerprint());
        assertTrue(target.getAnnotations().contains(Soft404Detector.PROBE_ANNOTATION));

        CrawlURI page = curi("https://example.com/uk/icantypeanythinghere/", 200);
        recordResponse(page, body);
        detector.process(page);
        assertTrue(page.getAnnotations().contains(Soft404Detector.ANNOTATION));
    }

    @Test
    void comparesContentBeyondFirstBuffer() throws Exception {
        String head = "<html><head><script>" + "x".repeat(50_000) + "</script></head>";
        CrawlURI probe = probe();
        recordResponse(probe, head + "<body>Page not found</body></html>");
        detector.process(probe);

        CrawlURI page = curi("https://example.com/real/page", 200);
        recordResponse(page, head + "<body>Real content</body></html>");

        assertEquals(ProcessResult.PROCEED, detector.process(page));
        assertFalse(page.getAnnotations().contains(Soft404Detector.ANNOTATION));
    }

    @Test
    void seedsAndRootAreExempt() throws Exception {
        CrawlURI probe = probe();
        String body = "<html><title>Home</title><a href=\"news\">News</a></html>";
        recordResponse(probe, body);
        detector.process(probe);

        CrawlURI root = curi("https://example.com/", 200);
        recordResponse(root, body);
        detector.process(root);
        assertFalse(root.getAnnotations().contains(Soft404Detector.ANNOTATION));

        CrawlURI seed = curi("https://example.com/index", 200);
        seed.setSeed(true);
        recordResponse(seed, body);
        detector.process(seed);
        assertFalse(seed.getAnnotations().contains(Soft404Detector.ANNOTATION));

        CrawlURI other = curi("https://example.com/missing", 200);
        recordResponse(other, body);
        detector.process(other);
        assertTrue(other.getAnnotations().contains(Soft404Detector.ANNOTATION));
    }

    @Test
    void normalizeReplacesTokensContainingDigitsAndRemovesEchoes() {
        assertEquals("<p class=\"0\"> Page not found: </p>",
                Soft404Detector.normalize("<p class=\"x9\">\n  Page   not found: foo/bar</p>",
                        List.of("foo/bar")));
    }

    @Test
    void checkpointRoundTripPreservesCounts() throws Exception {
        String body = "<html><title>Not here</title></html>";
        CrawlURI probe = probe();
        recordResponse(probe, body);
        detector.process(probe);
        for (String path : List.of("/a", "/b")) {
            CrawlURI page = curi("https://example.com" + path, 200);
            recordResponse(page, body);
            detector.process(page);
        }
        CrawlURI otherServer = curi("https://other.example.com/robots.txt", 200);
        detector.process(otherServer);

        Soft404Detector restored = new Soft404Detector();
        restored.fromCheckpointJson(detector.toCheckpointJson());

        assertEquals(2, restored.getProbesScheduled());
        assertEquals(1, restored.getServersWithSoft404s());
        assertEquals(2, restored.getSoft404sDetected());
    }

    private CrawlServer server() {
        return serverCache.getServerFor("example.com:443");
    }

    private CrawlURI probe() throws Exception {
        CrawlURI robots = curi("https://example.com/robots.txt", 200);
        detector.process(robots);
        CrawlURI probe = robots.getOutLinks().iterator().next();
        probe.setFetchStatus(200);
        probe.setFetchType(CrawlURI.FetchType.HTTP_GET);
        probe.setContentType("text/html");
        return probe;
    }

    private static String loginLink(CrawlURI curi) {
        return "<html><a href=\"/login/?returnUri="
                + URLEncoder.encode(curi.getURI(), StandardCharsets.UTF_8)
                + "\">Log in</a></html>";
    }

    private static String spaShell(String path, String token) {
        return "<!doctype html><html><head><title>Budgets</title>"
                + "<script src=\"/assets/index-3f9a1c.js\"></script>"
                + "<script data-path=\"" + path + "\""
                + " data-context-token=\"" + token + "\"></script>"
                + "</head><body><div id=\"root\"></div></body></html>";
    }

    private static CrawlURI curi(String uri, int status) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(uri));
        curi.setFetchStatus(status);
        curi.setFetchType(CrawlURI.FetchType.HTTP_GET);
        curi.setContentType("text/html");
        return curi;
    }

    private void recordResponse(CrawlURI curi, String body) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] response = ("HTTP/1.1 " + curi.getFetchStatus() + " Test\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "\r\n"
                + body).getBytes(StandardCharsets.UTF_8);
        Recorder recorder = new Recorder(tempDir.toFile(), "soft404-detector-" + System.nanoTime());
        curi.setRecorder(recorder);
        recorder.inputWrap(new ByteArrayInputStream(response));
        recorder.getRecordedInput().readFully();
        recorder.close();
    }
}
