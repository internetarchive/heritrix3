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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FetchHTTP2Test {
    @TempDir
    Path tempDir;

    @Test
    public void test() throws Exception {
        InetAddress loopbackAddress = Inet4Address.getLoopbackAddress();
        var server = HttpServer.create(new InetSocketAddress(loopbackAddress, 0), -1);
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.getResponseHeaders().add("Set-Cookie", "foo=bar; Path=/");
            byte[] body = "Hello World!".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        BdbCookieStore cookieStore = new BdbCookieStore();
        BdbModule bdb = new BdbModule();
        Path cookies = tempDir.resolve("cookies");
        Files.createDirectories(cookies);
        bdb.setDir(new ConfigPath("cookies", cookies.toString()));
        cookieStore.setBdbModule(bdb);
        try (var serverCache = new DefaultServerCache()) {
            bdb.start();
            cookieStore.start();
            var fetcher = new FetchHTTP2(serverCache, cookieStore);
            fetcher.setUserAgentProvider(new CrawlMetadata());
            fetcher.start();
            try {
                String url = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/";
                var curi = new CrawlURI(UURIFactory.getInstance(url));
                curi.setRecorder(new Recorder(tempDir.toFile(), "temp"));
                fetcher.innerProcess(curi);

                assertEquals(200, curi.getFetchStatus());
                assertEquals(CrawlURI.FetchType.HTTP_GET, curi.getFetchType());
                assertEquals(12, curi.getContentLength());
                assertEquals("text/html; charset=UTF-8", curi.getContentType());
                assertEquals("UTF-8", curi.getRecorder().getCharset().name());
                assertEquals(loopbackAddress.getHostAddress(), curi.getServerIP());
                assertEquals("Hello World!", curi.getRecorder().getContentReplayPrefixString(100));
                assertEquals("foo=bar; Path=/", curi.getHttpResponseHeader("Set-Cookie"));
                assertTrue(curi.getFetchBeginTime() > 1);
                assertTrue(curi.getFetchCompletedTime() >= curi.getFetchBeginTime());
            } finally {
                fetcher.stop();
                server.stop(0);
                cookieStore.stop();
                bdb.stop();
            }
        }
    }
}