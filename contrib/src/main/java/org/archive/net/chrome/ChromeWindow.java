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

import org.json.JSONObject;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static java.util.logging.Level.FINE;

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

    public ChromeWindow(ChromeClient client, String targetId) {
        this.client = client;
        this.targetId = targetId;
        this.sessionId = client.call("Target.attachToTarget", "targetId", targetId,
                "flatten", true).getString("sessionId");
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
        switch (message.getString("method")) {
            case "Page.loadEventFired":
                if (loadEventFuture != null) {
                    loadEventFuture.complete(null);
                }
                break;
            default:
                logger.log(FINE, "Unhandled event {0}", message);
                break;
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        client.call("Target.closeTarget", "targetId", targetId);
        client.sessionEventHandlers.remove(sessionId);
    }
}