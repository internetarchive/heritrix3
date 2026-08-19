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
package org.archive.modules.processor;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.util.JSONUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Detects responses produced by bot-blocking services and adds a "botblock:service" annotation.
 * <p>
 * Normally added to the fetch chain before the extractors.
 */
public class BotBlockDetector extends Processor {
    protected final ConcurrentMap<String, AtomicLong> counts = new ConcurrentHashMap<>();

    public Map<String, AtomicLong> getCounts() {
        return counts;
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return curi.isHttpTransaction() &&
               curi.getFetchStatus() > 0;
    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        String detected = detect(curi);
        if (detected != null &&
            curi.getAnnotations().add("botblock:" + detected)) {
            counts.computeIfAbsent(detected, ignored -> new AtomicLong()).incrementAndGet();
        }
    }

    @Override
    protected JSONObject toCheckpointJson() throws JSONException {
        JSONObject json = super.toCheckpointJson();
        json.put("counts", counts);
        return json;
    }

    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);
        counts.clear();
        JSONObject counts = json.optJSONObject("counts");
        if (counts != null) JSONUtils.putAllAtomicLongs(this.counts, counts);
    }

    @Override
    public String report() {
        return super.report() + "  Blocked requests by service: " +
               new TreeMap<>(counts) + "\n";
    }

    protected static String detect(CrawlURI curi) {
        if (detectAkamai(curi)) return "akamai";
        if (detectAnubis(curi)) return "anubis";
        if (detectCloudflare(curi)) return "cloudflare";
        if (detectDataDome(curi)) return "datadome";
        if (detectIncapsula(curi)) return "incapsula";
        return null;
    }

    private static boolean detectAkamai(CrawlURI curi) {
        return curi.getFetchStatus() == 403 &&
               "AkamaiGHost".equals(curi.getHttpResponseHeader("server"));
    }

    private static boolean detectAnubis(CrawlURI curi) {
        if (curi.getFetchStatus() == 307) {
            String location = curi.getHttpResponseHeader("location");
            return location != null && location.contains("/.within.website/?redir=");
        } else if (curi.getFetchStatus() == 200) {
            String setCookie = curi.getHttpResponseHeader("set-cookie");
            return setCookie != null && setCookie.startsWith("techaro.lol-anubis-") &&
                   bodyContainsHtml(curi, "<script id=\"anubis_challenge\"");
        }
        return false;
    }

    private static boolean detectCloudflare(CrawlURI curi) {
        if ("cloudflare".equalsIgnoreCase(curi.getHttpResponseHeader("server")))  {
            if ("challenge".equalsIgnoreCase(curi.getHttpResponseHeader("cf-mitigated"))) {
                return true;
            }
            return curi.getFetchStatus() == 403 &&
                   bodyContainsHtml(curi, "<h1 data-translate=\"block_headline\">Sorry, you have been blocked</h1>");
        }
        return false;
    }

    private static boolean detectDataDome(CrawlURI curi) {
        return curi.getHttpResponseHeader("x-dd-b") != null &&
               "protected".equalsIgnoreCase(curi.getHttpResponseHeader("x-datadome"));
    }

    private static boolean detectIncapsula(CrawlURI curi) {
        return curi.getFetchStatus() == 404 &&
               curi.getHttpResponseHeader("x-iinfo") != null &&
               bodyContainsHtml(curi, "Incapsula incident ID:");
    }

    private static boolean bodyContainsHtml(CrawlURI curi, String string) {
        return hasHtmlContentType(curi) &&
               curi.getRecorder() != null &&
               curi.getRecorder().getContentReplayPrefixString(8000).contains(string);
    }

    private static boolean hasHtmlContentType(CrawlURI curi) {
        String type = curi.getContentType();
        return type == null ||
               matchesMediaType(type, "text/html") ||
               matchesMediaType(type, "application/xhtml+xml");
    }

    private static boolean matchesMediaType(String value, String expected) {
        if (!value.regionMatches(true, 0, expected, 0, expected.length())) {
            return false;
        }
        int i = expected.length();
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
            i++;
        }
        return i == value.length() || value.charAt(i) == ';';
    }
}