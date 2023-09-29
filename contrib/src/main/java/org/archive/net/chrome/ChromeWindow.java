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

import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.json.JSONObject;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * A browser window or tab.
 */
public class ChromeWindow implements Closeable {
    private static final Logger logger = Logger.getLogger(ChromeWindow.class.getName());

    private final ChromeClient client;
    private final String targetId;
    private final String sessionId;
    private boolean closed;
    private CompletableFuture<Void> loadEventFuture;
    private final Map<String,ChromeRequest> requestMap = new ConcurrentHashMap<>();
    private Consumer<ChromeRequest> requestConsumer;
    private Consumer<InterceptedRequest> requestInterceptor;
    private final ExecutorService eventExecutor;

    public ChromeWindow(ChromeClient client, String targetId) {
        this.client = client;
        this.targetId = targetId;
        this.sessionId = client.call("Target.attachToTarget", "targetId", targetId,
                "flatten", true).getString("sessionId");
        eventExecutor = Executors.newSingleThreadExecutor(runnable ->
                new Thread(runnable, "ChromeWindow (sessionId=" + sessionId +")"));
        client.sessionEventHandlers.put(sessionId, this::handleEvent);
        call("Page.enable"); // for loadEventFired
        call("Page.setLifecycleEventsEnabled", "enabled", true); // for networkidle
        call("Runtime.enable"); // required by Firefox for Runtime.evaluate to work
    }

    /**
     * Call a devtools method in the session of this window.
     */
    public JSONObject call(String method, Object... keysAndValues) {
        return client.callInSession(sessionId, method, keysAndValues);
    }

    /**
     * Evaluate a JavaScript expression.
     */
    public JSONObject eval(String expression) {
        return call("Runtime.evaluate", "expression", expression,
                "returnByValue", true).getJSONObject("result");
    }

    /**
     * Navigate this window to a new URL. Returns a future which will be fulfilled when the page finishes loading.
     */
    public CompletableFuture<Void> navigateAsync(String url) {
        if (loadEventFuture != null) {
            loadEventFuture.cancel(false);
        }
        loadEventFuture = new CompletableFuture<>();
        call("Page.navigate", "url", url);
        return loadEventFuture;
    }

    private void handleEvent(JSONObject message) {
        if (closed) return;
        // Run event handlers on a different thread so we don't block the websocket receiving thread.
        // That would cause a deadlock if an event handler itself made an RPC call as the response could
        // never be processed.
        // We use a single thread per window though as the order events are processed is important.
        eventExecutor.submit(() -> {
            try {
                handleEventOnEventThread(message);
            } catch (Throwable t) {
                logger.log(WARNING, "Exception handling browser event " + message, t);
            }
        });
    }

    private void handleEventOnEventThread(JSONObject message) {
        JSONObject params = message.getJSONObject("params");
        switch (message.getString("method")) {
            case "Fetch.requestPaused":
                handlePausedRequest(params);
                break;
            case "Network.requestWillBeSent":
                handleRequestWillBeSent(params);
                break;
            case "Network.requestWillBeSentExtraInfo":
                handleRequestWillBeSentExtraInfo(params);
                break;
            case "Network.responseReceived":
                handleResponseReceived(params);
                break;
            case "Network.responseReceivedExtraInfo":
                handleResponseReceivedExtraInfo(params);
                break;
            case "Network.loadingFinished":
                handleLoadingFinished(params);
                break;
            case "Page.loadEventFired":
                if (loadEventFuture != null) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    loadEventFuture.complete(null);
                }
                break;
            default:
                logger.log(FINE, "Unhandled event {0}", message);
                break;
        }
    }

    private void handlePausedRequest(JSONObject params) {
        String networkId = params.getString("networkId");
        ChromeRequest request = requestMap.computeIfAbsent(networkId, id -> new ChromeRequest(this, id));
        request.setRequestJson(params.getJSONObject("request"));
        String id = params.getString("requestId");
        InterceptedRequest interceptedRequest = new InterceptedRequest(this, id, request);
        try {
            requestInterceptor.accept(interceptedRequest);
        } catch (Exception e) {
            logger.log(SEVERE, "Request interceptor threw", e);
        }
        if (!interceptedRequest.isHandled()) {
            interceptedRequest.continueNormally();
        }
    }

    private void handleRequestWillBeSent(JSONObject params) {
        String requestId = params.getString("requestId");
        ChromeRequest request = requestMap.computeIfAbsent(requestId, id -> new ChromeRequest(this, id));
        request.setRequestJson(params.getJSONObject("request"));
    }

    private void handleRequestWillBeSentExtraInfo(JSONObject params) {
        // it seems this event can arrive both before and after requestWillBeSent so we need to cope with that
        String requestId = params.getString("requestId");
        ChromeRequest request = requestMap.computeIfAbsent(requestId, id -> new ChromeRequest(this, id));
        if (params.has("headers")) {
            request.setRawRequestHeaders(params.getJSONObject("headers"));
        }
    }

    private void handleResponseReceived(JSONObject params) {
        ChromeRequest request = requestMap.get(params.getString("requestId"));
        if (request == null) {
            logger.log(WARNING, "Got responseReceived event without corresponding requestWillBeSent");
            return;
        }
        request.setResponseJson(params.getJSONObject("response"));
    }

    private void handleResponseReceivedExtraInfo(JSONObject params) {
        ChromeRequest request = requestMap.get(params.getString("requestId"));
        if (request == null) {
            logger.log(WARNING, "Got responseReceivedExtraInfo event without corresponding requestWillBeSent");
            return;
        }
        if (params.has("headers")) {
            request.setRawResponseHeaders(params.getJSONObject("headers"));
        }
        if (params.has("headersText")) {
            request.setResponseHeadersText(params.getString("headersText"));
        }
    }

    private void handleLoadingFinished(JSONObject params) {
        ChromeRequest request = requestMap.get(params.getString("requestId"));
        if (request == null) {
            logger.log(WARNING, "Got loadingFinished event without corresponding requestWillBeSent");
            return;
        }
        if (requestConsumer != null) {
            requestConsumer.accept(request);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        eventExecutor.shutdown();
        try {
            eventExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            client.call("Target.closeTarget", "targetId", targetId);
        } catch (WebsocketNotConnectedException e) {
            // no need to close the window if the browser has already exited
        }
        client.sessionEventHandlers.remove(sessionId);
    }

    public void captureRequests(Consumer<ChromeRequest> requestConsumer) {
        this.requestConsumer = requestConsumer;
        call("Network.enable");
    }

    public void interceptRequests(Consumer<InterceptedRequest> requestInterceptor) {
        this.requestInterceptor = requestInterceptor;
        call("Fetch.enable");
    }
}