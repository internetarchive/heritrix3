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
package org.archive.modules.recrawl;

import static org.archive.format.warc.WARCConstants.CONTENT_LENGTH;
import static org.archive.format.warc.WARCConstants.CONTENT_TYPE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_CONCURRENT_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_ID;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_PAYLOAD_DIGEST;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_PROFILE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO_DATE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO_FILENAME;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO_FILE_OFFSET;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_REFERS_TO_TARGET_URI;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_TRUNCATED;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_TYPE;
import static org.archive.format.warc.WARCConstants.HEADER_KEY_URI;
import static org.archive.format.warc.WARCConstants.HTTP_RESPONSE_MIMETYPE;
import static org.archive.format.warc.WARCConstants.NAMED_FIELD_TRUNCATED_VALUE_LENGTH;
import static org.archive.format.warc.WARCConstants.PROFILE_REVISIT_IDENTICAL_DIGEST;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_CONTENT_DIGEST_COUNT;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_DATE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_RECORD_ID;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCReader;
import org.archive.io.warc.WARCReaderFactory;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchHTTP;
import org.archive.modules.fetcher.FetchHTTPTests;
import org.archive.modules.writer.WARCWriterProcessor;
import org.archive.modules.writer.WARCWriterProcessorTest;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.spring.ConfigPath;
import org.archive.util.Base32;
import org.archive.util.Recorder;
import org.archive.util.TmpDirTestCase;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.servlet.SessionHandler;

public class ContentDigestHistoryTest extends TmpDirTestCase {

    private static Logger logger = Logger.getLogger(ContentDigestHistoryTest.class.getName());
    
    protected BdbModule bdb;
    protected BdbContentDigestHistory historyStore;
    protected ContentDigestHistoryStorer storer;
    protected ContentDigestHistoryLoader loader;

    protected ContentDigestHistoryLoader loader() throws IOException {
        if (loader == null) {
            loader = new ContentDigestHistoryLoader();
            loader.setContentDigestHistory(historyStore());
            logger.info("created " + loader);
        }
        return loader;
    }
    
    protected ContentDigestHistoryStorer storer() throws IOException {
        if (storer == null) {
            storer = new ContentDigestHistoryStorer();
            storer.setContentDigestHistory(historyStore());
            logger.info("created " + storer);
        }
        return storer;
    }
    
    protected BdbContentDigestHistory historyStore() throws IOException {
        if (historyStore == null) {
            historyStore = new BdbContentDigestHistory();
            historyStore.setBdbModule(bdb());
            historyStore.start();
            logger.info("created " + historyStore);
        }
        return historyStore;
    }

    protected BdbModule bdb() throws IOException {
        if (bdb == null) {
            ConfigPath basePath = new ConfigPath("testBase",getTmpDir().getAbsolutePath());
            ConfigPath bdbDir = new ConfigPath("bdb","bdb"); 
            bdbDir.setBase(basePath); 
            FileUtils.deleteDirectory(bdbDir.getFile());

            bdb = new BdbModule();
            bdb.setDir(bdbDir);
            bdb.start();
            logger.info("created " + bdb);
        }
        return bdb;
    }

