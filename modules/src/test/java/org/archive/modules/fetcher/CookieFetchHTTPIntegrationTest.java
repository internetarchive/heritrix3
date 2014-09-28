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

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

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
import org.archive.util.TmpDirTestCase;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.security.SslSocketConnector;
import org.mortbay.jetty.servlet.SessionHandler;
import org.mortbay.log.Log;

import sun.security.tools.KeyTool;

import com.google.common.io.Files;

@SuppressWarnings("restriction")
public class CookieFetchHTTPIntegrationTest extends ProcessorTestBase {

//    private static Logger logger = Logger.getLogger(FetchHTTPTest.class.getName());
    //    static {
    //        Logger.getLogger("").setLevel(Level.FINE);
    //        for (java.util.logging.Handler h: Logger.getLogger("").getHandlers()) {
    //            h.setLevel(Level.ALL);
    //            h.setFormatter(new OneLineSimpleLogger());
    //        }
    //    }

    protected static final String DEFAULT_PAYLOAD_STRING = "abcdefghijklmnopqrstuvwxyz0123456789\n";

    protected static class TestHandler extends SessionHandler {
        public TestHandler() {
            super();
        }
        
        @Override
        public void handle(String target, HttpServletRequest request,
                HttpServletResponse response, int dispatch) throws IOException,
                ServletException {
            if (request.getParameter("name") != null) {
                Cookie cookie = new javax.servlet.http.Cookie(request.getParameter("value"), 
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
        
        SocketConnector sc = new SocketConnector();
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
        
        SslSocketConnector ssc = new SslSocketConnector();
        ssc.setHost("127.0.0.1");
        ssc.setPort(7443);
        ssc.setKeyPassword(KEYSTORE_PASSWORD);
        ssc.setKeystore(keystoreFile.getPath());

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
                LOCALHOST = InetAddress.getLocalHost();
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
        protected FetchHTTP f0, f1;
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
        
        protected BdbCookieStore bdbCookieStore() throws IOException {
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
        
        protected FetchHTTP fetcher0() throws IOException {
            if (f0 == null) { 
                f0 = makeFetcher(new SimpleCookieStore());
            }
            return f0;
        }
        
        protected FetchHTTP fetcher1() throws IOException {
            if (f1 == null) { 
                f1 = makeFetcher(bdbCookieStore());
            }
            return f1;
        }

        protected FetchHTTP makeFetcher(AbstractCookieStore cookieStore) {
            FetchHTTP f = new FetchHTTP();
            f.setCookieStore(cookieStore);
            f.setServerCache(new AlwaysLocalhostServerCache());
            CrawlMetadata uap = new CrawlMetadata();
            uap.setUserAgentTemplate(getClass().getName());
            f.setUserAgentProvider(uap);

            f.start();
            return f;
        }

        public void testNoCookie() throws IOException, InterruptedException {
            testNoCookie(fetcher0());
            testNoCookie(fetcher1());
        }

        protected void testNoCookie(FetchHTTP f) throws URIException, IOException, InterruptedException {
            CrawlURI curi = makeCrawlURI("http://localhost:7777/");
            f.process(curi);
            
            String rawResponse = FetchHTTPTests.rawResponseString(curi);
            assertFalse(rawResponse.toLowerCase().contains("set-cookie:"));
            
            String requestString = FetchHTTPTests.httpRequestString(curi);
            assertFalse(requestString.toLowerCase().contains("cookie:"));
        }
        
        @Override
        protected Class<?> getModuleClass() {
            return BdbCookieStore.class;
        }
    }
}
