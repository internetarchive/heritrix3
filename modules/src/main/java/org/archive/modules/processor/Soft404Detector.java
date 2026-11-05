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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.url.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.hash.Hashing;

/**
 * Detects "soft 404s": pages that a site serves with a 200 (OK) status for URLs that don't exist.
 * On some sites, relative links on these pages lead to an endless series of fake URLs that all
 * return the same page.
 * <p>
 * For each server, the detector requests one made-up URL (starting with
 * {@code /heritrix-soft404-probe-}). If the server responds with 200, later 200 responses from that
 * server with the same content are treated as soft 404s. Parts of a page that typically change
 * from request to request, such as nonces, timestamps and the page's own URL, are ignored when
 * comparing content.
 * <p>
 * Soft 404s are annotated "soft404" in the crawl log, and the probe "soft404:probe". If
 * {@code skipExtraction} is enabled (the default) the rest of the fetch chain is skipped for
 * them, so neither the link extractors nor the browser processor see them, which stops the series
 * of fake URLs. Either way they're still fetched and archived, and their status codes are
 * unchanged. The processor report shows how many servers were probed, how many serve soft 404s
 * and how many soft 404s were detected.
 * <p>
 * Things to be aware of:
 * <ul>
 * <li>Each server gets one extra request, the probe, which is archived like any other.</li>
 * <li>Pages fetched before a server's probe completes aren't checked.</li>
 * <li>Seeds and the root URL ("/") are never treated as soft 404s, since many sites serve their
 * home page for missing URLs.</li>
 * <li>Sites whose missing-page content varies between requests (for example with A/B test
 * settings) aren't detected.</li>
 * <li>If a site serves the same bot-challenge page for every URL, every page will be detected as a
 * soft 404.</li>
 * </ul>
 * <p>
 * Add it to the fetch chain after fetchHttp and before the extractors. Any processors after it in
 * the fetch chain are skipped for soft 404s when skipExtraction is enabled.
 */
public class Soft404Detector extends Processor {
    private static final Logger logger = Logger.getLogger(Soft404Detector.class.getName());

    public static final String PROBE_PATH_PREFIX = "/heritrix-soft404-probe-";
    public static final String ANNOTATION = "soft404";
    public static final String PROBE_ANNOTATION = "soft404:probe";

    /** Maximum number of characters of content considered when fingerprinting. */
    static final int MAX_CONTENT_CHARS = 1024 * 1024;

    /** Minimum length of a path for its echoes in the content to be removed. */
    private static final int MIN_ECHO_LENGTH = 4;

    protected final AtomicLong probesScheduled = new AtomicLong();
    protected final AtomicLong serversWithSoft404s = new AtomicLong();
    protected final AtomicLong soft404sDetected = new AtomicLong();

    {
        setSkipExtraction(true);
    }
    public boolean getSkipExtraction() {
        return (Boolean) kp.get("skipExtraction");
    }
    /**
     * Whether to skip the rest of the fetch chain, including link extraction and the browser
     * processor, for soft 404 pages (including the probe). If disabled, soft 404s are only
     * annotated and counted.
     */
    public void setSkipExtraction(boolean skipExtraction) {
        kp.put("skipExtraction", skipExtraction);
    }

    protected ServerCache serverCache;

