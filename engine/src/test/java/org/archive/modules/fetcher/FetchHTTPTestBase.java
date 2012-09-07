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

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.crawler.prefetch.PreconditionEnforcer;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.ProcessorTestBase;
import org.archive.modules.credential.HtmlFormCredential;
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.archive.modules.deciderules.RejectDecideRule;
import org.archive.modules.recrawl.FetchHistoryProcessor;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.OneLineSimpleLogger;
import org.archive.util.Recorder;
import org.archive.util.TmpDirTestCase;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.littleshoot.proxy.DefaultHttpProxyServer;
import org.littleshoot.proxy.HttpFilter;
import org.littleshoot.proxy.HttpRequestFilter;
import org.littleshoot.proxy.ProxyAuthorizationHandler;
import org.mortbay.jetty.NCSARequestLog;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Response;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.handler.HandlerCollection;
import org.mortbay.jetty.handler.RequestLogHandler;
import org.mortbay.jetty.security.Authenticator;
import org.mortbay.jetty.security.BasicAuthenticator;
import org.mortbay.jetty.security.Constraint;
import org.mortbay.jetty.security.ConstraintMapping;
import org.mortbay.jetty.security.DigestAuthenticator;
import org.mortbay.jetty.security.FormAuthenticator;
import org.mortbay.jetty.security.HashUserRealm;
import org.mortbay.jetty.security.SecurityHandler;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.HashSessionManager;
import org.mortbay.jetty.servlet.SessionHandler;
import org.mortbay.log.Log;

import sun.security.tools.KeyTool;

public abstract class FetchHTTPTestBase extends ProcessorTestBase {

    private static Logger logger = Logger.getLogger(FetchHTTPTestBase.class.getName());
    static {
        Logger.getLogger("").setLevel(Level.FINE);
        for (java.util.logging.Handler h: Logger.getLogger("").getHandlers()) {
            h.setLevel(Level.ALL);
            h.setFormatter(new OneLineSimpleLogger());
        }
    }
    
    protected static final String BASIC_AUTH_REALM    = "basic-auth-realm";
    protected static final String BASIC_AUTH_ROLE     = "basic-auth-role";
    protected static final String BASIC_AUTH_LOGIN    = "basic-auth-login";
    protected static final String BASIC_AUTH_PASSWORD = "basic-auth-password";
    
    protected static final String DIGEST_AUTH_REALM    = "digest-auth-realm";
    protected static final String DIGEST_AUTH_ROLE     = "digest-auth-role";
    protected static final String DIGEST_AUTH_LOGIN    = "digest-auth-login";
    protected static final String DIGEST_AUTH_PASSWORD = "digest-auth-password";

    protected static final String FORM_AUTH_REALM    = "form-auth-realm";
    protected static final String FORM_AUTH_ROLE     = "form-auth-role";
    protected static final String FORM_AUTH_LOGIN    = "form-auth-login";
    protected static final String FORM_AUTH_PASSWORD = "form-auth-password";

    protected static final String ETAG_TEST_VALUE = "An ETag is an opaque identifier assigned by a web server to a specific version of a resource found at a URL!";

    protected static final String DEFAULT_PAYLOAD_STRING = "abcdefghijklmnopqrstuvwxyz0123456789\n";

    protected static final byte[] DEFAULT_GZIPPED_PAYLOAD = { 31, -117, 8, 0,
            -69, 25, 60, 80, 0, 3, 75, 76, 74, 78, 73, 77, 75, -49, -56, -52,
            -54, -50, -55, -51, -53, 47, 40, 44, 42, 46, 41, 45, 43, -81, -88,
            -84, 50, 48, 52, 50, 54, 49, 53, 51, -73, -80, -28, 2, 0, -43, 104,
            -33, -11, 37, 0, 0, 0 };

    protected static final byte[] EIGHTY_BYTE_LINE = "1234567890123456789012345678901234567890123456789012345678901234567890123456789\n".getBytes();

    protected static final String LOGIN_HTML =
            "<html>" +
            "<head><title>Log In</title></head>" +
            "<body>" +
            "<form action='/j_security_check' method='post'>" +
            "<div> username: <input name='j_username' type='text'/> </div>" +
            "<div> password: <input name='j_password' type='password'/> </div>" +
            "<div> <input type='submit' /> </div>" +
            "</form>" +
            "</body>" +
            "</html>";

