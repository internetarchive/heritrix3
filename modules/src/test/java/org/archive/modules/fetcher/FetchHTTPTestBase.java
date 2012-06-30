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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
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
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.log.Log;

public abstract class FetchHTTPTestBase extends ProcessorTestBase {
    
    private static Logger logger = Logger.getLogger(FetchHTTPTestBase.class.getName());
    
    protected static final String DEFAULT_PAYLOAD_STRING = "abcdefghijklmnopqrstuvwxyz0123456789\n";

    protected static class TestHandler extends AbstractHandler {
        @Override
        public void handle(String target, HttpServletRequest request,
                HttpServletResponse response, int dispatch) throws IOException,
                ServletException {
            if (target.endsWith("/set-cookie")) {
                response.addCookie(new Cookie("test-cookie-name", "test-cookie-value"));
            }
            response.setContentType("text/plain;charset=US-ASCII");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
            ((Request)request).setHandled(true);
        }
    }

    protected static Server httpServer;
    protected AbstractFetchHTTP fetcher;

    abstract protected AbstractFetchHTTP makeModule() throws IOException;

    // put auth around certain paths for testing fetcher's handling of auth
    protected static SecurityHandler makeAuthWrapper() {
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);;
        constraint.setRoles(new String[]{"basic-auth"});
        constraint.setAuthenticate(true);
         
        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/basic-auth");
         
        SecurityHandler authWrapper = new SecurityHandler();
        authWrapper.setUserRealm(new HashUserRealm(FetchHTTPTestBase.class.getName()) {
        });
        authWrapper.setConstraintMappings(new ConstraintMapping[]{constraintMapping});
        