    public ServerCache getServerCache() {
        return serverCache;
    }

    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return curi.isHttpTransaction() &&
               curi.getFetchStatus() > 0;
    }

    @Override
    protected void innerProcess(CrawlURI curi) {
        throw new AssertionError("innerProcessResult is overridden");
    }

    /**
     * Returns FINISH for soft 404s if skipExtraction is enabled, ending the fetch chain so
     * that neither the extractors nor the browser processor see them. Unlike marking the URI as
     * link extracted, this also works when extractIndependently is enabled.
     */
    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) throws InterruptedException {
        CrawlServer server = serverCache.getServerFor(curi.getUURI());
        if (server == null) return ProcessResult.PROCEED;

        boolean soft404;
        if (isProbe(curi)) {
            recordProbeResult(curi, server);
            // a redirected probe must continue so its Location gets extracted
            soft404 = curi.getFetchStatus() == 200;
        } else if (!server.hasSoft404Fingerprint()) {
            scheduleProbe(curi, server);
            soft404 = false;
        } else {
            soft404 = !isExempt(curi) && matchesProbe(curi, server);
            if (soft404) {
                curi.getAnnotations().add(ANNOTATION);
                soft404sDetected.incrementAndGet();
            }
        }
        return soft404 && getSkipExtraction() ? ProcessResult.FINISH : ProcessResult.PROCEED;
    }

    protected void scheduleProbe(CrawlURI curi, CrawlServer server) {
        // Outlinks of error responses are discarded by default, so wait for a successful response
        // (e.g. robots.txt is often a 404) before scheduling the probe.
        int status = curi.getFetchStatus();
        if (status < 200 || status >= 400) return;
        if (!server.startSoft404Probe()) return;
        server.makeDirty();
        try {
            UURI probeUri = UURIFactory.getInstance(curi.getUURI(), randomProbePath());
            CrawlURI probe = curi.createCrawlURI(probeUri, LinkContext.PREREQ_MISC, Hop.PREREQ);
            probe.setSchedulingDirective(SchedulingConstants.HIGH);
            curi.getOutLinks().add(probe);
            probesScheduled.incrementAndGet();
        } catch (URIException e) {
            logger.log(Level.WARNING, "Failed to create soft 404 probe for " + curi, e);
        }
    }

    protected void recordProbeResult(CrawlURI curi, CrawlServer server) {
        curi.getAnnotations().add(PROBE_ANNOTATION);
        if (curi.getFetchStatus() == 200 && curi.getContentLength() > 0) {
            server.setSoft404Fingerprint(fingerprint(curi), curi.getContentLength(),
                    mediaType(curi.getContentType()));
            server.makeDirty();
            serversWithSoft404s.incrementAndGet();
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Soft 404s detected on " + server);
            }
        }
    }

    protected boolean matchesProbe(CrawlURI curi, CrawlServer server) {
        if (curi.getFetchStatus() != 200) return false;
        if (!mediaType(curi.getContentType()).equals(server.getSoft404ContentType())) return false;
        long probeLength = server.getSoft404ContentLength();
        long tolerance = Math.max(1024, probeLength / 10);
        if (Math.abs(curi.getContentLength() - probeLength) > tolerance) return false;
        return fingerprint(curi) == server.getSoft404Fingerprint();
    }

    protected static boolean isProbe(CrawlURI curi) {
        // contains rather than startsWith so a redirect that keeps the path (e.g. adding a locale
        // prefix or trailing slash) counts as the probe. Redirects that discard the path don't
        // need following up, as they can't produce an endless series of URLs.
        String path = curi.getUURI().getEscapedPath();
        return path != null && path.contains(PROBE_PATH_PREFIX);
    }

    protected static boolean isExempt(CrawlURI curi) {
        if (curi.isSeed()) return true;
        String path = curi.getUURI().getEscapedPath();
        return path == null || path.isEmpty() || path.equals("/");
    }

    /**
     * Two segments deep, as only soft 404s at that depth or deeper can generate endless URLs:
     * relative links on a single-segment page always resolve against the root, so they yield the
     * same URLs every time. Some sites serve soft 404s only at one depth or the other.
     */
    protected static String randomProbePath() {
        return PROBE_PATH_PREFIX + randomToken() + "/" + randomToken();
    }

    private static String randomToken() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(Character.forDigit(random.nextInt(36), 36));
        }
        return sb.toString();
    }

    static long fingerprint(CrawlURI curi) {
        String normalized = normalize(readContent(curi), echoesOf(curi.getUURI()));
        return Hashing.murmur3_128().hashString(normalized, UTF_8).asLong();
    }

    /**
     * Reads up to MAX_CONTENT_CHARS of decoded content.
     */
    static String readContent(CrawlURI curi) {
        Recorder recorder = curi.getRecorder();
        if (recorder == null) return "";
        try {
            CharSequence content = recorder.getContentReplayCharSequence();
            return content.subSequence(0, Math.min(content.length(), MAX_CONTENT_CHARS)).toString();
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to read content of " + curi, e);
            return "";
        }
    }

    /**
     * Forms in which a page might echo its own path, longest first.
     */
    static List<String> echoesOf(UURI uuri) {
        Set<String> forms = new LinkedHashSet<>();
        try {
            for (String form : new String[]{uuri.getEscapedPathQuery(), uuri.getPathQuery(),
                    uuri.getEscapedPath(), uuri.getPath()}) {
                if (form == null) continue;
                // percent-encoded, e.g. in a return URL parameter
                String encoded = URLEncoder.encode(form, UTF_8);
                if (encoded.length() >= MIN_ECHO_LENGTH) forms.add(encoded);
                if (form.startsWith("/")) form = form.substring(1);
                if (form.length() < MIN_ECHO_LENGTH) continue;
                forms.add(form);
                forms.add(form.replace("/", "\\/")); // JSON
            }
        } catch (URIException e) {
            // ignore; use whatever forms we have
        }
        List<String> echoes = new ArrayList<>(forms);
        echoes.sort(Comparator.comparingInt(String::length).reversed());
        return echoes;
    }

    /**
     * Removes the given echoes, replaces each token (a run of characters other than whitespace,
     * quotes and angle brackets) containing a digit with "0", and collapses whitespace.
     */
    static String normalize(String content, List<String> echoes) {
        for (String echo : echoes) {
            content = content.replace(echo, "");
        }
        StringBuilder sb = new StringBuilder(content.length());
        int i = 0;
        int n = content.length();
        while (i < n) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                while (i < n && Character.isWhitespace(content.charAt(i))) i++;
                sb.append(' ');
            } else if (isTokenDelimiter(c)) {
                sb.append(c);
                i++;
            } else {
                int start = i;
                boolean hasDigit = false;
                while (i < n && !Character.isWhitespace(content.charAt(i))
                        && !isTokenDelimiter(content.charAt(i))) {
                    if (Character.isDigit(content.charAt(i))) hasDigit = true;
                    i++;
                }
                if (hasDigit) {
                    sb.append('0');
                } else {
                    sb.append(content, start, i);
                }
            }
        }
        return sb.toString();
    }

    private static boolean isTokenDelimiter(char c) {
        return c == '"' || c == '\'' || c == '<' || c == '>';
    }

    private static String mediaType(String contentType) {
        if (contentType == null) return "";
        int semicolon = contentType.indexOf(';');
        if (semicolon >= 0) contentType = contentType.substring(0, semicolon);
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    public long getProbesScheduled() {
        return probesScheduled.get();
    }

    public long getServersWithSoft404s() {
        return serversWithSoft404s.get();
    }

    public long getSoft404sDetected() {
        return soft404sDetected.get();
    }

    @Override
    protected JSONObject toCheckpointJson() throws JSONException {
        JSONObject json = super.toCheckpointJson();
        json.put("probesScheduled", probesScheduled.get());
        json.put("serversWithSoft404s", serversWithSoft404s.get());
        json.put("soft404sDetected", soft404sDetected.get());
        return json;
    }

    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);
        probesScheduled.set(json.optLong("probesScheduled"));
        serversWithSoft404s.set(json.optLong("serversWithSoft404s"));
        soft404sDetected.set(json.optLong("soft404sDetected"));
    }

    @Override
    public String report() {
        return super.report() +
               "  Probes scheduled: " + probesScheduled.get() + "\n" +
               "  Servers with soft 404s: " + serversWithSoft404s.get() + "\n" +
               "  Soft 404s detected: " + soft404sDetected.get() + "\n";
    }
}
