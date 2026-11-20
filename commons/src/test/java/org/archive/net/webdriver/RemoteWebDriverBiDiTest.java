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

package org.archive.net.webdriver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

class RemoteWebDriverBiDiTest {
    private static final String AUTHORIZATION = "Basic dXNlcjpzZWNyZXQ="; // user:secret
    private static final Session.CapabilitiesRequest CAPABILITIES = new Session.CapabilitiesRequest(
            Map.of("goog:chromeOptions", Map.of("args", List.of("headless=new"))), null);
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private HttpServer endpoint;

    @Test
    void sessionNotCreated() throws IOException {
        startEndpoint(500, "{\"value\":{\"error\":\"session not created\",\"message\":\"no free slots\"}}");
        var e = assertThrows(IOException.class, () -> new RemoteWebDriverBiDi(endpointUri(), "user", "secret",
                CAPABILITIES));
        assertTrue(e.getMessage().contains("no free slots"), e.getMessage());
        assertEquals(List.of("POST /session authorized"), requests);
    }

    @Test
    void deletesSessionIfWebSocketFails() throws Exception {
        startEndpoint(200, "{\"value\":{\"sessionId\":\"abc\",\"capabilities\":{" +
                "\"webSocketUrl\":\"ws://10.255.255.1:4444/session/abc/se/bidi\"}}}");
        assertThrows(Exception.class, () -> new RemoteWebDriverBiDi(endpointUri(), "user", "secret", CAPABILITIES));
        for (int i = 0; i < 50 && requests.size() < 3; i++) Thread.sleep(100);
        assertEquals(List.of("POST /session authorized", "GET /session/abc/se/bidi authorized",
                "DELETE /session/abc authorized"), requests);
    }

    @Test
    void webSocketUrl() {
        URI reported = URI.create("ws://10.88.0.47:4444/session/abc/se/bidi");
        assertEquals(URI.create("ws://localhost:4455/session/abc/se/bidi"),
                RemoteWebDriverBiDi.webSocketUrl(URI.create("http://localhost:4455"), reported));
        assertEquals(URI.create("wss://grid.example.org/wd/hub/session/abc/se/bidi"),
                RemoteWebDriverBiDi.webSocketUrl(URI.create("https://grid.example.org/wd/hub/"), reported));
        assertEquals(URI.create("ws://[::1]:4444/wd/hub/session/abc/se/bidi"),
                RemoteWebDriverBiDi.webSocketUrl(URI.create("http://[::1]:4444/wd/hub"),
                        URI.create("ws://10.88.0.47:4444/wd/hub/session/abc/se/bidi")));
    }

    /**
     * Answers every request under /session with the given response, recording whether it had the password.
     */
    private void startEndpoint(int status, String body) throws IOException {
        endpoint = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        endpoint.createContext("/session", exchange -> {
            boolean authorized = AUTHORIZATION.equals(exchange.getRequestHeaders().getFirst("Authorization"));
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() +
                    (authorized ? " authorized" : " unauthorized"));
            byte[] bytes = body.getBytes(UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(exchange.getRequestMethod().equals("POST") ? status : 404, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        endpoint.start();
    }

    private URI endpointUri() {
        return URI.create("http://127.0.0.1:" + endpoint.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        if (endpoint != null) endpoint.stop(0);
    }
}