        return authWrapper;
    }
    
    public static Server startHttpServer() throws Exception {
        Log.getLogger(Server.class.getCanonicalName()).setDebugEnabled(true);
        
        Server server = new Server();
        
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);
        
        SecurityHandler authWrapper = makeAuthWrapper();
        authWrapper.setHandler(new TestHandler());
        server.setHandler(authWrapper);
        
        server.start();
        
        return server;
    }
    
    protected static void ensureHttpServer() throws Exception {
        if (httpServer == null) { 
            httpServer = startHttpServer();
        }
    }

    protected AbstractFetchHTTP getFetcher() throws IOException {
        if (fetcher == null) { 
            fetcher = makeModule();
        }
        
        return fetcher;
    }

    protected String getUserAgentString() {
        return getClass().getName();
    }

    protected Recorder getRecorder() throws IOException {
        if (Recorder.getHttpRecorder() == null) {
            Recorder httpRecorder = new Recorder(TmpDirTestCase.tmpDir(),
                    getClass().getName(), 16 * 1024, 512 * 1024);
            Recorder.setHttpRecorder(httpRecorder);
        }

        return Recorder.getHttpRecorder();
    }
    
    protected CrawlURI makeCrawlURI(String uri) throws URIException,
            IOException {
        UURI uuri = UURIFactory.getInstance(uri);
        CrawlURI curi = new CrawlURI(uuri);
        curi.setRecorder(getRecorder());
        return curi;
    }

    protected void runDefaultChecks(CrawlURI curi, Set<String> exclusions) throws IOException,
            UnsupportedEncodingException {
        
        // inspect request that was sent
        byte[] buf = IOUtils.toByteArray(getRecorder().getRecordedOutput().getReplayInputStream());
        String requestString = new String(buf, "US-ASCII");
        if (!exclusions.contains("requestLine")) {
            assertTrue(requestString.startsWith("GET / HTTP/1.0\r\n"));
        }
        assertTrue(requestString.contains("User-Agent: " + getUserAgentString() + "\r\n"));
        assertTrue(requestString.matches("(?s).*Connection: [Cc]lose\r\n.*"));
        if (!exclusions.contains("acceptHeaders")) {
            assertTrue(requestString.contains("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"));
        }
        assertTrue(requestString.contains("Host: localhost:7777\r\n"));
        assertTrue(requestString.endsWith("\r\n\r\n"));
        
        // check sizes
        assertEquals(DEFAULT_PAYLOAD_STRING.length(), curi.getContentLength());
        assertEquals(curi.getContentSize(), curi.getRecordedSize());
        
        // check various 
        assertEquals("sha1:TQ5R6YVOZLTQENRIIENVGXHOPX3YCRNJ", curi.getContentDigestSchemeString());
        assertEquals("text/plain;charset=US-ASCII", curi.getContentType());
        assertEquals(Charset.forName("US-ASCII"), curi.getRecorder().getCharset());
        assertTrue(curi.getCredentials().isEmpty());
        assertTrue(curi.getFetchDuration() >= 0);
        assertTrue(curi.getFetchStatus() == 200);
        assertTrue(curi.getFetchType() == FetchType.HTTP_GET);
        
        // check message body, i.e. "raw, possibly chunked-transfer-encoded message contents not including the leading headers"
        buf = IOUtils.toByteArray(curi.getRecorder().getMessageBodyReplayInputStream());
        String messageBodyString = new String(buf, "US-ASCII");
        assertEquals(DEFAULT_PAYLOAD_STRING, messageBodyString);

        // check entity, i.e. "message-body after any (usually-unnecessary) transfer-decoding but before any content-encoding (eg gzip) decoding"
        buf = IOUtils.toByteArray(curi.getRecorder().getEntityReplayInputStream());
        String entityString = new String(buf, "US-ASCII");
        assertEquals(DEFAULT_PAYLOAD_STRING, entityString);

        // check content, i.e. message-body after possibly tranfer-decoding and after content-encoding (eg gzip) decoding
        buf = IOUtils.toByteArray(curi.getRecorder().getContentReplayInputStream());
        String contentString = new String(buf, "US-ASCII");
        assertEquals(DEFAULT_PAYLOAD_STRING, contentString);
        assertEquals(DEFAULT_PAYLOAD_STRING.substring(0, 10), curi.getRecorder().getContentReplayPrefixString(10));
        assertEquals(DEFAULT_PAYLOAD_STRING, curi.getRecorder().getContentReplayCharSequence().toString());
    }
    
    public void testDefaults() throws Exception {
        ensureHttpServer();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>());
    }

    public void testAcceptHeaders() throws Exception {
        ensureHttpServer();
        List<String> headers = Arrays.asList("header1: value1", "header2: value2");
        getFetcher().setAcceptHeaders(headers);
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);

        // applicable default checks
        HashSet<String> skipTheseChecks = new HashSet<String>(Arrays.asList("acceptHeaders"));
        runDefaultChecks(curi, skipTheseChecks);
        
        // special checks for this test
        byte[] buf = IOUtils.toByteArray(getRecorder().getRecordedOutput().getReplayInputStream());
        String requestString = new String(buf, "US-ASCII");
        assertFalse(requestString.contains("Accept:"));
        for (String h: headers) {
            assertTrue(requestString.contains(h));
        }
    }
    
    public void testCookies() throws Exception {
        ensureHttpServer();
        
        checkSetCookieURI();
        
        // second request to see if cookie is sent
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>());
        
        // check for cookie headers
        byte[] buf = IOUtils.toByteArray(getRecorder().getRecordedOutput().getReplayInputStream());
        String requestString = new String(buf, "US-ASCII");
        assertTrue(requestString.contains("Cookie: test-cookie-name=test-cookie-value\r\n"));
    }

    public void testIgnoreCookies() throws Exception {
        ensureHttpServer();

        getFetcher().setIgnoreCookies(true);
        checkSetCookieURI();

        // second request to see if cookie is NOT sent
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>());

        // check for cookie headers
        byte[] buf = IOUtils.toByteArray(getRecorder().getRecordedOutput().getReplayInputStream());
        String requestString = new String(buf, "US-ASCII");
        assertFalse(requestString.contains("Cookie:"));
    }
    
    public void testBasicAuth() throws Exception {
        ensureHttpServer();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/basic-auth");
        getFetcher().process(curi);
        
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getReplayInputStream());
        String replayString = new String(buf, "US-ASCII");
        logger.info('\n' + replayString);

        runDefaultChecks(curi, new HashSet<String>(Arrays.asList("requestLine")));
    }

    protected void checkSetCookieURI() throws URIException, IOException,
            InterruptedException, UnsupportedEncodingException {
        CrawlURI curi = makeCrawlURI("http://localhost:7777/set-cookie");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>(Arrays.asList("requestLine")));
        
        // check for set-cookie header
        byte[] buf = IOUtils.toByteArray(getRecorder().getReplayInputStream());
        String rawResponseString = new String(buf, "US-ASCII");
        assertTrue(rawResponseString.contains("Set-Cookie: test-cookie-name=test-cookie-value\r\n"));
    }
    
}
