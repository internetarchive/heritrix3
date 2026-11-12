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

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * WebDriver BiDi client for a session created through a WebDriver HTTP endpoint, such as a Selenium Grid.
 */
public class RemoteWebDriverBiDi extends AbstractWebDriverBiDi {
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS);
    // Selenium Grid queues new session requests for up to 300 seconds by default.
    // We should outwait it, as a session it creates after we've given up would be orphaned.
    private static final Duration NEW_SESSION_TIMEOUT = Duration.ofMinutes(10);
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    private final String authorization;
    private final URI sessionUrl;

    /**
     * Creates a session and connects to its WebDriver BiDi WebSocket. Deletes the session if connecting fails.
     * Waits up to ten minutes for the session, as a Grid with no free slot queues the request.
     *
     * @param endpoint the WebDriver endpoint, e.g. {@code http://grid:4444}
     * @param username for HTTP Basic authentication, or null for none
     */
    public RemoteWebDriverBiDi(URI endpoint, String username, String password,
                               Session.CapabilitiesRequest capabilities)
            throws IOException, InterruptedException, ExecutionException {
        authorization = username == null ? null : "Basic " + Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        var request = (JSONObject) BiDiJson.toJson(capabilities);
        request.getJSONObject("alwaysMatch").put("webSocketUrl", true);
        JSONObject session = createSession(resolve(endpoint, "session"), new JSONObject().put("capabilities", request));
        sessionUrl = resolve(endpoint, "session/" + session.getString("sessionId"));
        try {
            URI webSocketUrl = webSocketUrl(endpoint,
                    URI.create(session.getJSONObject("capabilities").getString("webSocketUrl")));
            var builder = httpClient.newWebSocketBuilder();
            if (authorization != null) builder.header("Authorization", authorization);
            connect(builder, webSocketUrl);
        } catch (TimeoutException e) {
            deleteSession();
            throw new IOException("Timed out connecting to WebDriver BiDi WebSocket", e);
        } catch (Exception e) {
            deleteSession();
            throw e;
        }
    }

    /**
     * Builds the WebSocket URL from the endpoint's scheme, host and port and the reported URL's path. Selenium Grid
     * reports its own idea of its address, such as a container's internal IP and port, and leaves out the path
     * prefix of any reverse proxy in front of it.
     */
    static URI webSocketUrl(URI endpoint, URI reported) {
        String basePath = endpoint.getRawPath() == null ? "" : endpoint.getRawPath().replaceAll("/+$", "");
        String path = reported.getRawPath();
        if (!path.startsWith(basePath + "/")) path = basePath + path;
        return URI.create(("https".equals(endpoint.getScheme()) ? "wss" : "ws") + "://" + endpoint.getHost()
                + (endpoint.getPort() == -1 ? "" : ":" + endpoint.getPort()) + path);
    }

    private static URI resolve(URI endpoint, String path) {
        String base = endpoint.toString();
        return URI.create(base.endsWith("/") ? base + path : base + "/" + path);
    }

    private JSONObject createSession(URI url, JSONObject body)
            throws IOException, InterruptedException, ExecutionException {
        var builder = HttpRequest.newBuilder(url).timeout(NEW_SESSION_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .header("Content-Type", "application/json; charset=utf-8");
        if (authorization != null) builder.header("Authorization", authorization);
        var future = httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString());
        HttpResponse<String> response;
        try {
            response = future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException cause) throw cause;
            throw e;
        }
        if (response.statusCode() != 200) {
            throw new IOException("POST " + url + " failed with status " + response.statusCode() + ": "
                    + response.body());
        }
        return new JSONObject(response.body()).getJSONObject("value");
    }

    /**
     * Asks the endpoint to delete the session, without waiting for the response so that closing isn't held up by
     * an unreachable endpoint. If the request never arrives, Grid ends the session once it has been idle for its
     * session timeout.
     */
    private void deleteSession() {
        var builder = HttpRequest.newBuilder(sessionUrl).timeout(HTTP_TIMEOUT).DELETE();
        if (authorization != null) builder.header("Authorization", authorization);
        httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.discarding()).whenComplete((response, e) -> {
            if (e != null || response.statusCode() != 200) {
                logger.log(System.Logger.Level.WARNING, "Error deleting WebDriver session " + sessionUrl + ": "
                        + (e != null ? e : "status " + response.statusCode()));
            }
        });
    }

    @Override
    protected void destroy() {
        deleteSession();
    }

    @Override
    public void close() throws IOException {
        deleteSession();
        super.close();
    }
}
