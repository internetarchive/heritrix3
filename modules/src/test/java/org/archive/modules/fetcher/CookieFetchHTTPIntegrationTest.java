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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections.Closure;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessorTestBase;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.spring.ConfigFile;
import org.archive.spring.ConfigPath;
import org.archive.util.KeyTool;
import org.archive.util.TmpDirTestCase;

import com.google.common.io.Files;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class CookieFetchHTTPIntegrationTest extends ProcessorTestBase {

    protected static class TestHandler extends SessionHandler {
        public TestHandler() {
            super();
        }

        @Override
        public void doHandle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            if (request.getParameter("name") != null) {
                Cookie cookie = new javax.servlet.http.Cookie(request.getParameter("name"), 
                        request.getParameter("value"));
                if (request.getParameter("domain") != null) {
                    cookie.setDomain(request.getParameter("domain"));
                }
                if (request.getParameter("path") != null) {
                    cookie.setDomain(request.getParameter("path"));
                }
                if (request.getParameter("maxAge") != null) {
                    cookie.setMaxAge(Integer.valueOf(request.getParameter("maxAge")));
                }
                if (request.getParameter("secure") != null) {
                    cookie.setSecure(request.getParameter("secure").equals(1));
                }
                if (request.getParameter("comment") != null) {
                    cookie.setComment(request.getParameter("comment"));
                }
                if (request.getParameter("version") != null) {
                    cookie.setVersion(Integer.valueOf(request.getParameter("version")));
                }
                response.addCookie(cookie);
            }

            response.setContentType("text/plain;charset=US-ASCII");
            response.setStatus(200);

            if (request.getCookies() != null) {
                response.getOutputStream().println(request.getCookies().length + " cookies received");
                for (int i = 0; i < request.getCookies().length; i++) {
                    response.getOutputStream().println(i + ". " + request.getCookies()[0]);
                }
            } else {
                response.getOutputStream().println("0 cookies received");
            }

            ((Request)request).setHandled(true);
        }
    }

    public static Server startHttpServer() throws Exception {
        Log.getLogger(Server.class.getCanonicalName()).setDebugEnabled(true);

        Server server = new Server();

        server.setHandler(new TestHandler());

        ServerConnector sc = new ServerConnector(server);
        sc.setHost("127.0.0.1");
        sc.setPort(7777);

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

        server.addConnector(sc);
        server.addConnector(ssc);
        server.start();

        return server;
    }

    public static Test suite() {
        return new TestSetup(new TestSuite(CookieFetchHTTPIntegrationTests.class)) {
            private Server server;
            @Override
            protected void setUp() throws Exception {
                super.setUp();
                server = startHttpServer();
            }
            @Override
            protected void tearDown() throws Exception {
                super.tearDown();
                server.stop();
                server.destroy();
            }
        };
    }

    public static class AlwaysLocalhostServerCache extends ServerCache {
        protected static final InetAddress LOCALHOST;
        static {
            try {
                LOCALHOST = InetAddress.getByName("127.0.0.1");
            } catch (UnknownHostException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public CrawlHost getHostFor(String host) {
            CrawlHost h = new CrawlHost(host);
            h.setIP(LOCALHOST, -1);
            return h;
        }
        @Override 
        public CrawlServer getServerFor(String serverKey) {
            CrawlServer s = new CrawlServer(serverKey);
            return s;
        }
        @Override public void forAllHostsDo(Closure action) { throw new RuntimeException("not implemented"); }
        @Override public Set<String> hostKeys() { throw new RuntimeException("not implemented"); }
    }

    public static class CookieFetchHTTPIntegrationTests extends ProcessorTestBase {

        @Override
        protected Class<?> getModuleClass() {
            return BdbCookieStore.class;
        }

        protected FetchHTTP fetcher;
        protected BdbModule bdb;
        protected BdbCookieStore bdbCookieStore;

        protected File tmpdir = Files.createTempDir();

        protected BdbModule bdb() throws IOException {
            if (bdb == null) {
                ConfigPath basePath = new ConfigPath("testBase",
                        tmpdir.getAbsolutePath());
                ConfigPath bdbDir = new ConfigPath("bdb", "bdb");
                bdbDir.setBase(basePath); 
                FileUtils.deleteDirectory(bdbDir.getFile());

                bdb = new BdbModule();
                bdb.setDir(bdbDir);
                bdb.start();
            }
            return bdb;
        }

        protected AbstractCookieStore bdbCookieStore() throws IOException {
            if (bdbCookieStore == null) {
                bdbCookieStore = new BdbCookieStore();
                ConfigPath basePath = new ConfigPath("testBase", 
                        tmpdir.getAbsolutePath());
                ConfigFile cookiesSaveFile = new ConfigFile("cookiesSaveFile", "cookies.txt");
                cookiesSaveFile.setBase(basePath);
                bdbCookieStore.setCookiesSaveFile(cookiesSaveFile);
                bdbCookieStore.setBdbModule(bdb());
                bdbCookieStore.start();
            }
            return bdbCookieStore;
        }

        protected SimpleCookieStore simpleCookieStore;
        protected SimpleCookieStore simpleCookieStore() {
            if (simpleCookieStore == null) {
                simpleCookieStore = new SimpleCookieStore();
                simpleCookieStore.start();
            }
            return simpleCookieStore;
        }

        protected FetchHTTP fetcher() {
            if (fetcher == null) {
                fetcher = new FetchHTTP();
                // f.setCookieStore(cookieStore);
                fetcher.setServerCache(new AlwaysLocalhostServerCache());
                CrawlMetadata uap = new CrawlMetadata();
                uap.setUserAgentTemplate(getClass().getName());
                fetcher.setUserAgentProvider(uap);
                fetcher.start();
            }
            return fetcher;
        }

        public void testNoCookie() throws IOException, InterruptedException {
            testNoCookie(simpleCookieStore());
            testNoCookie(bdbCookieStore());
        }

        protected void testNoCookie(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            // check second fetch has no cookies
            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(0, cookieStore.getCookies().size());
        }

        // name value domain path maxAge secure comment version

        protected void testBasics(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://example.com:7777/?name=foo&value=bar");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar\r\n"));

            // check second fetch has expected cookie
            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(1, cookieStore.getCookies().size());
        }

        public void testBasics() throws URIException, IOException, InterruptedException {
            testBasics(simpleCookieStore());
            testBasics(bdbCookieStore());
        }

        protected void testImplicitDomain(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://example.com:7777/?name=foo&value=bar");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar\r\n"));

            // check second fetch has expected cookie
            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            // check fetch to different domain has no cookie
            curi = makeCrawlURI("http://example.ORG:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            /*
             * XXX I think browsers differ on this behavior. This is what
             * org.apache.http.impl.cookie.BrowserCompatSpec does.
             */
            curi = makeCrawlURI("http://SUBDOMAIN.example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(1, cookieStore.getCookies().size());
        }

        public void testImplicitDomain() throws URIException, IOException, InterruptedException {
            testImplicitDomain(simpleCookieStore());
            testImplicitDomain(bdbCookieStore());
        }

        protected void testExplicitDomain(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://example.com:7777/?name=foo&value=bar&domain=example.com");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar; Domain=example.com\r\n"));

            // check second fetch has expected cookie
            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            // check fetch to different domain has no cookie
            curi = makeCrawlURI("http://example.ORG:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://SUBDOMAIN.example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(1, cookieStore.getCookies().size());
        }

        public void testExplicitDomain() throws URIException, IOException, InterruptedException {
            testExplicitDomain(simpleCookieStore());
            testExplicitDomain(bdbCookieStore());
        }

        protected void testExplicitDomainWithLeadingDot(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://example.com:7777/?name=foo&value=bar&domain=.example.com");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar; Domain=.example.com\r\n"));

            // check second fetch has expected cookie
            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            // check fetch to different domain has no cookie
            curi = makeCrawlURI("http://example.ORG:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://SUBDOMAIN.example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(1, cookieStore.getCookies().size());
        }

        public void testExplicitDomainWithLeadingDot() throws URIException, IOException, InterruptedException {
            testExplicitDomainWithLeadingDot(simpleCookieStore());
            testExplicitDomainWithLeadingDot(bdbCookieStore());
        }

        protected void testRejectDomain(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://example.com:7777/?name=foo&value=bar&domain=somethingelse.com");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar; Domain=somethingelse.com\r\n"));

            // check fetch of original domain has no cookie
            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            // check fetch of cookie domain has no cookie
            curi = makeCrawlURI("http://somethingelse.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(0, cookieStore.getCookies().size());

            // reject wrong subdomain

            curi = makeCrawlURI("http://FOO.example.com:7777/?name=foo&value=bar&domain=BAR.example.com");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar; Domain=bar.example.com\r\n"));

            // check fetch of original domain has no cookie
            curi = makeCrawlURI("http://foo.example.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            // check fetch of cookie domain has no cookie
            curi = makeCrawlURI("http://bar.example.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(0, cookieStore.getCookies().size());
        }

        public void testRejectDomain() throws URIException, IOException, InterruptedException {
            testRejectDomain(simpleCookieStore());
            testRejectDomain(bdbCookieStore());
        }

        protected void testSubdomainParentDomain(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://FOO.example.com:7777/?name=foo&value=bar&domain=example.com");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar; Domain=example.com\r\n"));

            curi = makeCrawlURI("http://FOO.example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://BAR.example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://somethingelse.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(1, cookieStore.getCookies().size());
        }

        public void testSubdomainParentDomain() throws URIException, IOException, InterruptedException {
            testSubdomainParentDomain(simpleCookieStore());
            testSubdomainParentDomain(bdbCookieStore());
        }

        protected void testIPAddress(AbstractCookieStore cookieStore) throws URIException, IOException, InterruptedException {
            cookieStore.clear();
            fetcher().setCookieStore(cookieStore);

            CrawlURI curi = makeCrawlURI("http://10.0.0.1:7777/?name=foo&value=bar");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertTrue(FetchHTTPTests.rawResponseString(curi).contains("Set-Cookie: foo=bar\r\n"));

            curi = makeCrawlURI("http://10.0.0.1:7777/");
            fetcher().process(curi);
            assertTrue(FetchHTTPTests.httpRequestString(curi).contains("Cookie: foo=bar\r\n"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://192.168.0.1:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://10.0.0.2:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            curi = makeCrawlURI("http://example.com:7777/");
            fetcher().process(curi);
            assertFalse(FetchHTTPTests.httpRequestString(curi).toLowerCase().contains("cookie:"));
            assertFalse(FetchHTTPTests.rawResponseString(curi).toLowerCase().contains("set-cookie:"));

            assertEquals(1, cookieStore.getCookies().size());
        }

        public void testIPAddress() throws URIException, IOException, InterruptedException {
            testIPAddress(simpleCookieStore());
            testIPAddress(bdbCookieStore());
        }
    }
}