    protected static class TestHandler extends SessionHandler {

        public TestHandler() {
            super();
        }
        
        @Override
        public void handle(String target, HttpServletRequest request,
                HttpServletResponse response, int dispatch) throws IOException,
                ServletException {
            
            if (target.endsWith("/set-cookie")) {
                response.addCookie(new Cookie("test-cookie-name", "test-cookie-value"));
            }
            
            if (target.equals("/login.html")) {
                response.setContentType("text/html;charset=US-ASCII");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getOutputStream().write(LOGIN_HTML.getBytes("US-ASCII"));
                ((Request)request).setHandled(true);
            } else if (target.equals("/200k")) {
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(HttpServletResponse.SC_OK);
                assertTrue(EIGHTY_BYTE_LINE.length == 80);
                for (int i = 0; i < 200000 / EIGHTY_BYTE_LINE.length; i++) {
                    response.getOutputStream().write(EIGHTY_BYTE_LINE);
                }
                ((Request)request).setHandled(true);
            } else if (target.equals("/slow.txt")) {
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(HttpServletResponse.SC_OK);
                for (int i = 0; i < 60; i++) {
                    response.getOutputStream().write(EIGHTY_BYTE_LINE);
                    response.getOutputStream().flush();
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
                ((Request)request).setHandled(true);
            } else if (request.getHeader("Accept-Encoding") != null
                    && request.getHeader("Accept-Encoding").contains("gzip")) {
                response.setHeader("Content-Encoding", "gzip");
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(HttpServletResponse.SC_OK);
                response.getOutputStream().write(DEFAULT_GZIPPED_PAYLOAD);
                ((Request)request).setHandled(true);
            } else {
                response.setContentType("text/plain;charset=US-ASCII");
                response.setDateHeader("Last-Modified", 0);
                response.setHeader("ETag", ETAG_TEST_VALUE);
                response.setStatus(HttpServletResponse.SC_OK);
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                ((Request)request).setHandled(true);
            }
        }
    }

    protected static Map<Integer, Server> httpServers;
    protected static Request lastRequest = null;
    protected static Response lastResponse = null;
    
    protected AbstractFetchHTTP fetcher;

    abstract protected AbstractFetchHTTP makeModule() throws IOException;
    
