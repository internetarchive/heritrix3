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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
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
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.OneLineSimpleLogger;
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
    static {
        Logger.getLogger("").setLevel(Level.ALL);
        for (Handler h: Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
            h.setFormatter(new OneLineSimpleLogger());
        }
    }
    
    protected static final String BASIC_AUTH_REALM = "basic-auth-realm";
    protected static final String BASIC_AUTH_ROLE = "basic-auth-role";
    protected static final String BASIC_AUTH_LOGIN = "basic-auth-login";
    protected static final String BASIC_AUTH_PASSWORD = "basic-auth-password";
    
    protected static final String DIGEST_AUTH_REALM    = "digest-auth-realm";
    protected static final String DIGEST_AUTH_ROLE     = "digest-auth-role";
    protected static final String DIGEST_AUTH_LOGIN    = "digest-auth-login";
    protected static final String DIGEST_AUTH_PASSWORD = "digest-auth-password";

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

    protected static Map<Integer, Server> httpServers;
    protected AbstractFetchHTTP fetcher;

    abstract protected AbstractFetchHTTP makeModule() throws IOException;
    
    protected static SecurityHandler makeAuthWrapper(String authMethod,
            final String role, String realm, final String login,
            final String password) {
        Constraint constraint = new Constraint();
        constraint.setRoles(new String[] { role });
        constraint.setAuthenticate(true);

        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/auth");

        SecurityHandler authWrapper = new SecurityHandler();
        authWrapper.setAuthMethod(authMethod);
        authWrapper
                .setConstraintMappings(new ConstraintMapping[] { constraintMapping });
        authWrapper.setUserRealm(new HashUserRealm(realm) {
            {
                put(login, password);
                addUserToRole(login, role);
            }
        });

        return authWrapper;
    }
    
    // can't easily have the same Server do different types of auth for
    // different paths, so we have multiple servers
    /**
     * @return map(port->server)
     */
    public static Map<Integer,Server> startHttpServers() throws Exception {
        Log.getLogger(Server.class.getCanonicalName()).setDebugEnabled(true);
        
        HashMap<Integer, Server> servers = new HashMap<Integer,Server>();
        
        // server for basic auth
        Server server = new Server();
        
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);

        SecurityHandler authWrapper = makeAuthWrapper(Constraint.__BASIC_AUTH,
                BASIC_AUTH_ROLE, BASIC_AUTH_REALM, BASIC_AUTH_LOGIN,
                BASIC_AUTH_PASSWORD);
        authWrapper.setHandler(new TestHandler());
        server.setHandler(authWrapper);
        
        server.start();
        servers.put(sc.getPort(), server);

        // server for digest auth
        server = new Server();
        
        sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7778);
        server.addConnector(sc);
        
        authWrapper = makeAuthWrapper(Constraint.__DIGEST_AUTH,
                DIGEST_AUTH_ROLE, DIGEST_AUTH_REALM, DIGEST_AUTH_LOGIN,
                DIGEST_AUTH_PASSWORD);
        authWrapper.setHandler(new TestHandler());
        server.setHandler(authWrapper);
        
        server.start();
        servers.put(sc.getPort(), server);

        return servers;
    }
    
    protected static void ensureHttpServers() throws Exception {
        if (httpServers == null) { 
            httpServers = startHttpServers();
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
        
        String requestString = httpRequestString(curi);
        if (!exclusions.contains("requestLine")) {
            assertTrue(requestString.startsWith("GET / HTTP/1.0\r\n"));
        }
        assertTrue(requestString.contains("User-Agent: " + getUserAgentString() + "\r\n"));
        assertTrue(requestString.matches("(?s).*Connection: [Cc]lose\r\n.*"));
        if (!exclusions.contains("acceptHeaders")) {
            assertTrue(requestString.contains("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\r\n"));
        }
        if (!exclusions.contains("hostHeader")) {
            assertTrue(requestString.contains("Host: localhost:7777\r\n"));
        }
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
        assertEquals(DEFAULT_PAYLOAD_STRING, messageBodyString(curi));

        // check entity, i.e. "message-body after any (usually-unnecessary) transfer-decoding but before any content-encoding (eg gzip) decoding"
        assertEquals(DEFAULT_PAYLOAD_STRING, entityString(curi));

        // check content, i.e. message-body after possibly tranfer-decoding and after content-encoding (eg gzip) decoding
        assertEquals(DEFAULT_PAYLOAD_STRING, contentString(curi));
        assertEquals(DEFAULT_PAYLOAD_STRING.substring(0, 10), curi.getRecorder().getContentReplayPrefixString(10));
        assertEquals(DEFAULT_PAYLOAD_STRING, curi.getRecorder().getContentReplayCharSequence().toString());
    }

    // convenience methods to get strings from raw recorded i/o
    protected String contentString(CrawlURI curi) throws IOException, UnsupportedEncodingException {
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getContentReplayInputStream());
        return new String(buf, "US-ASCII");
    }
    protected String entityString(CrawlURI curi) throws IOException, UnsupportedEncodingException {
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getEntityReplayInputStream());
        return new String(buf, "US-ASCII");
    }
    protected String messageBodyString(CrawlURI curi) throws IOException, UnsupportedEncodingException {
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getMessageBodyReplayInputStream());
        return new String(buf, "US-ASCII");
    }
    protected String httpRequestString(CrawlURI curi) throws IOException, UnsupportedEncodingException {
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getRecordedOutput().getReplayInputStream());
        return new String(buf, "US-ASCII");
    }
    
    public void testDefaults() throws Exception {
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>());
    }

    public void testAcceptHeaders() throws Exception {
        ensureHttpServers();
        List<String> headers = Arrays.asList("header1: value1", "header2: value2");
        getFetcher().setAcceptHeaders(headers);
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);

        // applicable default checks
        HashSet<String> skipTheseChecks = new HashSet<String>(Arrays.asList("acceptHeaders"));
        runDefaultChecks(curi, skipTheseChecks);
        
        // special checks for this test
        String requestString = httpRequestString(curi);
        assertFalse(requestString.contains("Accept:"));
        for (String h: headers) {
            assertTrue(requestString.contains(h));
        }
    }

    public void testCookies() throws Exception {
        ensureHttpServers();
        
        checkSetCookieURI();
        
        // second request to see if cookie is sent
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>());
        
        String requestString = httpRequestString(curi);
        assertTrue(requestString.contains("Cookie: test-cookie-name=test-cookie-value\r\n"));
    }

    public void testIgnoreCookies() throws Exception {
        ensureHttpServers();

        getFetcher().setIgnoreCookies(true);
        checkSetCookieURI();

        // second request to see if cookie is NOT sent
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>());

        String requestString = httpRequestString(curi);
        assertFalse(requestString.contains("Cookie:"));
    }
    
    public void testBasicAuth() throws Exception {
        ensureHttpServers();

        HttpAuthenticationCredential basicAuthCredential = new HttpAuthenticationCredential();
        basicAuthCredential.setRealm(BASIC_AUTH_REALM);
        basicAuthCredential.setDomain("localhost:7777");
        basicAuthCredential.setLogin(BASIC_AUTH_LOGIN);
        basicAuthCredential.setPassword(BASIC_AUTH_PASSWORD);
        
        getFetcher().getCredentialStore().getCredentials().put("basic-auth-credential",
                basicAuthCredential);

        CrawlURI curi = makeCrawlURI("http://localhost:7777/auth");
        getFetcher().process(curi);

        // check that we got the expected response and the fetcher did its thing
        assertEquals(401, curi.getFetchStatus());
        assertTrue(curi.getCredentials().contains(basicAuthCredential));
        
        // fetch again with the credentials
        getFetcher().process(curi);
        String httpRequestString = httpRequestString(curi);
        // logger.info('\n' + httpRequestString + contentString(curi));
        assertTrue(httpRequestString.contains("Authorization: Basic YmFzaWMtYXV0aC1sb2dpbjpiYXNpYy1hdXRoLXBhc3N3b3Jk\r\n"));
        // otherwise should be a normal 200 response
        runDefaultChecks(curi, new HashSet<String>(Arrays.asList("requestLine")));
    }

    // server for digest auth is at localhost:7778
    public void testDigestAuth() throws Exception {
        ensureHttpServers();

        HttpAuthenticationCredential digestAuthCred = new HttpAuthenticationCredential();
        digestAuthCred.setRealm(DIGEST_AUTH_REALM);
        digestAuthCred.setDomain("localhost:7778");
        digestAuthCred.setLogin(DIGEST_AUTH_LOGIN);
        digestAuthCred.setPassword(DIGEST_AUTH_PASSWORD);
        
        getFetcher().getCredentialStore().getCredentials().put("digest-auth-credential",
                digestAuthCred);

        CrawlURI curi = makeCrawlURI("http://localhost:7778/auth");
        getFetcher().process(curi);

        // check that we got the expected response and the fetcher did its thing
        assertEquals(401, curi.getFetchStatus());
        assertTrue(curi.getCredentials().contains(digestAuthCred));
        
        // fetch again with the credentials
        getFetcher().process(curi);
        String httpRequestString = httpRequestString(curi);
        // logger.info('\n' + httpRequestString + contentString(curi));
        assertTrue(httpRequestString.contains("Authorization: Digest"));
        // otherwise should be a normal 200 response
        runDefaultChecks(curi, new HashSet<String>(Arrays.asList("requestLine", "hostHeader")));
    }

    protected void checkSetCookieURI() throws URIException, IOException,
            InterruptedException, UnsupportedEncodingException {
        CrawlURI curi = makeCrawlURI("http://localhost:7777/set-cookie");
        getFetcher().process(curi);
        runDefaultChecks(curi, new HashSet<String>(Arrays.asList("requestLine")));
        
        // check for set-cookie header
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getReplayInputStream());
        String rawResponseString = new String(buf, "US-ASCII");
        assertTrue(rawResponseString.contains("Set-Cookie: test-cookie-name=test-cookie-value\r\n"));
    }
    
}
