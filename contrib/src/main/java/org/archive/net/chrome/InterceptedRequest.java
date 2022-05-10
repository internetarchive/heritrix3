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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;
import java.util.Collection;
import java.util.Map;

public class InterceptedRequest {
    private final String id;
    private final ChromeRequest request;
    private final ChromeWindow window;
    private boolean handled;

    public InterceptedRequest(ChromeWindow window, String id, ChromeRequest request) {
        this.window = window;
        this.id = id;
        this.request = request;
    }

    public ChromeRequest getRequest() {
        return request;
    }

    public void fulfill(int status, Collection<Map.Entry<String,String>> headers, byte[] body) {
        setHandled();
        JSONArray headerArray = new JSONArray();
        for (Map.Entry<String,String> entry : headers) {
            JSONObject object = new JSONObject();
            object.put("name", entry.getKey());
            object.put("value", entry.getValue());
            headerArray.put(object);
        }
        String encodedBody = Base64.getEncoder().encodeToString(body);
        request.setResponseFulfilledByInterception(true);
        window.call("Fetch.fulfillRequest",
                "requestId", id,
                "responseCode", status,
                "responseHeaders", headerArray,
                "body", encodedBody);
    }

    public void continueNormally() {
        setHandled();
        window.call("Fetch.continueRequest", "requestId", id);
    }

    public boolean isHandled() {
        return handled;
    }

    private void setHandled() {
        if (handled) {
            throw new IllegalStateException("intercepted request already handled");
        }
        handled = true;
    }
}
