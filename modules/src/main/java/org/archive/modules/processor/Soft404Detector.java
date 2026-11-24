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
import java.io.Serializable;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
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
 * {@code /heritrix-soft404-probe-}). If the server responds with a 200 HTML page, later 200 HTML
 * responses from that server with the same content are treated as soft 404s. Parts of a page that
 * typically change from request to request, such as nonces, timestamps and the page's own URL, are
 * ignored when comparing content.
 * <p>
 * What a server returns for missing URLs can change during a crawl, for example once it has set a
 * session cookie. So if a page has the same content as another recent page from the server, the
 * server is probed again, up to {@code maxProbes} probes in all (2 by default). Pages matching any
 * probe fingerprint are treated as soft 404s.
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
 * <li>Each server that returns an HTML page gets one extra request, the probe, or up to
 * {@code maxProbes} with re-probes. Probes are archived like any other request. This includes
 * servers reached only by embeds or redirects, as soft 404s can produce endless URLs through those
 * too.</li>
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

    /** Path requested from every server for the rest of the crawl. Saved in checkpoints. */
    protected String probePath = randomProbePath();

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
     * processor, for soft 404 pages. If disabled, soft 404s are only annotated and counted. The
     * probe itself is always skipped, unless it's a redirect.
     */
    public void setSkipExtraction(boolean skipExtraction) {
        kp.put("skipExtraction", skipExtraction);
    }

    {
        setMaxProbes(2);
    }
    public int getMaxProbes() {
        return (Integer) kp.get("maxProbes");
    }
    /**
     * Maximum number of probes per server.
     */
    public void setMaxProbes(int maxProbes) {
        kp.put("maxProbes", maxProbes);
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
               curi.getFetchStatus() > 0 &&
               (isProbe(curi) || isHtml(curi));
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
        State state = server.getSoft404State();
        server.makeDirty(); // as the state may change below

        if (isProbe(curi)) {
            recordProbeResult(curi, state);
            // A redirected probe must continue so its Location gets extracted. Otherwise there's
            // nothing worth extracting from a made-up URL.
            int status = curi.getFetchStatus();
            return status >= 300 && status < 400 ? ProcessResult.PROCEED : ProcessResult.FINISH;
        }
        if (!state.isProbeScheduled()) {
            scheduleProbe(curi, state, false);
            return ProcessResult.PROCEED;
        }
        if (isExempt(curi) || curi.getFetchStatus() != 200) return ProcessResult.PROCEED;

        String content = readContent(curi);
        long fingerprint = fingerprint(content, echoesOf(curi.getUURI()));
        if (matchesProbe(content, fingerprint, state)) {
            curi.getAnnotations().add(ANNOTATION);
            soft404sDetected.incrementAndGet();
            return getSkipExtraction() ? ProcessResult.FINISH : ProcessResult.PROCEED;
        }
        // If the same page is served for several URLs, probe again in case it's a soft 404 that
        // the earlier probes missed, e.g. because they ran before a session cookie was set.
        if (state.canStartProbe(getMaxProbes()) && state.addRecentPage(fingerprint)) {
            scheduleProbe(curi, state, true);
        }
        return ProcessResult.PROCEED;
    }

    /**
     * Whether the content matches what a probe got, either with its echoes of its own URL removed
     * (giving the given fingerprint) or as is. The latter catches a page reached by a relative
     * link on a copy of itself, where the "echo" is part of the page and so also in the probe's
     * content.
     */
    private static boolean matchesProbe(String content, long fingerprint, State state) {
        if (!state.hasFingerprints()) return false;
        return state.isFingerprint(fingerprint)
                || state.isFingerprint(fingerprint(content, List.of()));
    }

    protected void scheduleProbe(CrawlURI curi, State state, boolean reprobe) {
        int status = curi.getFetchStatus();
        if (status < 200 || status >= 400) return;
        if (!state.startProbe(getMaxProbes())) return;
        try {
            UURI probeUri = UURIFactory.getInstance(curi.getUURI(), probePath);
            CrawlURI probe = curi.createCrawlURI(probeUri, LinkContext.INFERRED_MISC, Hop.INFERRED);
            probe.setSchedulingDirective(SchedulingConstants.HIGH);
            // re-probes reuse the probe URL, so must bypass the already-seen check
            probe.setForceFetch(reprobe);
            curi.getOutLinks().add(probe);
            probesScheduled.incrementAndGet();
        } catch (URIException e) {
            logger.log(Level.WARNING, "Failed to create soft 404 probe for " + curi, e);
        }
    }

    protected void recordProbeResult(CrawlURI curi, State state) {
        curi.getAnnotations().add(PROBE_ANNOTATION);
        state.finishProbe();
        if (curi.getFetchStatus() != 200 || curi.getContentLength() <= 0 || !isHtml(curi)) return;
        if (!state.addFingerprint(fingerprint(curi))) return;
        serversWithSoft404s.incrementAndGet();
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Soft 404s detected by " + curi);
        }
    }

    protected boolean isProbe(CrawlURI curi) {
        // endsWith so a redirect that keeps the path (e.g. adding a locale prefix or trailing
        // slash) counts as the probe. Redirects that discard the path don't need following up, as
        // they can't produce an endless series of URLs.
        String path = curi.getUURI().getEscapedPath();
        return path != null && (path.endsWith(probePath) || path.endsWith(probePath + "/"));
    }

    private static boolean isHtml(CrawlURI curi) {
        return curi.hasContentType("text/html") || curi.hasContentType("application/xhtml+xml");
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
        return fingerprint(readContent(curi), echoesOf(curi.getUURI()));
    }

    private static long fingerprint(String content, List<String> echoes) {
        return Hashing.murmur3_128().hashString(normalize(content, echoes), UTF_8).asLong();
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
            for (String path : new String[]{uuri.getEscapedPathQuery(), uuri.getPathQuery(),
                    uuri.getEscapedPath(), uuri.getPath()}) {
                if (path == null) continue;
                // also without a trailing slash, e.g. in a canonical link
                for (String form : List.of(path, StringUtils.removeEnd(path, "/"))) {
                    // percent-encoded, e.g. in a return URL parameter
                    String encoded = URLEncoder.encode(form, UTF_8);
                    if (encoded.length() >= MIN_ECHO_LENGTH) forms.add(encoded);
                    if (form.startsWith("/")) form = form.substring(1);
                    if (form.length() < MIN_ECHO_LENGTH) continue;
                    forms.add(form);
                    forms.add(form.replace("/", "\\/")); // JSON
                }
            }
        } catch (URIException e) {
            // ignore; use whatever forms we have
        }
        List<String> echoes = new ArrayList<>(forms);
        echoes.sort(Comparator.comparingInt(String::length).reversed());
        return echoes;
    }

    /**
     * Removes the given echoes, replaces each token (a run of ASCII letters, digits and
     * {@code ._~+/%-}) containing a digit with "0", and collapses whitespace.
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
            } else if (!isTokenChar(c)) {
                sb.append(c);
                i++;
            } else {
                int start = i;
                boolean hasDigit = false;
                while (i < n && isTokenChar(content.charAt(i))) {
                    if (content.charAt(i) <= '9' && content.charAt(i) >= '0') hasDigit = true;
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

    private static boolean isTokenChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "._~+/%-".indexOf(c) >= 0;
    }

    public String getProbePath() {
        return probePath;
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
        json.put("probePath", probePath);
        return json;
    }

    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);
        probesScheduled.set(json.optLong("probesScheduled"));
        serversWithSoft404s.set(json.optLong("serversWithSoft404s"));
        soft404sDetected.set(json.optLong("soft404sDetected"));
        probePath = json.optString("probePath", probePath);
    }

    @Override
    public String report() {
        return super.report() +
               "  Probes scheduled: " + probesScheduled.get() + "\n" +
               "  Servers with soft 404s: " + serversWithSoft404s.get() + "\n" +
               "  Soft 404s detected: " + soft404sDetected.get() + "\n";
    }

    /**
     * Soft 404 detection state of one server, kept in its {@link CrawlServer}.
     */
    public static class State implements Serializable {
        private static final long serialVersionUID = 1L;

        /** Number of recent page fingerprints kept to spot repeated pages. */
        static final int RECENT_PAGES = 8;

        private int probeCount;
        private boolean probePending;
        /** Fingerprints of soft 404 pages that probes found, or null if none did. */
        private long[] fingerprints;
        /** Fingerprints of recent pages, oldest first, or null if none are being kept. */
        private long[] recentPages;

        /**
         * Marks a probe as scheduled, unless one is pending or maxProbes have already been
         * scheduled.
         * @return true if the caller should schedule a probe
         */
        public synchronized boolean startProbe(int maxProbes) {
            if (!canStartProbe(maxProbes)) return false;
            probeCount++;
            probePending = true;
            recentPages = null;
            return true;
        }

        public synchronized boolean canStartProbe(int maxProbes) {
            return !probePending && probeCount < maxProbes;
        }

        /**
         * Marks the pending probe as done, or records a probe of this server that was scheduled
         * elsewhere, e.g. one redirected from another server.
         */
        public synchronized void finishProbe() {
            probeCount = Math.max(probeCount, 1);
            probePending = false;
        }

        public synchronized boolean isProbeScheduled() {
            return probeCount > 0;
        }

        public synchronized boolean hasFingerprints() {
            return fingerprints != null;
        }

        /**
         * Whether a probe got a page with this fingerprint with a success status for a
         * nonexistent URL.
         */
        public synchronized boolean isFingerprint(long fingerprint) {
            return fingerprints != null && contains(fingerprints, fingerprint);
        }

        /**
         * Records the fingerprint of a page a probe got with a success status for a nonexistent
         * URL.
         * @return true if it's the first one recorded
         */
        public synchronized boolean addFingerprint(long fingerprint) {
            if (isFingerprint(fingerprint)) return false;
            long[] old = fingerprints == null ? new long[0] : fingerprints;
            fingerprints = Arrays.copyOf(old, old.length + 1);
            fingerprints[old.length] = fingerprint;
            return old.length == 0;
        }

        /**
         * Records the fingerprint of a page, keeping only the most recent RECENT_PAGES.
         * @return true if one of the kept pages already had this fingerprint
         */
        public synchronized boolean addRecentPage(long fingerprint) {
            long[] pages = recentPages == null ? new long[0] : recentPages;
            if (contains(pages, fingerprint)) return true;
            int keep = Math.min(pages.length, RECENT_PAGES - 1);
            recentPages = Arrays.copyOfRange(pages, pages.length - keep, pages.length + 1);
            recentPages[keep] = fingerprint;
            return false;
        }

        private static boolean contains(long[] values, long value) {
            for (long v : values) {
                if (v == value) return true;
            }
            return false;
        }
    }
}