    public void testBasics() throws InterruptedException, IOException {
        CrawlURI curi1 = new CrawlURI(UURIFactory.getInstance("http://example.org/1"));
        // without Recorder, CrawlURI#getContentLength() returns zero, which makes
        // loader().shoudProcess() return false.
        Recorder rec = new Recorder(getTmpDir(), "rec");
        curi1.setRecorder(rec);
        // give Recorder some content so that getContentLength() returns non-zero.
        InputStream is = rec.inputWrap(new ByteArrayInputStream("HTTP/1.0 200 OK\r\n\r\ntext.".getBytes()));
        is.read(new byte[1024]);
        is.close();
        
        assertFalse(loader().shouldProcess(curi1));
        assertFalse(storer().shouldProcess(curi1));

        // sha1 of "monkey\n", point is to have a value there
        curi1.setContentDigest("sha1", Base32.decode("orfjublpcrnymm4seg5uk6vfoeu7kw6c"));

        assertTrue(loader().shouldProcess(curi1));
        assertTrue(storer().shouldProcess(curi1));
        
        assertEquals("sha1:ORFJUBLPCRNYMM4SEG5UK6VFOEU7KW6C", historyStore().persistKeyFor(curi1));

        assertFalse(curi1.hasContentDigestHistory());
        
        loader().process(curi1);

        assertTrue(curi1.hasContentDigestHistory());
        assertTrue(curi1.getContentDigestHistory().isEmpty());

        storer().process(curi1);
        assertTrue(historyStore().store.isEmpty());
        
        curi1.getContentDigestHistory().put(A_ORIGINAL_URL, "http://example.org/original");
        // curi1.getContentDigestHistory().put(A_WARC_RECORD_ID, "<urn:uuid:f00dface-d00d-d00d-d00d-0beefface0ff>");
        // curi1.getContentDigestHistory().put(A_WARC_FILENAME, "test.warc.gz");
        // curi1.getContentDigestHistory().put(A_WARC_FILE_OFFSET, 98765432l);
        // curi1.getContentDigestHistory().put(A_ORIGINAL_DATE, "20120101000000");
        // curi1.getContentDigestHistory().put(A_CONTENT_DIGEST_COUNT, 1);
        
        loader().process(curi1);
        assertEquals("http://example.org/original", curi1.getContentDigestHistory().get(A_ORIGINAL_URL));
        
        storer().process(curi1);
        
        assertFalse(historyStore().store.isEmpty());
        assertEquals(1, historyStore().store.size());
        
        CrawlURI curi2 = new CrawlURI(UURIFactory.getInstance("http://example.org/2"));
        curi2.setContentDigest("sha1", Base32.decode("orfjublpcrnymm4seg5uk6vfoeu7kw6c"));
        curi2.setRecorder(rec);
        
        assertFalse(curi2.hasContentDigestHistory());
        
        loader().process(curi2);
        
        assertTrue(curi2.hasContentDigestHistory());
        assertEquals("http://example.org/original", curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
    }

    protected CrawlURI makeCrawlURI(String uri) throws URIException,
            IOException {
        UURI uuri = UURIFactory.getInstance(uri);
        CrawlURI curi = new CrawlURI(uuri);
        curi.setSeed(true);
        curi.setRecorder(getRecorder());
        return curi;
    }

    /*
     * fetches two different urls with same content, writes warc records, checks results 
     */
    public void testWarcDedupe() throws Exception {
        historyStore().store.clear();
        assertTrue(historyStore().store.isEmpty());

        Server server = newHttpServer();

        FetchHTTP fetcher = FetchHTTPTests.newTestFetchHttp(getClass().getName());
        WARCWriterProcessor warcWriter = WARCWriterProcessorTest.newTestWarcWriter(getClass().getName());
        warcWriter.setServerCache(fetcher.getServerCache());
        for (File dir: warcWriter.calcOutputDirs()) {
            /* make sure we don't have other stuff hanging around that will
             * confuse the warc reader checks later */
            FileUtils.deleteDirectory(dir);
        }

        try {
            server.start();
            warcWriter.start();
            fetcher.start();

            CrawlURI curi1 = makeCrawlURI("http://127.0.0.1:7777/url1");
            CrawlURI curi2 = makeCrawlURI("http://127.0.0.1:7777/url2"); 
            final String expectedDigest = "sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ";

            fetcher.process(curi1);
            assertEquals(200, curi1.getFetchStatus());
            assertEquals(141, curi1.getContentSize());
            assertEquals(expectedDigest, curi1.getContentDigestSchemeString());
            assertFalse(curi1.hasContentDigestHistory());

            loader().process(curi1);
            assertTrue(curi1.hasContentDigestHistory());
            assertTrue(curi1.getContentDigestHistory().isEmpty());

            warcWriter.process(curi1);
            assertEquals(curi1.getUURI().toString(), curi1.getContentDigestHistory().get(A_ORIGINAL_URL));
            assertEquals(1, curi1.getContentDigestHistory().get(A_CONTENT_DIGEST_COUNT));
            String report = warcWriter.report();
            assertTrue(report.contains("Total CrawlURIs:   1\n"));
            assertTrue(report.contains("Revisit records:   0\n"));

            storer().process(curi1);
            assertEquals(1, historyStore().store.size());
            assertNotNull(historyStore().store.get(expectedDigest));
            assertEquals(curi1.getUURI().toString(), historyStore().store.get(expectedDigest).get(A_ORIGINAL_URL));
            assertEquals(1, historyStore().store.get(expectedDigest).get(A_CONTENT_DIGEST_COUNT));

            fetcher.process(curi2);
            assertEquals(200, curi1.getFetchStatus());
            assertEquals(141, curi1.getContentSize());
            assertEquals(expectedDigest, curi1.getContentDigestSchemeString());
            assertFalse(curi2.hasContentDigestHistory());

            loader().process(curi2);
            assertTrue(curi2.hasContentDigestHistory());
            assertEquals(curi1.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
            assertNotSame(curi2.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
            assertEquals(1, curi2.getContentDigestHistory().get(A_CONTENT_DIGEST_COUNT));

            warcWriter.process(curi2);
            assertTrue(curi2.getAnnotations().contains("warcRevisit:digest"));
            assertEquals(curi1.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
            assertNotSame(curi2.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
            assertEquals(2, curi2.getContentDigestHistory().get(A_CONTENT_DIGEST_COUNT));
            report = warcWriter.report();
            assertTrue(report.contains("Total CrawlURIs:   2\n"));
            assertTrue(report.contains("Revisit records:   1\n"));

            storer().process(curi2);
            assertEquals(1, historyStore().store.size());
            assertNotNull(historyStore().store.get(expectedDigest));
            assertEquals(curi1.getUURI().toString(), historyStore().store.get(expectedDigest).get(A_ORIGINAL_URL));
            assertEquals(2, historyStore().store.get(expectedDigest).get(A_CONTENT_DIGEST_COUNT));

            warcWriter.stop();
            
            String payloadRecordIdWithBrackets = "<"
                    + historyStore().store.get(expectedDigest).get(
                            A_WARC_RECORD_ID) + ">";
            
            // check the warc records
            List<File> warcDirs = warcWriter.calcOutputDirs();
            assertEquals(1, warcDirs.size());
            String[] warcs = warcDirs.get(0).list();
            assertEquals(1, warcs.length);
            WARCReader warcReader = WARCReaderFactory.get(new File(warcDirs.get(0), warcs[0]));
            Iterator<ArchiveRecord> recordIterator = warcReader.iterator();
            
            ArchiveRecord record = recordIterator.next();
            assertEquals(WARCRecordType.warcinfo.toString(), record.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            
            assertTrue(recordIterator.hasNext());
            record = recordIterator.next();
            assertEquals(WARCRecordType.response.toString(), record.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals("141", record.getHeader().getHeaderValue(CONTENT_LENGTH));
            assertEquals(expectedDigest, record.getHeader().getHeaderValue(HEADER_KEY_PAYLOAD_DIGEST));
            assertEquals(curi1.getUURI().toString(), record.getHeader().getHeaderValue(HEADER_KEY_URI));
            assertEquals(payloadRecordIdWithBrackets, record.getHeader().getHeaderValue(HEADER_KEY_ID));
            
            assertTrue(recordIterator.hasNext());
            record = recordIterator.next();
            assertEquals(WARCRecordType.request.toString(), record.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals(curi1.getUURI().toString(), record.getHeader().getHeaderValue(HEADER_KEY_URI));
            assertEquals(payloadRecordIdWithBrackets, record.getHeader().getHeaderValue(HEADER_KEY_CONCURRENT_TO));
            
            assertTrue(recordIterator.hasNext());
            record = recordIterator.next();
            assertEquals(WARCRecordType.metadata.toString(), record.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals(curi1.getUURI().toString(), record.getHeader().getHeaderValue(HEADER_KEY_URI));
            assertEquals(payloadRecordIdWithBrackets, record.getHeader().getHeaderValue(HEADER_KEY_CONCURRENT_TO));
            
            // the all-important revisit record
            assertTrue(recordIterator.hasNext());
            record = recordIterator.next();
            assertEquals(WARCRecordType.revisit.toString(), record.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals(curi2.getUURI().toString(), record.getHeader().getHeaderValue(HEADER_KEY_URI));
            assertEquals(payloadRecordIdWithBrackets, record.getHeader().getHeaderValue(HEADER_KEY_REFERS_TO));
            assertEquals(NAMED_FIELD_TRUNCATED_VALUE_LENGTH, record.getHeader().getHeaderValue(HEADER_KEY_TRUNCATED));
            assertEquals(HTTP_RESPONSE_MIMETYPE, record.getHeader().getHeaderValue(CONTENT_TYPE));
            assertEquals(expectedDigest, record.getHeader().getHeaderValue(HEADER_KEY_PAYLOAD_DIGEST));
            assertEquals(PROFILE_REVISIT_IDENTICAL_DIGEST, 
                    record.getHeader().getHeaderValue(HEADER_KEY_PROFILE));
            assertEquals(curi1.getUURI().toString(), record.getHeader().getHeaderValue(HEADER_KEY_REFERS_TO_TARGET_URI));
            assertEquals(historyStore().store.get(expectedDigest).get(A_ORIGINAL_DATE), 
                    record.getHeader().getHeaderValue(HEADER_KEY_REFERS_TO_DATE));
            assertNull(record.getHeader().getHeaderValue(HEADER_KEY_REFERS_TO_FILENAME));
            assertNull(record.getHeader().getHeaderValue(HEADER_KEY_REFERS_TO_FILE_OFFSET));

            assertTrue(recordIterator.hasNext());
            record = recordIterator.next();
            assertEquals(WARCRecordType.request.toString(), record.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals(curi2.getUURI().toString(), record.getHeader().getHeaderValue(HEADER_KEY_URI));
            
            assertTrue(recordIterator.hasNext());
            record = recordIterator.next();
            assertEquals(WARCRecordType.metadata.toString(), record.getHeader().getHeaderValue(HEADER_KEY_TYPE));
            assertEquals(curi2.getUURI().toString(), record.getHeader().getHeaderValue(HEADER_KEY_URI));

            assertFalse(recordIterator.hasNext());
            
        } finally {
            warcWriter.stop();
            fetcher.stop();
            server.stop();
        }
    }
    

    protected Recorder getRecorder() throws IOException {
        if (Recorder.getHttpRecorder() == null) {
            Recorder httpRecorder = new Recorder(TmpDirTestCase.tmpDir(),
                    getClass().getName(), 16 * 1024, 512 * 1024);
            Recorder.setHttpRecorder(httpRecorder);
        }

        return Recorder.getHttpRecorder();
    }

    protected static final String DEFAULT_PAYLOAD_STRING = "abcdefghijklmnopqrstuvwxyz0123456789\n";
    protected Server newHttpServer() throws Exception {
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(new SessionHandler(){
            @Override
            public void handle(String target, HttpServletRequest request,
                    HttpServletResponse response, int dispatch) throws IOException,
                    ServletException {

                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                ((Request)request).setHandled(true);
            }
        });

        Server server = new Server();
        server.setHandler(handlers);
        
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        
        return server;
    }
}
