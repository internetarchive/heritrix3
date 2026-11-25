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

import static org.archive.modules.CoreAttributeConstants.LENGTH_TRUNC;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.archive.bdb.BdbModule;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.revisit.IdenticalPayloadDigestRevisit;
import org.archive.net.UURIFactory;
import org.archive.spring.ConfigPath;
import org.archive.spring.ConfigPathConfigurer;
import org.archive.util.Base32;
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
        assertEquals(new Capture(1000, 200, "text/html", DIGEST, responseId, false, false,
                DIR.resolve("test.warc.gz"), 100, 250, 350, null, -1), capture);
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
