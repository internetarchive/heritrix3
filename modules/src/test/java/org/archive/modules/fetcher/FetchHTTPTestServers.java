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

import org.archive.util.KeyTool;
import org.archive.util.TmpDirTestCase;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.DigestAuthenticator;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import static org.junit.Assert.assertTrue;

public class FetchHTTPTestServers {

//    private static Logger logger = Logger.getLogger(FetchHTTPTest.class.getName());
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

    protected static final String ETAG_TEST_VALUE = "An ETag is an opaque identifier assigned by a web server to a specific version of a resource found at a URL!";

    protected static final String DEFAULT_PAYLOAD_STRING = "abcdefghijklmnopqrstuvwxyz0123456789\n";

    protected static final byte[] DEFAULT_GZIPPED_PAYLOAD = { 31, -117, 8, 0,
            -69, 25, 60, 80, 0, 3, 75, 76, 74, 78, 73, 77, 75, -49, -56, -52,
            -54, -50, -55, -51, -53, 47, 40, 44, 42, 46, 41, 45, 43, -81, -88,
            -84, 50, 48, 52, 50, 54, 49, 53, 51, -73, -80, -28, 2, 0, -43, 104,
            -33, -11, 37, 0, 0, 0 };
    
    protected static final byte[] CP1251_PAYLOAD = {
        (byte) 0xca, (byte) 0xee, (byte) 0xf7, (byte) 0xe0, (byte) 0xed, (byte) 0xe8,
        (byte) 0x20, (byte) 0xce, (byte) 0xf0, (byte) 0xea, (byte) 0xe5, (byte) 0xf1,
        (byte) 0xf2, (byte) 0xe0, (byte) 0xf0, (byte) 0x20, (byte) 0xe5, (byte) 0x20,
        (byte) 0xe5, (byte) 0xe4, (byte) 0xe5, (byte) 0xed, (byte) 0x20, (byte) 0xee,
        (byte) 0xe4, (byte) 0x20, (byte) 0xed, (byte) 0xe0, (byte) 0xbc, (byte) 0xef,
        (byte) 0xee, (byte) 0xe7, (byte) 0xed, (byte) 0xe0, (byte) 0xf2, (byte) 0xe8,
        (byte) 0xf2, (byte) 0xe5, (byte) 0x20, (byte) 0xe8, (byte) 0x20, (byte) 0xed,
        (byte) 0xe0, (byte) 0xbc, (byte) 0xef, (byte) 0xee, (byte) 0xef, (byte) 0xf3,
        (byte) 0xeb, (byte) 0xe0, (byte) 0xf0, (byte) 0xed, (byte) 0xe8, (byte) 0xf2,
        (byte) 0xe5, (byte) 0x20, (byte) 0xe1, (byte) 0xeb, (byte) 0xe5, (byte) 0xf5,
        (byte) 0x2d, (byte) 0xee, (byte) 0xf0, (byte) 0xea, (byte) 0xe5, (byte) 0xf1,
        (byte) 0xf2, (byte) 0xf0, (byte) 0xe8, (byte) 0x20, (byte) 0xe2, (byte) 0xee,
        (byte) 0x20, (byte) 0xf1, (byte) 0xe2, (byte) 0xe5, (byte) 0xf2, (byte) 0xee,
        (byte) 0xf2, (byte) 0x2c, (byte) 0x20, (byte) 0xea, (byte) 0xee, (byte) 0xbc,
        (byte) 0x20, (byte) 0xe3, (byte) 0xee, (byte) 0x20, (byte) 0xf1, (byte) 0xee,
        (byte) 0xf7, (byte) 0xe8, (byte) 0xed, (byte) 0xf3, (byte) 0xe2, (byte) 0xe0,
        (byte) 0xe0, (byte) 0xf2, (byte) 0x20, (byte) 0xe4, (byte) 0xe5, (byte) 0xf1,
        (byte) 0xe5, (byte) 0xf2, (byte) 0xec, (byte) 0xe8, (byte) 0xed, (byte) 0xe0,
        (byte) 0x20, (byte) 0xd0, (byte) 0xee, (byte) 0xec, (byte) 0xe8, (byte) 0x2d,
        (byte) 0xcc, (byte) 0xe0, (byte) 0xea, (byte) 0xe5, (byte) 0xe4, (byte) 0xee,
        (byte) 0xed, (byte) 0xf6, (byte) 0xe8, (byte) 0x20, (byte) 0xef, (byte) 0xee,
        (byte) 0x20, (byte) 0xef, (byte) 0xee, (byte) 0xf2, (byte) 0xe5, (byte) 0xea,
        (byte) 0xeb, (byte) 0xee, (byte) 0x20, (byte) 0xee, (byte) 0xe4, (byte) 0x20,
        (byte) 0xca, (byte) 0xee, (byte) 0xf7, (byte) 0xe0, (byte) 0xed, (byte) 0xe8,
        (byte) 0x2c, (byte) 0x20, (byte) 0xef, (byte) 0xf0, (byte) 0xe5, (byte) 0xe4,
        (byte) 0xe2, (byte) 0xee, (byte) 0xe4, (byte) 0xe5, (byte) 0xed, (byte) 0xe8,
        (byte) 0x20, (byte) 0xee, (byte) 0xe4, (byte) 0x20, (byte) 0xf2, (byte) 0xf0,
        (byte) 0xf3, (byte) 0xe1, (byte) 0xe0, (byte) 0xf7, (byte) 0xee, (byte) 0xf2,
        (byte) 0x20, (byte) 0xcd, (byte) 0xe0, (byte) 0xe0, (byte) 0xf2, (byte) 0x20,
        (byte) 0x28, (byte) 0xcd, (byte) 0xe5, (byte) 0xe0, (byte) 0xf2, (byte) 0x29,
        (byte) 0x20, (byte) 0xc2, (byte) 0xe5, (byte) 0xeb, (byte) 0xe8, (byte) 0xee,
        (byte) 0xe2, (byte) 0x2e, (byte) 0x0a,
    };

