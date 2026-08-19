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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class BotBlockDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsAkamaiBlock() throws Exception {
        assertBlocked(akamaiBlock(), "akamai");
    }

    @Test
    void detectsAnubisRedirectChallenge() throws Exception {
        CrawlURI curi = curi(307);
        curi.putHttpResponseHeader("Location",
                "/.within.website/?redir=https%3A%2F%2Fexample.com%2F");

        assertBlocked(curi, "anubis");
    }

    @Test
    void detectsAnubisCookieChallenge() throws Exception {
        CrawlURI curi = curi(200);
        curi.putHttpResponseHeader("Set-Cookie",
                "techaro.lol-anubis-auth=token; Path=/");
        recordResponse(curi, "<script id=\"anubis_challenge\"></script>");

        assertBlocked(curi, "anubis");
    }

    @Test
    void ordinaryResponseDoesNotMatch() throws Exception {
        CrawlURI curi = curi(200);

        assertNotBlocked(curi);
    }

    @Test
    void detectsCloudflareChallenge() throws Exception {
        CrawlURI curi = curi(403);
        curi.putHttpResponseHeader("Server", "cloudflare");
        curi.putHttpResponseHeader("CF-Mitigated", "challenge");

        assertBlocked(curi, "cloudflare");
    }

    @Test
    void detectsCloudflareBlock() throws Exception {
        assertBlocked(cloudflareBlock(), "cloudflare");
    }

    @Test
    void detectsDatadomeBlock() throws Exception {
        CrawlURI curi = curi(403);
        // some sites use both Cloudflare and DataDome
        curi.putHttpResponseHeader("server", "cloudflare");
        curi.putHttpResponseHeader("cf-ray", "1111111111111111-SJC");
        curi.putHttpResponseHeader("x-datadome", "protected");
        curi.putHttpResponseHeader("x-dd-b", "2");
        curi.putHttpResponseHeader("x-datadome-cid", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA==");
        assertBlocked(curi, "datadome");
    }

    @Test
    void detectsIncapsulaChallenge() throws Exception {
        CrawlURI curi = curi(404);
        curi.putHttpResponseHeader("x-iinfo", "foo");
        recordResponse(curi,
                "Request unsuccessful. Incapsula incident ID: 123456");

        assertBlocked(curi, "incapsula");
    }

    @Test
    void unrelated403ResponseDoesNotMatch() throws Exception {
        CrawlURI curi = curi(403);
        curi.putHttpResponseHeader("Server", "example");

        assertNotBlocked(curi);
    }

    @Test
    void checkpointRoundTripPreservesBlockedRequestCounts() throws Exception {
        BotBlockDetector detector = new BotBlockDetector();
        detector.process(akamaiBlock());
        detector.process(cloudflareBlock());

        BotBlockDetector restored = new BotBlockDetector();
        restored.fromCheckpointJson(detector.toCheckpointJson());

        assertEquals(1, restored.getCounts().get("akamai").get());
        assertEquals(1, restored.getCounts().get("cloudflare").get());
        assertEquals(2, restored.getURICount());
    }

    private static void assertBlocked(CrawlURI curi, String service)
            throws InterruptedException {
        new BotBlockDetector().process(curi);
        assertEquals(Set.of("botblock:" + service), curi.getAnnotations());
    }

    private static void assertNotBlocked(CrawlURI curi)
            throws InterruptedException {
        new BotBlockDetector().process(curi);
        assertTrue(curi.getAnnotations().isEmpty());
    }

    private static CrawlURI akamaiBlock() throws Exception {
        CrawlURI curi = curi(403);
        curi.putHttpResponseHeader("Server", "AkamaiGHost");
        return curi;
    }

    private CrawlURI cloudflareBlock() throws Exception {
        CrawlURI curi = curi(403);
        curi.putHttpResponseHeader("Server", "cloudflare");
        recordResponse(curi, "<h1 data-translate=\"block_headline\">Sorry, you have been blocked</h1>");
        return curi;
    }

    private static CrawlURI curi(int status) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance("https://example.com/"));
        curi.setFetchStatus(status);
        curi.setFetchType(CrawlURI.FetchType.HTTP_GET);
        curi.setContentType("text/html");
        return curi;
    }

    private void recordResponse(CrawlURI curi, String body) throws Exception {
        byte[] response = ("HTTP/1.1 " + curi.getFetchStatus() + " Test\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n"
                + "\r\n"
                + body).getBytes(StandardCharsets.UTF_8);
        Recorder recorder = new Recorder(tempDir.toFile(), "bot-block-detector");
        curi.setRecorder(recorder);
        recorder.inputWrap(new ByteArrayInputStream(response));
        recorder.getRecordedInput().readFully();
        recorder.close();
    }
}
