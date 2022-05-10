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

package org.archive.net.chrome;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

import java.io.Closeable;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * A client for the <a href="https://chromedevtools.github.io/devtools-protocol/">Chrome Devtools Protocol</a>.
 */
public class ChromeClient implements Closeable {
    private static final Logger logger = Logger.getLogger(ChromeClient.class.getName());
    private static final int RPC_TIMEOUT_SECONDS = 60;

    private final DevtoolsSocket devtoolsSocket;
    private final AtomicLong nextMessageId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JSONObject>> responseFutures = new ConcurrentHashMap<>();
    final ConcurrentHashMap<String, Consumer<JSONObject>> sessionEventHandlers = new ConcurrentHashMap<>();

    public ChromeClient(String devtoolsUrl) {
        devtoolsSocket = new DevtoolsSocket(URI.create(devtoolsUrl));
        try {
            devtoolsSocket.connectBlocking();
        } catch (InterruptedException e) {
            throw new ChromeException("Interrupted while connecting", e);
        }
    }

    public JSONObject call(String method, Object... keysAndValues) {
        return callInSession(null, method, keysAndValues);
    }

    public JSONObject callInSession(String sessionId, String method, Object... keysAndValues) {
        JSONObject params = new JSONObject();
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("keysAndValues.length must even");
        }
        for (int i = 0; i < keysAndValues.length; i += 2) {
            params.put((String)keysAndValues[i], keysAndValues[i + 1]);
        }
        return callInternal(sessionId, method, params);
    }

    private JSONObject callInternal(String sessionId, String method, JSONObject params) {
        long id = nextMessageId.getAndIncrement();
        JSONObject message = new JSONObject();
        message.put("id", id);
        if (sessionId != null) {
            message.put("sessionId", sessionId);
        }
        message.put("method", method);
        message.put("params", params);
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        responseFutures.put(id, future);
        devtoolsSocket.send(message.toString());
        try {
            return future.get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new ChromeException("Call interrupted", e);
        } catch (TimeoutException e) {
            throw new ChromeException("Call timed out: " + message, e);
        } catch (ExecutionException e) {
            throw new ChromeException("Call failed: " + message + ": " + e.getMessage(), e.getCause());
        }
    }

    private void handleResponse(JSONObject message) {
        long id = message.getLong("id");
        CompletableFuture<JSONObject> future = responseFutures.remove(id);
        if (future == null) {
            logger.log(WARNING, "Unexpected RPC response id {0}", id);
        } else if (message.has("error")) {
            future.completeExceptionally(new ChromeException(message.getJSONObject("error").getString("message")));
        } else {
            future.complete(message.getJSONObject("result"));
        }
    }

    private void handleEvent(JSONObject message) {
        if (message.has("sessionId")) {
            String sessionId = message.getString("sessionId");
            Consumer<JSONObject> handler = sessionEventHandlers.get(sessionId);
            if (handler != null) {
                handler.accept(message);
            } else {
                logger.log(WARNING, "Received event for unknown session {0}", sessionId);
            }
        }
    }

    public ChromeWindow createWindow(int width, int height) {
        String targetId = call("Target.createTarget", "url", "about:blank",
                "width", width, "height", height).getString("targetId");
        return new ChromeWindow(this, targetId);
    }

    @Override
    public void close() {
        devtoolsSocket.close();
    }

    private class DevtoolsSocket extends WebSocketClient {
        public DevtoolsSocket(URI uri) {
            super(uri);
            setConnectionLostTimeout(-1); // disable pings - Chromium doesn't support them
        }

        @Override
        public void onOpen(ServerHandshake serverHandshake) {

        }

        @Override
        public void onMessage(String messageString) {
            try {
                JSONObject message = new JSONObject(messageString);
                if (message.has("method")) {
                    handleEvent(message);
                } else {
                    handleResponse(message);
                }
            } catch (Throwable e) {
                logger.log(WARNING, "Exception handling message from Chromium", e);
                throw e;
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            if (!remote) return;
            logger.log(WARNING, "Websocket closed by browser: " + reason);
        }

        @Override
        public void onError(Exception e) {
            logger.log(Level.SEVERE, "Websocket error", e);
        }
    }
}