    protected static final byte[] EIGHTY_BYTE_LINE = "1234567890123456789012345678901234567890123456789012345678901234567890123456789\n".getBytes();

    protected static class TestHandler extends SessionHandler {

        public TestHandler() {
            super();
        }

        @Override
        public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            // echo the remote host back to the client so tests can reference it
            response.setHeader("Client-Host", request.getRemoteHost());

            if (target.endsWith("/set-cookie")) {
                response.addCookie(new javax.servlet.http.Cookie("test-cookie-name", "test-cookie-value"));
            }
            
            if (target.equals("/200k")) {
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(200);
                assertTrue(EIGHTY_BYTE_LINE.length == 80);
                for (int i = 0; i < 200000 / EIGHTY_BYTE_LINE.length; i++) {
                    response.getOutputStream().write(EIGHTY_BYTE_LINE);
                }
                ((Request)request).setHandled(true);
            } else if (target.equals("/slow.txt")) {
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(200);
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
                response.setStatus(200);
                // response.setContentLength(HttpTokens.CHUNKED_CONTENT);
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                response.getOutputStream().flush();
                ((Request)request).setHandled(true);
            } else if (request.getHeader("Accept-Encoding") != null
                    && request.getHeader("Accept-Encoding").contains("gzip")) {
                response.setHeader("Content-Encoding", "gzip");
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(200);
                response.getOutputStream().write(DEFAULT_GZIPPED_PAYLOAD);
                ((Request)request).setHandled(true);
            } else if (target.equals("/401-no-challenge")) {
                response.setStatus(401);
                response.setContentType("text/plain;charset=US-ASCII");
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                ((Request)request).setHandled(true);
            } else if (target.equals("/cp1251")) {
                response.setContentType("text/plain;charset=cp1251");
                response.setStatus(200);
                response.getOutputStream().write(CP1251_PAYLOAD);
                ((Request)request).setHandled(true);
            } else if (target.equals("/unsupported-charset")) {
                response.setContentType("text/plain;charset=UNSUPPORTED-CHARSET");
                response.setStatus(200);
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                ((Request)request).setHandled(true);
            } else if (target.equals("/invalid-charset")) {
                response.setContentType("text/plain;charset=%%INVALID-CHARSET%%");
                response.setStatus(200);
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                ((Request)request).setHandled(true);
            } else if (target.equals("/if-modified-since")) {
                if (request.getHeader("if-modified-since") != null) {
                    response.setStatus(304);
                    ((Request)request).setHandled(true);
                } else {
                    response.setContentType("text/plain;charset=US-ASCII");
                    response.setDateHeader("Last-Modified", 0);
                    response.setStatus(200);
                    response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                    ((Request)request).setHandled(true);
                }
            } else if (target.equals("/if-none-match")) {
                if (request.getHeader("if-none-match") != null) {
                    response.setStatus(304);
                    ((Request)request).setHandled(true);
                } else {
                    response.setContentType("text/plain;charset=US-ASCII");
                    response.setHeader("ETag", ETAG_TEST_VALUE);
                    response.setStatus(200);
                    response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                    ((Request)request).setHandled(true);
                }
            } else {
                response.setContentType("text/plain;charset=US-ASCII");
                response.setStatus(200);
                response.getOutputStream().write(DEFAULT_PAYLOAD_STRING.getBytes("US-ASCII"));
                ((Request)request).setHandled(true);
            }
        }
    }

    protected static Map<Integer, Server> httpServers;

    protected static SecurityHandler makeAuthWrapper(Authenticator authenticator,
            final String role, String realm, final String login,
            final String password) {
        Constraint constraint = new Constraint();
        constraint.setRoles(new String[] { role });
        constraint.setAuthenticate(true);

        ConstraintMapping constraintMapping = new ConstraintMapping();
        constraintMapping.setConstraint(constraint);
        constraintMapping.setPathSpec("/auth/*");

        ConstraintSecurityHandler authWrapper = new ConstraintSecurityHandler();
        authWrapper.setAuthenticator(authenticator);
        
        authWrapper.setConstraintMappings(new ConstraintMapping[] {constraintMapping});
        UserStore userStore = new UserStore();
        userStore.addUser(login, new Password(password), new String[] {role});
        HashLoginService loginService = new HashLoginService(realm);
        loginService.setUserStore(userStore);
        authWrapper.setLoginService(loginService);

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
        
        ServerConnector sc = new ServerConnector(server);
        sc.setHost("127.0.0.1");
        sc.setPort(7777);
        server.addConnector(sc);

        SecurityHandler authWrapper = makeAuthWrapper(new BasicAuthenticator(),
                BASIC_AUTH_ROLE, BASIC_AUTH_REALM, BASIC_AUTH_LOGIN,
                BASIC_AUTH_PASSWORD);
        HandlerCollection handlers = new HandlerCollection();
        handlers.addHandler(new TestHandler());
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

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePassword(KEYSTORE_PASSWORD);
        sslContextFactory.setKeyStorePath(keystoreFile.getPath());

        HttpConfiguration httpsConfig = new HttpConfiguration();
        httpsConfig.addCustomizer(new SecureRequestCustomizer());

        ServerConnector ssc = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()),
                new HttpConnectionFactory(httpsConfig));
        ssc.setHost("127.0.0.1");
        ssc.setPort(7443);

        server.addConnector(ssc);
        
        server.start();

        // server for digest auth
        server = new Server();
        
        sc = new ServerConnector(server);
        sc.setHost("127.0.0.1");
        sc.setPort(7778);
        server.addConnector(sc);
        
        authWrapper = makeAuthWrapper(new DigestAuthenticator(),
                DIGEST_AUTH_ROLE, DIGEST_AUTH_REALM, DIGEST_AUTH_LOGIN,
                DIGEST_AUTH_PASSWORD);
        HandlerCollection handlers2 = new HandlerCollection();
        handlers2.addHandler(new TestHandler());
        authWrapper.setHandler(handlers2);
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

    public static void stopHttpServers() throws Exception {
        if (httpServers != null) {
            for (Server server: httpServers.values()) {
                server.stop();
                server.destroy();
            }
            httpServers = null;
        }
    }
}
