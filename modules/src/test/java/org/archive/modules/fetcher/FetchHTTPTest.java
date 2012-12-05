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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.archive.modules.ProcessorTestBase;
import org.archive.util.TmpDirTestCase;
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

public class FetchHTTPTest extends ProcessorTestBase {

//    private static Logger logger = Logger.getLogger(FetchHTTPTest.class.getName());
//
//    /* uncomment this for detailed logging from jetty and heritrix */
//    static {
//        Logger.getLogger("").setLevel(Level.FINE);
//        for (java.util.logging.Handler h: Logger.getLogger("").getHandlers()) {
//            h.setLevel(Level.ALL);
//            h.setFormatter(new OneLineSimpleLogger());
//        }
//    }
    
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
                response.addCookie(new javax.servlet.http.Cookie("test-cookie-name", "test-cookie-value"));
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
            } else if (target.equals("/chunked.txt")) {
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(HttpServletResponse.SC_OK);
                // response.setContentLength(HttpTokens.CHUNKED_CONTENT);
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                response.getOutputStream().flush();
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
    
    public static Test suite() {
        
        return new TestSetup(new TestSuite(FetchHTTPTests.class)) {
            @Override
            protected void setUp() throws Exception {
                super.setUp();
                ensureHttpServers();
            }
            @Override
            protected void tearDown() throws Exception {
                super.tearDown();
                
                if (httpServers != null) {
                    for (Server server: httpServers.values()) {
                        server.stop();
                        server.destroy();
                    }
                    httpServers = null;
                }
            }
        };
    }

    public static Request getLastRequest() {
        return lastRequest;
    }
}
