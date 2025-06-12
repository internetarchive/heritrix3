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

import com.sun.net.httpserver.HttpServer;
import org.archive.bdb.BdbModule;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.archive.spring.ConfigPath;
import org.archive.util.Recorder;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class FetchHTTP2Test {
    private static HttpServer server;

    @TempDir
    Path tempDir;
    private BdbModule bdb;
    private BdbCookieStore cookieStore;
    private FetchHTTP2 fetcher;
    private static String baseUrl;
    private Recorder recorder;

    @BeforeAll
    public static void beforeAll() throws IOException {
        InetAddress loopbackAddress = Inet4Address.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopbackAddress, 0), -1);
        server.createContext("/", exchange -> {
            if (exchange.getRequestHeaders().containsKey("Via")) {
                exchange.getResponseHeaders().add("Used-Proxy", "true");
            }
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().add("Set-Cookie", "foo=bar; Path=/");
            byte[] body = "Hello World!".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/gzip", exchange -> {
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            byte[] body = "Hello World!".getBytes();
            exchange.sendResponseHeaders(200, 0);
            try (var gzip = new GZIPOutputStream(exchange.getResponseBody())) {
                gzip.write(body);
            }
            exchange.close();
        });

        server.start();
        baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/";
    }

    @AfterAll
    public static void afterAll() {
        if (server != null) server.stop(0);
    }

    @BeforeEach
    public void beforeEach() throws IOException {
        cookieStore = new BdbCookieStore();
        bdb = new BdbModule();
        Path cookies = tempDir.resolve("cookies");
        Files.createDirectories(cookies);
        bdb.setDir(new ConfigPath("cookies", cookies.toString()));
        cookieStore.setBdbModule(bdb);
        var serverCache = new DefaultServerCache();
        bdb.start();
        cookieStore.start();
        fetcher = new FetchHTTP2(serverCache, cookieStore);
        fetcher.setUserAgentProvider(new CrawlMetadata());
        recorder = new Recorder(tempDir.toFile(), "temp");
    }

    @AfterEach
    public void afterEach() {
        fetcher.stop();
        cookieStore.stop();
        bdb.stop();
        recorder.cleanup();
    }

    @Test
    public void test() throws Exception {
        fetcher.start();
        var curi = new CrawlURI(UURIFactory.getInstance(baseUrl));
        curi.setRecorder(recorder);
        fetcher.innerProcess(curi);

        assertEquals(200, curi.getFetchStatus());
        assertEquals(CrawlURI.FetchType.HTTP_GET, curi.getFetchType());
        assertEquals(12, curi.getContentLength());
        assertEquals("text/html; charset=UTF-8", curi.getContentType());
        assertEquals("UTF-8", curi.getRecorder().getCharset().name());
        assertEquals(Inet4Address.getLoopbackAddress().getHostAddress(), curi.getServerIP());
        assertEquals("Hello World!", curi.getRecorder().getContentReplayPrefixString(100));
        assertEquals("foo=bar; Path=/", curi.getHttpResponseHeader("Set-Cookie"));
        assertTrue(curi.getFetchBeginTime() > 1);
        assertTrue(curi.getFetchCompletedTime() >= curi.getFetchBeginTime());
        assertNull(curi.getHttpResponseHeader("Used-Proxy"));
        curi.getRecorder().cleanup();
    }

    @Test
    public void testHttpProxy() throws Exception {
        Server proxyServer = new Server(new InetSocketAddress(Inet4Address.getLoopbackAddress(), 0));
        proxyServer.setHandler(new ProxyHandler.Forward());
        proxyServer.start();
        try {
            var proxyPort = ((ServerConnector) proxyServer.getConnectors()[0]).getLocalPort();
            fetcher.setHttpProxyHost(Inet4Address.getLoopbackAddress().getHostAddress());
            fetcher.setHttpProxyPort(proxyPort);
            fetcher.start();
            var curi = new CrawlURI(UURIFactory.getInstance(baseUrl));
            curi.setRecorder(recorder);
            fetcher.innerProcess(curi);
            assertEquals("true", curi.getHttpResponseHeader("Used-Proxy"));
            assertEquals(200, curi.getFetchStatus());
            assertEquals("Hello World!", curi.getRecorder().getContentReplayPrefixString(100));
        } finally {
            proxyServer.stop();
        }
    }

    @Test
    public void testGzipEncoding() throws Exception {
        fetcher.start();
        var curi = new CrawlURI(UURIFactory.getInstance(baseUrl + "gzip"));
        curi.setRecorder(recorder);
        fetcher.innerProcess(curi);
        assertEquals(200, curi.getFetchStatus());
        assertEquals(CrawlURI.FetchType.HTTP_GET, curi.getFetchType());
        assertEquals(32, curi.getContentLength());
        assertEquals("text/plain", curi.getContentType());
        assertEquals("gzip", curi.getRecorder().getContentEncoding());
        assertEquals("Hello World!", curi.getRecorder().getContentReplayPrefixString(100));
        curi.getRecorder().cleanup();
    }
}