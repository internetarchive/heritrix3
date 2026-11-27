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

package org.archive.modules.index;

import static org.archive.format.warc.WARCConstants.HEADER_KEY_ID;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_TYPE;
import static org.archive.modules.CoreAttributeConstants.LENGTH_TRUNC;
import static org.junit.jupiter.api.Assertions.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.archive.bdb.BdbModule;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.fetcher.FetchHTTP;
import org.archive.modules.fetcher.FetchHTTPTest;
import org.archive.modules.revisit.IdenticalPayloadDigestRevisit;
import org.archive.modules.revisit.RevisitProfile;
import org.archive.modules.writer.WARCWriterChainProcessor;
import org.archive.modules.writer.WARCWriterChainProcessorTest;
import org.archive.net.UURIFactory;
import org.archive.spring.ConfigPath;
import org.archive.spring.ConfigPathConfigurer;
import org.archive.util.Base32;
import org.archive.util.Recorder;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BdbCaptureIndexTest {
    private static final Path DIR = Path.of("/job/warcs");
    private static final String DIGEST = "sha1:ORFJUBLPCRNYMM4SEG5UK6VFOEU7KW6C";

    @TempDir
    Path tempDir;
    BdbModule bdb;
    BdbCaptureIndex index;

    @BeforeEach
    void setUp() {
        bdb = new BdbModule();
        bdb.setDir(new ConfigPath("bdb", tempDir.resolve("bdb").toString()));
        bdb.start();
        index = new BdbCaptureIndex();
        index.setBdbModule(bdb);
        index.start();
    }

    @AfterEach
    void tearDown() {
        index.stop();
        bdb.close();
    }

    @Test
    void indexesResponse() throws Exception {
        CrawlURI curi = curi("http://example.org/a", 1000);
        UUID responseId = UUID.randomUUID();
        index.add(curi, List.of(
                record(WARCRecordType.response, responseId, 100, 250),
                record(WARCRecordType.request, UUID.randomUUID(), 350, 150),
                record(WARCRecordType.metadata, UUID.randomUUID(), 500, 200)));

        Capture capture = index.latest("http://example.org/a");
        assertEquals(new Capture("http://(org,example,)/a", 1000, "http://example.org/a", 200,
                "text/html", DIGEST, responseId, false, false, DIR.resolve("test.warc.gz"), 100,
                250, 350, null, -1), capture);
        assertNull(index.latest("http://example.org/b"));
        assertNull(index.latest("http://example.org/"));
    }

    @Test
    void returnsNewestCapture() throws Exception {
        index.add(curi("http://example.org/a", 1000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 100)));
        index.add(curi("http://example.org/a", 3000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 300, 100)));
        index.add(curi("http://example.org/a", 2000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 500, 100)));
        index.add(curi("http://example.org/ab", 4000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 700, 100)));

        assertEquals(3000, index.latest("http://example.org/a").time());
    }

    @Test
    void pagesExactAndPrefixQueriesInIndexOrder() throws Exception {
        String url = "http://example.org/a";
        String key = "http://(org,example,)/a";
        var response = List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 100));
        int batchSize = 512;
        int count = batchSize + 1;
        for (int time = 1; time <= count; time++) {
            index.add(curi(url, time), response);
        }
        index.add(curi(url + "b", 1), response);
        index.add(curi("http://other.org/", 1), response);

        var first = index.query(key, true, null, batchSize);
        assertEquals(count - 1, first.size());
        for (int i = 0; i < first.size(); i++) assertEquals(count - i, first.get(i).time());
        Capture after = first.get(first.size() - 1);
        var last = index.query(key, true, after, batchSize);
        assertEquals(1, last.size());
        assertEquals(1, last.get(0).time());
        assertTrue(index.query(key, true, last.get(0), batchSize).isEmpty());
        assertEquals(List.of(url, url + "b"), index.query(key, false, after, batchSize)
                .stream().map(Capture::url).toList());
        assertEquals(3, index.query("", false, after, batchSize).size());
        assertTrue(index.query(key + "/missing", true, null, batchSize).isEmpty());
    }

    @Test
    void skipsNonGetAndUncanonicalized() throws Exception {
        CrawlURI post = curi("http://example.org/post", 1000);
        post.setFetchType(FetchType.HTTP_POST);
        index.add(post, List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 100)));

        CrawlURI uncanonicalized = new CrawlURI(UURIFactory.getInstance("http://example.org/raw"));
        uncanonicalized.setFetchType(FetchType.HTTP_GET);
        index.add(uncanonicalized,
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 300, 100)));

        assertNull(index.latest("http://example.org/post"));
        assertNull(index.latest("http://example.org/raw"));
    }

    @Test
    void resolvesRevisitRefersTo() throws Exception {
        CrawlURI truncated = curi("http://example.org/a", 1000);
        truncated.getAnnotations().add(LENGTH_TRUNC);
        index.add(truncated, List.of(record(WARCRecordType.response, UUID.randomUUID(), 50, 50)));
        index.add(curi("http://example.org/a", 2000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 200)));
        index.add(curi("http://example.org/a", 3000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 300, 100)));

        CrawlURI revisit = curi("http://example.org/a", 4000);
        revisit.setRevisitProfile(new IdenticalPayloadDigestRevisit(DIGEST));
        index.add(revisit, List.of(record(WARCRecordType.revisit, UUID.randomUUID(), 400, 100)));

        Capture capture = index.latest("http://example.org/a");
        assertTrue(capture.revisit());
        assertEquals(DIGEST, capture.digest());
        assertEquals(DIR.resolve("test.warc.gz"), capture.refersToPath());
        assertEquals(300, capture.refersToOffset());
    }

    @Test
    void keepsFileNamesAcrossRestart() throws Exception {
        index.add(curi("http://example.org/a", 1000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 100)));
        index.stop();
        index.start();
        index.add(curi("http://example.org/b", 1000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 100,
                        "other.warc.gz")));

        assertEquals(DIR.resolve("test.warc.gz"), index.latest("http://example.org/a").path());
        assertEquals(DIR.resolve("other.warc.gz"), index.latest("http://example.org/b").path());
    }

    @Test
    void recordsPathsInsideTheJobDirRelativeToIt() throws Exception {
        index.stop();
        index.setConfigPathConfigurer(configurer("/job"));
        index.start();
        index.add(curi("http://example.org/a", 1000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 100)));
        index.stop();
        index.setConfigPathConfigurer(configurer("/moved"));
        index.start();
        index.add(curi("http://example.org/b", 1000),
                List.of(record(WARCRecordType.response, UUID.randomUUID(), 100, 100,
                        "other.warc.gz")));
        index.stop();
        index.start();

        assertEquals(Path.of("/moved/warcs/test.warc.gz"),
                index.latest("http://example.org/a").path());
        assertEquals(DIR.resolve("other.warc.gz"), index.latest("http://example.org/b").path());
    }

    private static ConfigPathConfigurer configurer(String jobDir) {
        return new ConfigPathConfigurer() {{
            path = new ConfigPath("job", jobDir);
        }};
    }

    @Test
    void indexesAndReadsBackWrittenRecords() throws Exception {
        Server server = new Server();
        var connector = new ServerConnector(server);
        server.addConnector(connector);
        server.setHandler(new Handler.Abstract.NonBlocking() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) {
                response.getHeaders().put(HttpHeader.CONTENT_TYPE, "text/plain");
                response.getHeaders().add("Set-Cookie", "a=1").add("Set-Cookie", "b=2");
                response.getHeaders().put(HttpHeader.TRANSFER_ENCODING, "chunked");
                Content.Sink.write(response, true, "hello", callback);
                return true;
            }
        });
        server.start();
        FetchHTTP fetcher = FetchHTTPTest.newTestFetchHttp(getClass().getName());
        fetcher.setUseHTTP11(true);
        WARCWriterChainProcessor writer =
                WARCWriterChainProcessorTest.makeTestWARCWriterChainProcessor(tempDir);
        writer.setServerCache(fetcher.getServerCache());
        writer.setCaptureIndex(index);
        writer.start();
        try {
            String url = "http://127.0.0.1:" + connector.getLocalPort() + "/a";
            CrawlURI curi = fetchAndWrite(fetcher, writer, url, null);

            Capture capture = index.latest(url);
            assertEquals(200, capture.status());
            assertEquals(curi.getContentDigestSchemeString(), capture.digest());

            // read back while the file is still open
            assertEquals(writer.calcOutputDirs().get(0).toPath(), capture.path().getParent());
            ArchiveRecord response = readOpenRecord(capture.path(), capture.offset());
            assertEquals("response", response.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals(url, response.getHeader().getUrl());
            assertEquals("<urn:uuid:" + capture.recordId() + ">",
                    response.getHeader().getHeaderValue(HEADER_KEY_ID));
            ArchiveRecord request = readOpenRecord(capture.path(), capture.requestOffset());
            assertEquals("request", request.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals(capture.offset() + capture.length(), capture.requestOffset());
            assertResponse(capture);

            fetchAndWrite(fetcher, writer, url,
                    new IdenticalPayloadDigestRevisit(capture.digest()));
            Capture revisit = index.latest(url);
            assertTrue(revisit.revisit());
            assertEquals(capture.path(), revisit.refersToPath());
            assertResponse(revisit);

            writer.stop();
            assertResponse(revisit); // after the file is closed and renamed
        } finally {
            Recorder.setHttpRecorder(null);
            writer.stop();
            fetcher.stop();
            server.stop();
        }
    }

    private CrawlURI fetchAndWrite(FetchHTTP fetcher, WARCWriterChainProcessor writer, String url,
                                   RevisitProfile revisitProfile) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(url));
        curi.setCanonicalString(url);
        var recorder = new Recorder(tempDir.toFile(), "rec");
        Recorder.setHttpRecorder(recorder);
        curi.setRecorder(recorder);
        fetcher.process(curi);
        curi.setRevisitProfile(revisitProfile);
        writer.process(curi);
        return curi;
    }

    private static void assertResponse(Capture capture) throws IOException {
        try (var response = CapturedResponse.open(capture)) {
            assertEquals(200, response.status());
            assertTrue(response.headers().stream().anyMatch(h -> h.getValue().equals("chunked")));
            assertEquals(List.of("a=1", "b=2"), response.headers().stream()
                    .filter(h -> h.getName().equalsIgnoreCase("Set-Cookie"))
                    .map(h -> h.getValue()).toList());
            assertEquals("hello",
                    new String(response.body().readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static ArchiveRecord readOpenRecord(Path path, long offset) throws IOException {
        var in = new FileInputStream(path + ".open");
        in.getChannel().position(offset);
        return WARCReaderFactory.get(path.toString(), in, false).get();
    }

    private static CrawlURI curi(String uri, long time) throws Exception {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(uri));
        curi.setCanonicalString(uri);
        curi.setFetchType(FetchType.HTTP_GET);
        curi.setFetchBeginTime(time);
        curi.setFetchStatus(200);
        curi.setContentType("text/html");
        curi.setContentDigest("sha1", Base32.decode(DIGEST.substring("sha1:".length())));
        return curi;
    }

    private static WARCRecordInfo record(WARCRecordType type, UUID id, long offset, long length) {
        return record(type, id, offset, length, "test.warc.gz");
    }

    private static WARCRecordInfo record(WARCRecordType type, UUID id, long offset, long length,
                                         String filename) {
        var record = new WARCRecordInfo();
        record.setType(type);
        record.setRecordId(URI.create("urn:uuid:" + id));
        record.setWARCFileOffset(offset);
        record.setRecordLength(length);
        record.setWARCFilename(filename);
        record.setWARCPath(DIR.resolve(filename));
        return record;
    }
}
