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

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_CONTENT_DIGEST_COUNT;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchHTTP;
import org.archive.modules.fetcher.FetchHTTPTest;
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

    public void testWarcDedupe() throws Exception {
        historyStore().store.clear();
        assertTrue(historyStore().store.isEmpty());
        
        startHttpServer();
        
        FetchHTTP fetcher = FetchHTTPTest.newTestFetchHttp(getClass().getName());
        WARCWriterProcessor warcWriter = WARCWriterProcessorTest.newTestWarcWriter(getClass().getName());
        warcWriter.setServerCache(fetcher.getServerCache());
        warcWriter.start();
        fetcher.start();
        
        CrawlURI curi1 = makeCrawlURI("http://127.0.0.1:7777/url1");
        CrawlURI curi2 = makeCrawlURI("http://127.0.0.1:7777/url2"); 

        fetcher.process(curi1);
        assertEquals(200, curi1.getFetchStatus());
        assertEquals(141, curi1.getContentSize());
        assertEquals("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ", curi1.getContentDigestSchemeString());
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
        assertNotNull(historyStore().store.get("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ"));
        assertEquals(curi1.getUURI().toString(), historyStore().store.get("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ").get(A_ORIGINAL_URL));
        assertEquals(1, historyStore().store.get("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ").get(A_CONTENT_DIGEST_COUNT));

        fetcher.process(curi2);
        assertEquals(200, curi1.getFetchStatus());
        assertEquals(141, curi1.getContentSize());
        assertEquals("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ", curi1.getContentDigestSchemeString());
        assertFalse(curi2.hasContentDigestHistory());

        loader().process(curi2);
        assertTrue(curi2.hasContentDigestHistory());
        assertEquals(curi1.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
        assertNotSame(curi2.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
        assertEquals(1, curi2.getContentDigestHistory().get(A_CONTENT_DIGEST_COUNT));
        
        warcWriter.process(curi2);
        assertEquals(curi1.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
        assertNotSame(curi2.getUURI().toString(), curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
        assertEquals(2, curi2.getContentDigestHistory().get(A_CONTENT_DIGEST_COUNT));
        report = warcWriter.report();
        assertTrue(report.contains("Total CrawlURIs:   2\n"));
        assertTrue(report.contains("Revisit records:   1\n"));
        
        storer().process(curi2);
        assertEquals(1, historyStore().store.size());
        assertNotNull(historyStore().store.get("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ"));
        assertEquals(curi1.getUURI().toString(), historyStore().store.get("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ").get(A_ORIGINAL_URL));
        assertEquals(2, historyStore().store.get("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ").get(A_CONTENT_DIGEST_COUNT));
        
        warcWriter.stop();
        fetcher.stop();
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
    protected void startHttpServer() throws Exception {
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
        
        server.start();
    }
}
