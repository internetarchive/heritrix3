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

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class ChromeRequest {
    private final ChromeWindow window;
    private final String id;
    private JSONObject requestJson;
    private JSONObject rawRequestHeaders;
    private JSONObject responseJson;
    private JSONObject rawResponseHeaders;
    private String responseHeadersText;
    private final long beginTime = System.currentTimeMillis();
    private boolean responseFulfilledByInterception;

    public ChromeRequest(ChromeWindow window, String id) {
        this.window = window;
        this.id = id;
    }

    void setRawRequestHeaders(JSONObject rawRequestHeaders) {
        this.rawRequestHeaders = rawRequestHeaders;
    }

    void setResponseJson(JSONObject responseJson) {
        this.responseJson = responseJson;
    }

    void setRawResponseHeaders(JSONObject headers) {
        this.rawResponseHeaders = headers;
    }

    void setResponseHeadersText(String responseHeadersText) {
        this.responseHeadersText = responseHeadersText;
    }

    public String getUrl() {
        return requestJson.getString("url");
    }

    public byte[] getResponseBody() {
        JSONObject reply = window.call("Network.getResponseBody", "requestId", id);
        byte[] body;
        if (reply.getBoolean("base64Encoded")) {
            body = Base64.getDecoder().decode(reply.getString("body"));
        } else {
            body = reply.getString("body").getBytes(StandardCharsets.UTF_8);
        }
        return body;
    }

    public String getRequestHeader() {
        if (responseJson != null && responseJson.has("requestHeadersText")) {
            return responseJson.getString("requestHeadersText");
        }
        StringBuilder builder = new StringBuilder();
        builder.append(requestJson.getString("method"));
        builder.append(' ');
        builder.append(getUrl());
        builder.append(" HTTP/1.1\r\n");
        formatHeaders(builder, requestJson.getJSONObject("headers"), rawRequestHeaders);
        return builder.toString();
    }

    public byte[] getRequestBody() {
        if (requestJson.has("postData")) {
            return requestJson.getString("postData").getBytes(StandardCharsets.UTF_8);
        } else {
            return new byte[0];
        }
    }

    public String getResponseHeader() {
        if (responseHeadersText != null) {
            return responseHeadersText;
        } else if (responseJson.has("headersText")) {
            return responseJson.getString("headersText");
        }
        StringBuilder builder = new StringBuilder();
        if (responseJson.getString("protocol").equals("http/1.0")) {
            builder.append("HTTP/1.0");
        } else {
            builder.append("HTTP/1.1");
        }
        builder.append(getStatus());
        builder.append(" ");
        builder.append(responseJson.getString("statusText"));
        builder.append("\r\n");
        formatHeaders(builder, responseJson.getJSONObject("headers"), rawResponseHeaders);
        return builder.toString();
    }

    private void formatHeaders(StringBuilder builder, JSONObject headers, JSONObject rawHeaders) {
        if (rawHeaders != null) {
            headers = rawHeaders;
        }
        for (Object key : headers.keySet()) {
            builder.append(key);
            builder.append(": ");
            builder.append(headers.getString((String) key));
            builder.append("\r\n");
        }
        builder.append("\r\n");
    }

    public String getMethod() {
        return requestJson.getString("method");
    }

    public int getStatus() {
        return responseJson.getInt("status");
    }

    public String getResponseContentType() {
        return responseJson.getString("mimeType");
    }

    public long getBeginTime() {
        return beginTime;
    }

    public String getRemoteIPAddress() {
        if (responseJson.has("remoteIPAddress")) {
            return responseJson.getString("remoteIPAddress");
        } else {
            return null;
        }
    }

    void setRequestJson(JSONObject requestJson) {
        this.requestJson = requestJson;
    }

    void setResponseFulfilledByInterception(boolean responseFulfilledByInterception) {
        this.responseFulfilledByInterception = responseFulfilledByInterception;
    }

    public boolean isResponseFulfilledByInterception() {
        return responseFulfilledByInterception;
    }
}