    protected static SecurityHandler makeAuthWrapper(Authenticator authenticator,
            final String role, String realm, final String login,
            final String password) {
        Constraint constraint = new Constraint();
        constraint.setRoles(new String[] { role });
        constraint.setAuthenticate(true);

        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/auth/*");

        SecurityHandler authWrapper = new SecurityHandler();
        authWrapper.setAuthenticator(authenticator);
        
        authWrapper.setConstraintMappings(new ConstraintMapping[] {constraintMapping});
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

        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(new TestHandler());
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        NCSARequestLog requestLog = new NCSARequestLog() {
            @Override
            public void log(Request request, Response response) {
                super.log(request, response);
                lastRequest = request;
                lastResponse = response;
            }
        };
        requestLogHandler.setRequestLog(requestLog);
        handlers.addHandler(requestLogHandler);

        // server for basic auth
        Server server = new Server();
        
        SocketConnector sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);

        SecurityHandler authWrapper = makeAuthWrapper(new BasicAuthenticator(),
                BASIC_AUTH_ROLE, BASIC_AUTH_REALM, BASIC_AUTH_LOGIN,
                BASIC_AUTH_PASSWORD);
        authWrapper.setHandler(handlers);
        server.setHandler(authWrapper);
        
        servers.put(sc.getPort(), server);
        
        File keystoreFile = new File(TmpDirTestCase.tmpDir(), "keystore");
        if (keystoreFile.exists()) {
            keystoreFile.delete();
        }
        final String KEYSTORE_PASSWORD = "keystore-password";
        KeyTool.main(new String[] {
                "-keystore", keystoreFile.getPath(),
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD,
                "-alias", "jetty",
                "-genkey", 
                "-keyalg", "RSA",
                "-dname", "CN=127.0.0.1",
                "-validity","3650"}); // 10 yr validity
        
        SslSocketConnector ssc = new SslSocketConnector();
        ssc.setHost("127.0.0.1");
        ssc.setPort(7443);
        ssc.setKeyPassword(KEYSTORE_PASSWORD);
        ssc.setKeystore(keystoreFile.getPath());

        server.addConnector(ssc);
        
        server.start();

        // server for digest auth
        server = new Server();
        
        sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7778);
        server.addConnector(sc);
        
        authWrapper = makeAuthWrapper(new DigestAuthenticator(),
                DIGEST_AUTH_ROLE, DIGEST_AUTH_REALM, DIGEST_AUTH_LOGIN,
                DIGEST_AUTH_PASSWORD);
        authWrapper.setHandler(handlers);
        server.setHandler(authWrapper);
        
        server.start();
        servers.put(sc.getPort(), server);
        
        // server for form auth
        server = new Server();
        
        sc = new SocketConnector();
        sc.setHost("127.0.0.1");
        sc.setPort(7779);
        server.addConnector(sc);
        
        FormAuthenticator formAuthenticatrix = new FormAuthenticator();
        formAuthenticatrix.setLoginPage("/login.html");
        
        authWrapper = makeAuthWrapper(formAuthenticatrix,
                FORM_AUTH_ROLE, FORM_AUTH_REALM, FORM_AUTH_LOGIN,
                FORM_AUTH_PASSWORD);
        authWrapper.setHandler(handlers);
        SessionHandler sessionHandler = new SessionHandler();
        sessionHandler.setSessionManager(new HashSessionManager());
        sessionHandler.setHandler(authWrapper);
        server.setHandler(sessionHandler);
        
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
        curi.setSeed(true);
        curi.setRecorder(getRecorder());
        return curi;
    }

    protected void runDefaultChecks(CrawlURI curi, String... exclusionsArray)
        throws IOException, UnsupportedEncodingException {

        Set<String> exclusions = new HashSet<String>(Arrays.asList(exclusionsArray));
        
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
        
        if (!exclusions.contains("httpBindAddress")) {
            assertEquals("127.0.0.1", lastRequest.getRemoteAddr());
        }
        
        assertTrue(curi.getNonFatalFailures().isEmpty());
    }

    // convenience methods to get strings from raw recorded i/o
    protected String rawResponseString(CrawlURI curi) throws IOException, UnsupportedEncodingException {
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getReplayInputStream());
        return new String(buf, "US-ASCII");
    }
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
        runDefaultChecks(curi);
    }

    public void testAcceptHeaders() throws Exception {
        ensureHttpServers();
        List<String> headers = Arrays.asList("header1: value1", "header2: value2");
        getFetcher().setAcceptHeaders(headers);
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);

        runDefaultChecks(curi, "acceptHeaders");
        
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
        runDefaultChecks(curi);
        
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
        runDefaultChecks(curi);

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

        CrawlURI curi = makeCrawlURI("http://localhost:7777/auth/1");
        getFetcher().process(curi);

        // check that we got the expected response and the fetcher did its thing
        assertEquals(401, curi.getFetchStatus());
        assertTrue(curi.getCredentials().contains(basicAuthCredential));
        assertTrue(curi.getHttpAuthChallenges() != null && curi.getHttpAuthChallenges().containsKey("basic"));
        
        // fetch again with the credentials
        getFetcher().process(curi);
        String httpRequestString = httpRequestString(curi);
        // logger.info('\n' + httpRequestString + contentString(curi));
        assertTrue(httpRequestString.contains("Authorization: Basic YmFzaWMtYXV0aC1sb2dpbjpiYXNpYy1hdXRoLXBhc3N3b3Jk\r\n"));
        // otherwise should be a normal 200 response
        runDefaultChecks(curi, "requestLine");
        
        // fetch a fresh uri to make sure auth info was cached and we don't get another 401
        curi = makeCrawlURI("http://localhost:7777/auth/2");
        getFetcher().process(curi);
        httpRequestString = httpRequestString(curi);
        assertTrue(httpRequestString.contains("Authorization: Basic YmFzaWMtYXV0aC1sb2dpbjpiYXNpYy1hdXRoLXBhc3N3b3Jk\r\n"));
        // otherwise should be a normal 200 response
        runDefaultChecks(curi, "requestLine");
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

        CrawlURI curi = makeCrawlURI("http://localhost:7778/auth/1");
        getFetcher().process(curi);

        // check that we got the expected response and the fetcher did its thing
        assertEquals(401, curi.getFetchStatus());
        assertTrue(curi.getCredentials().contains(digestAuthCred));
        assertTrue(curi.getHttpAuthChallenges() != null && curi.getHttpAuthChallenges().containsKey("digest"));

        // stick a basic auth 401 in there to check it doesn't mess with the digest auth we're working on
        CrawlURI interferingUri = makeCrawlURI("http://localhost:7777/auth/basic");
        getFetcher().process(interferingUri);
        assertEquals(401, interferingUri.getFetchStatus());
        // logger.info('\n' + httpRequestString(interferingUri) + "\n\n" + rawResponseString(interferingUri));

        // fetch original again with the credentials
        getFetcher().process(curi);
        String httpRequestString = httpRequestString(curi);
        // logger.info('\n' + httpRequestString + "\n\n" + rawResponseString(interferingUri));
        assertTrue(httpRequestString.contains("Authorization: Digest"));
        // otherwise should be a normal 200 response
        runDefaultChecks(curi, "requestLine", "hostHeader");
        
        // fetch a fresh uri to make sure auth info was cached and we don't get another 401
        curi = makeCrawlURI("http://localhost:7778/auth/2");
        getFetcher().process(curi);
        httpRequestString = httpRequestString(curi);
        assertTrue(httpRequestString.contains("Authorization: Digest"));
        // otherwise should be a normal 200 response
        runDefaultChecks(curi, "requestLine", "hostHeader");
    }
    
    // server for form auth is at localhost:7779
    public void testFormAuth() throws Exception {
        ensureHttpServers();
        
        HtmlFormCredential cred = new HtmlFormCredential();
        cred.setDomain("localhost:7779");
        cred.setLoginUri("/j_security_check");
        HashMap<String, String> formItems = new HashMap<String,String>();
        formItems.put("j_username", FORM_AUTH_LOGIN);
        formItems.put("j_password", FORM_AUTH_PASSWORD);
        cred.setFormItems(formItems);

        getFetcher().getCredentialStore().getCredentials().put("form-auth-credential",
                cred);
        
        CrawlURI curi = makeCrawlURI("http://localhost:7779/");
        getFetcher().process(curi);
        logger.info('\n' + httpRequestString(curi) + contentString(curi));
        runDefaultChecks(curi, "hostHeader");

        // jetty needs us to hit a restricted url so it can redirect to the
        // login page and remember where to redirect back to after successful
        // login (if not we get a NPE within jetty)
        curi = makeCrawlURI("http://localhost:7779/auth/1");
        getFetcher().process(curi);
        logger.info('\n' + httpRequestString(curi) + "\n\n" + rawResponseString(curi));
        assertEquals(302, curi.getFetchStatus());
        assertTrue(curi.getHttpResponseHeader("Location").startsWith("http://localhost:7779/login.html"));
        
        PreconditionEnforcer preconditionEnforcer = new PreconditionEnforcer();
        preconditionEnforcer.setServerCache(getFetcher().getServerCache());
        preconditionEnforcer.setCredentialStore(getFetcher().getCredentialStore());
        boolean result = preconditionEnforcer.credentialPrecondition(curi);
        assertTrue(result);

        CrawlURI loginUri = curi.getPrerequisiteUri();
        assertEquals("http://localhost:7779/j_security_check", loginUri.toString());
        
        // there's some special logic with side effects in here for the login uri itself 
        result = preconditionEnforcer.credentialPrecondition(loginUri);
        assertFalse(result);
        
        loginUri.setRecorder(getRecorder());
        getFetcher().process(loginUri);
        logger.info('\n' + httpRequestString(loginUri) + "\n\n" + rawResponseString(loginUri));
        assertEquals(302, loginUri.getFetchStatus()); // 302 on successful login
        assertEquals("http://localhost:7779/auth/1", loginUri.getHttpResponseHeader("location"));
        
        curi = makeCrawlURI("http://localhost:7779/auth/1");
        getFetcher().process(curi);
        logger.info('\n' + httpRequestString(curi) + contentString(curi));
        runDefaultChecks(curi, "hostHeader", "requestLine");
    }

    protected void checkSetCookieURI() throws URIException, IOException,
            InterruptedException, UnsupportedEncodingException {
        CrawlURI curi = makeCrawlURI("http://localhost:7777/set-cookie");
        getFetcher().process(curi);
        runDefaultChecks(curi, "requestLine");
        
        // check for set-cookie header
        byte[] buf = IOUtils.toByteArray(curi.getRecorder().getReplayInputStream());
        String rawResponseString = new String(buf, "US-ASCII");
        assertTrue(rawResponseString.contains("Set-Cookie: test-cookie-name=test-cookie-value\r\n"));
    }
    
    public void testAcceptCompression() throws Exception {
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().setAcceptCompression(true);
        getFetcher().process(curi);
        String httpRequestString = httpRequestString(curi);
        // logger.info('\n' + httpRequestString + "\n\n" + rawResponseString(curi));
        // logger.info("\n----- begin contentString -----\n" + contentString(curi));
        // logger.info("\n----- begin entityString -----\n" + entityString(curi));
        // logger.info("\n----- begin messageBodyString -----\n" + messageBodyString(curi));
        assertTrue(httpRequestString.contains("Accept-Encoding: gzip,deflate\r\n"));
        assertEquals(DEFAULT_GZIPPED_PAYLOAD.length, curi.getContentLength());
        assertEquals(curi.getContentSize(), curi.getRecordedSize());

        // check various 
        assertEquals("text/plain;charset=US-ASCII", curi.getContentType());
        assertEquals(Charset.forName("US-ASCII"), curi.getRecorder().getCharset());
        assertTrue(curi.getCredentials().isEmpty());
        assertTrue(curi.getFetchDuration() >= 0);
        assertTrue(curi.getFetchStatus() == 200);
        assertTrue(curi.getFetchType() == FetchType.HTTP_GET);

        // check message body, i.e. "raw, possibly chunked-transfer-encoded message contents not including the leading headers"
        assertTrue(Arrays.equals(DEFAULT_GZIPPED_PAYLOAD, IOUtils.toByteArray(curi.getRecorder().getMessageBodyReplayInputStream())));

        // check entity, i.e. "message-body after any (usually-unnecessary) transfer-decoding but before any content-encoding (eg gzip) decoding"
        assertTrue(Arrays.equals(DEFAULT_GZIPPED_PAYLOAD, IOUtils.toByteArray(curi.getRecorder().getEntityReplayInputStream())));

        // check content, i.e. message-body after possibly tranfer-decoding and after content-encoding (eg gzip) decoding
        assertEquals(DEFAULT_PAYLOAD_STRING, contentString(curi));
        assertEquals("sha1:6HXUWMO6VPBHU4SIPOVJ3OPMCSN6JJW4", curi.getContentDigestSchemeString());
    }

    // Binding to 127.0.0.2 only works on some systems. Fails on my mac. We
    // could skip the test in that case, but better to leave it in I think, so
    // we notice if it starts failing on the build box and we can do something
    // about it.
    public void testHttpBindAddress() throws Exception {
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().setHttpBindAddress("127.0.0.2");
        getFetcher().process(curi);

        // the client bind address isn't recorded anywhere in heritrix as
        // far as i can tell, so we get it this way...
        assertEquals("127.0.0.2", lastRequest.getRemoteAddr());

        runDefaultChecks(curi, "httpBindAddress");
    }

    protected static class ProxiedRequestRememberer implements HttpRequestFilter {
        protected HttpRequest lastProxiedRequest = null;
        public HttpRequest getLastProxiedRequest() {
            return lastProxiedRequest;
        }

        @Override
        public void filter(HttpRequest httpRequest) {
            lastProxiedRequest = httpRequest;
        }

        public void clear() {
            lastProxiedRequest = null;
        }
    }

    public void testHttpProxy() throws Exception {
        ensureHttpServers();
        
        ProxiedRequestRememberer proxiedRequestRememberer = new ProxiedRequestRememberer();
        DefaultHttpProxyServer httpProxyServer = new DefaultHttpProxyServer(7877, proxiedRequestRememberer, new HashMap<String, HttpFilter>());
        httpProxyServer.start(true, false);

        try {
            getFetcher().setHttpProxyHost("localhost");
            getFetcher().setHttpProxyPort(7877);

            CrawlURI curi = makeCrawlURI("http://localhost:7777/");
            getFetcher().process(curi);
            // logger.info('\n' + httpRequestString(curi) + "\n\n" + rawResponseString(curi));

            String requestString = httpRequestString(curi);
            assertTrue(requestString.startsWith("GET http://localhost:7777/ HTTP/1.0\r\n"));
            assertNotNull(curi.getHttpResponseHeader("Via"));
            
            assertTrue(requestString.contains("Proxy-Connection: close\r\n"));
            
            // check that our little proxy server really handled a request
            assertNotNull(proxiedRequestRememberer.getLastProxiedRequest());
            
            runDefaultChecks(curi, "requestLine");
        } finally {
            httpProxyServer.stop();
        }
    }
    
    public void testHttpProxyAuth() throws Exception {
        ensureHttpServers();
        
        ProxiedRequestRememberer proxiedRequestRememberer = new ProxiedRequestRememberer();
        DefaultHttpProxyServer httpProxyServer = new DefaultHttpProxyServer(7877, proxiedRequestRememberer, new HashMap<String, HttpFilter>());
        httpProxyServer.addProxyAuthenticationHandler(new ProxyAuthorizationHandler() {
            @Override
            public boolean authenticate(String userName, String password) {
                // logger.info("username=" + userName + " password=" + password);
                return "http-proxy-user".equals(userName) && "http-proxy-password".equals(password);
            }
        });
        httpProxyServer.start(true, false);

        try {
            getFetcher().setHttpProxyHost("localhost");
            getFetcher().setHttpProxyPort(7877);
            getFetcher().setHttpProxyUser("http-proxy-user");
            getFetcher().setHttpProxyPassword("http-proxy-password");

            CrawlURI curi = makeCrawlURI("http://localhost:7777/");
            getFetcher().process(curi);
            // logger.info('\n' + httpRequestString(curi) + "\n\n" + rawResponseString(curi));

            String requestString = httpRequestString(curi);
            assertTrue(requestString.startsWith("GET http://localhost:7777/ HTTP/1.0\r\n"));
            assertTrue(requestString.contains("Proxy-Connection: close\r\n"));
            assertNull(proxiedRequestRememberer.getLastProxiedRequest()); // request didn't make it this far
            assertEquals(407, curi.getFetchStatus());

            // fetch original again now that credentials should be populated
            proxiedRequestRememberer.clear();
            curi = makeCrawlURI("http://localhost:7777/");
            getFetcher().process(curi);
            // logger.info('\n' + httpRequestString(curi) + "\n\n" + rawResponseString(curi));

            requestString = httpRequestString(curi);
            assertTrue(requestString.startsWith("GET http://localhost:7777/ HTTP/1.0\r\n"));
            assertTrue(requestString.contains("Proxy-Connection: close\r\n"));
            assertNotNull(curi.getHttpResponseHeader("Via"));
            assertNotNull(proxiedRequestRememberer.getLastProxiedRequest());
            runDefaultChecks(curi, "requestLine");
        } finally {
            httpProxyServer.stop();
        }
    }
    
    public void testMaxFetchKBSec() throws Exception {
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/200k");
        getFetcher().setMaxFetchKBSec(100);
        getFetcher().process(curi);
        assertEquals(200000, curi.getContentLength());
        assertTrue(curi.getFetchDuration() > 1800 && curi.getFetchDuration() < 2200);
    }
    
    public void testMaxLengthBytes() throws Exception {
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/200k");
        getFetcher().setMaxLengthBytes(50000);
        getFetcher().process(curi);
        assertEquals(50001, curi.getRecordedSize());
    }

    public void testSendRange() throws Exception { 
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/200k");
        getFetcher().setMaxLengthBytes(50000);
        getFetcher().setSendRange(true);
        getFetcher().process(curi);
        // logger.info("\n" + httpRequestString(curi));
        assertTrue(httpRequestString(curi).contains("Range: bytes=0-49999\r\n"));
        // XXX make server honor range and inspect response?
        // assertEquals(50000, curi.getRecordedSize());
    }
    
    public void testSendIfModifiedSince() throws Exception {
        ensureHttpServers();
        
        getFetcher().setSendIfModifiedSince(true);

        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        assertFalse(httpRequestString(curi).toLowerCase().contains("if-modified-since"));
        assertTrue(curi.getHttpResponseHeader("last-modified").equals("Thu, 01 Jan 1970 00:00:00 GMT"));
        runDefaultChecks(curi);

        // logger.info("before FetchHistoryProcessor fetchHistory=" + Arrays.toString(curi.getFetchHistory()));
        FetchHistoryProcessor fetchHistoryProcessor = new FetchHistoryProcessor();
        fetchHistoryProcessor.process(curi);
        // logger.info("after FetchHistoryProcessor fetchHistory=" + Arrays.toString(curi.getFetchHistory()));

        getFetcher().process(curi);
        // logger.info("\n" + httpRequestString(curi));
        assertTrue(httpRequestString(curi).contains("If-Modified-Since: Thu, 01 Jan 1970 00:00:00 GMT\r\n"));
        runDefaultChecks(curi);
        // XXX make server send 304 not-modified and check for it here?
    }
    
    public void testSendIfNoneMatch() throws Exception {
        ensureHttpServers();
        
        getFetcher().setSendIfNoneMatch(true);
        
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().process(curi);
        assertFalse(httpRequestString(curi).toLowerCase().contains("if-none-match"));
        assertTrue(curi.getHttpResponseHeader("etag").equals(ETAG_TEST_VALUE));
        runDefaultChecks(curi);

        logger.info("before FetchHistoryProcessor fetchHistory=" + Arrays.toString(curi.getFetchHistory()));
        FetchHistoryProcessor fetchHistoryProcessor = new FetchHistoryProcessor();
        fetchHistoryProcessor.process(curi);
        logger.info("after FetchHistoryProcessor fetchHistory=" + Arrays.toString(curi.getFetchHistory()));

        getFetcher().process(curi);
        logger.info("\n" + httpRequestString(curi));
        assertTrue(httpRequestString(curi).contains("If-None-Match: " + ETAG_TEST_VALUE + "\r\n"));
        runDefaultChecks(curi);
        // XXX make server send 304 not-modified and check for it here?
    }
    
    public void testShouldFetchBodyRule() throws Exception {
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("http://localhost:7777/");
        getFetcher().setShouldFetchBodyRule(new RejectDecideRule());
        getFetcher().process(curi);
        logger.info('\n' + httpRequestString(curi) + "\n\n" + rawResponseString(curi));

        assertTrue(httpRequestString(curi).startsWith("GET / HTTP/1.0\r\n"));
        assertEquals("text/plain;charset=US-ASCII", curi.getContentType());
        assertTrue(curi.getCredentials().isEmpty());
        assertTrue(curi.getFetchDuration() >= 0);
        assertTrue(curi.getFetchStatus() == 200);
        assertTrue(curi.getFetchType() == FetchType.HTTP_GET);
        
        // check for empty body
        assertEquals(0, curi.getContentLength());
        assertEquals(curi.getContentSize(), curi.getRecordedSize());
        assertEquals("", messageBodyString(curi));
        assertEquals("", entityString(curi));
    }

    public void testFetchTimeout() throws Exception {
        ensureHttpServers();
        
        CrawlURI curi = makeCrawlURI("http://localhost:7777/slow.txt");
        getFetcher().setTimeoutSeconds(2);
        getFetcher().process(curi);
        
        // logger.info('\n' + httpRequestString(curi) + "\n\n" + rawResponseString(curi));
        assertTrue(curi.getAnnotations().contains("timeTrunc"));
        assertTrue(curi.getFetchDuration() >= 2000 && curi.getFetchDuration() < 2200);
    }

    // see http://stackoverflow.com/questions/100841/artificially-create-a-connection-timeout-error
    public void testConnectionTimeout() throws Exception {
        CrawlURI curi = makeCrawlURI("http://10.255.255.1/");
        getFetcher().setSoTimeoutMs(300);
        
        long start = System.currentTimeMillis();
        getFetcher().process(curi);
        long elapsed = System.currentTimeMillis() - start;
        
        assertTrue(elapsed >= 300 && elapsed < 400);
        
        // Httpcomponents throws org.apache.http.conn.ConnectTimeoutException,
        // commons-httpclient throws java.net.SocketTimeoutException. Both are
        // instances of InterruptedIOException
        assertEquals(1, curi.getNonFatalFailures().size());
        assertTrue(curi.getNonFatalFailures().toArray()[0] instanceof InterruptedIOException);
        assertTrue(curi.getNonFatalFailures().toArray()[0].toString().matches("(?i).*connect.*timed out.*"));

        assertEquals(FetchStatusCodes.S_CONNECT_FAILED, curi.getFetchStatus());
        
        assertEquals(0, curi.getFetchCompletedTime());
    }
    
    // XXX testSocketTimeout() (the other kind) - how to simulate?
    
    public void testSslTrustLevel() throws Exception {
        ensureHttpServers();
        CrawlURI curi = makeCrawlURI("https://localhost:7443/");
        getFetcher().process(curi);
        runDefaultChecks(curi, "hostHeader");
    }
}
