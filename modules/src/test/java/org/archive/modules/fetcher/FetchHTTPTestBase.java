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
package org.archive.modules.fetcher;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.ProcessorTestBase;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.archive.util.TmpDirTestCase;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.AbstractHandler;
import org.mortbay.log.Log;

public abstract class FetchHTTPTestBase extends ProcessorTestBase {
    
    private static Logger logger = Logger.getLogger(FetchHTTPTestBase.class.getName());

    protected static class TestHandler extends AbstractHandler {
        @Override
        public void handle(String target, HttpServletRequest request,
                HttpServletResponse response, int dispatch) throws IOException,
                ServletException {
            response.setContentType("text/plain;charset=US-ASCII");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println("I am 29 bytes of ascii text.");
            ((Request)request).setHandled(true);
        }
    }

    protected static Server httpServer;
    protected AbstractFetchHTTP fetcher;

    abstract protected AbstractFetchHTTP makeModule() throws IOException;

    public static Server startHttpFileServer() throws Exception {
        Log.getLogger(Server.class.getCanonicalName()).setDebugEnabled(true);
        
        Server server = new Server();
        
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        
        server.setHandler(new TestHandler());
        
        server.start();
        
        return server;
    }
    
    protected static void ensureHttpServer() throws Exception {
        if (httpServer == null) { 
            httpServer = startHttpFileServer();
        }
    }

    protected AbstractFetchHTTP getFetcher() throws IOException {
        if (fetcher == null) { 
            fetcher = makeModule();
        }
        
        return fetcher;
    }

    protected CrawlURI defaultTestURI() throws URIException, IOException {
        UURI uuri = UURIFactory.getInstance("http://localhost:7777/");
        CrawlURI curi = new CrawlURI(uuri);
        curi.setRecorder(getRecorder());
        return curi;
    }

    protected void runDefaultChecks(CrawlURI curi, Set<String> exclusions) throws IOException,
            UnsupportedEncodingException {
        
        byte[] buf = IOUtils.toByteArray(getRecorder().getRecordedOutput().getReplayInputStream());
        String requestString = new String(buf, "US-ASCII");
        assertTrue(requestString.startsWith("GET / HTTP/1.0\r\n"));
        assertTrue(requestString.contains("User-Agent: " + getUserAgentString() + "\r\n"));
        assertTrue(requestString.matches("(?s).*Connection: [Cc]lose\r\n.*"));
        if (!exclusions.contains("acceptHeaders")) {
            assertTrue(requestString.contains("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"));
        }
        assertTrue(requestString.contains("Host: localhost:7777\r\n"));
        assertTrue(requestString.endsWith("\r\n\r\n"));
        
        buf = IOUtils.toByteArray(curi.getRecorder().getEntityReplayInputStream());
        String entityString = new String(buf, "US-ASCII");
        assertTrue(entityString.equals("I am 29 bytes of ascii text.\n"));
        
        assertEquals(curi.getContentLength(), 29);
        assertEquals(curi.getContentDigestSchemeString(), "sha1:Q5XGEQBX3LORBIZ5PBKNYNQEZDPAUASH");
        assertEquals(curi.getContentType(), "text/plain;charset=US-ASCII");
        assertTrue(curi.getCredentials().isEmpty());
        assertTrue(curi.getFetchDuration() >= 0);
        assertTrue(curi.getFetchStatus() == 200);
        assertTrue(curi.getFetchType() == FetchType.HTTP_GET);
        assertEquals(curi.getRecordedSize(), curi.getContentSize());
    }

    
    public void testDefaults() throws Exception {
        ensureHttpServer();
        CrawlURI curi = defaultTestURI();
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>());
    }

    public void testAcceptHeaders() throws Exception {
        ensureHttpServer();
        List<String> headers = Arrays.asList("header1: value1", "header2: value2");
        getFetcher().setAcceptHeaders(headers);
        CrawlURI curi = defaultTestURI();
        getFetcher().process(curi);

        HashSet<String> exclusions = new HashSet<String>(Arrays.asList("acceptHeaders"));
        runDefaultChecks(curi, exclusions);
        
        byte[] buf = IOUtils.toByteArray(getRecorder().getRecordedOutput().getReplayInputStream());
        String requestString = new String(buf, "US-ASCII");
        assertFalse(requestString.contains("Accept:"));
        for (String h: headers) {
            assertTrue(requestString.contains(h));
        }
    }

    protected String getUserAgentString() {
        return getClass().getName();
    }

    protected Recorder getRecorder() throws IOException {
        if (Recorder.getHttpRecorder() == null) {
            Recorder httpRecorder = new Recorder(
                    TmpDirTestCase.tmpDir(),
                    getClass().getName(), 16 * 1024, 512 * 1024);

            Recorder.setHttpRecorder(httpRecorder);
        }
        
        return Recorder.getHttpRecorder();
    }
}
