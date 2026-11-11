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

package org.archive.crawler.processor;

import org.apache.commons.lang3.StringUtils;
import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Frontier;
import org.archive.io.RecorderLengthExceededException;
import org.archive.io.RecorderTimeoutException;
import org.archive.io.RecordingInputStream;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorChain;
import org.archive.modules.behaviors.Behavior;
import org.archive.modules.behaviors.ExtractLinksBehavior;
import org.archive.modules.behaviors.Page;
import org.archive.modules.behaviors.ScrollDownBehavior;
import org.archive.modules.extractor.Extractor;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.extractor.UriErrorLoggerModule;
import org.archive.modules.fetcher.FetchHTTP2;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.net.MitmProxy;
import org.archive.net.webdriver.*;
import org.archive.spring.KeyedProperties;
import org.archive.util.IdleBarrier;
import org.archive.util.Recorder;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.client.Result;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.ERROR;
import static java.lang.System.Logger.Level.WARNING;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.FAILED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.SUCCEEDED;
import static org.archive.modules.CoreAttributeConstants.A_HTTP_RESPONSE_HEADERS;
import static org.archive.modules.fetcher.FetchErrors.LENGTH_TRUNC;
import static org.archive.modules.fetcher.FetchErrors.TIMER_TRUNC;

/**
 * Opens a web page in a local web browser via WebDriver BiDi and runs {@link Behavior}s to interact with the page.
 * Subresources loaded by the browser are recorded using a HTTP proxy. Must be used in conjunction with
 * {@link FetchHTTP2}. Normally defined in the FetchChain after the link extractors.
 */
public class BrowserProcessor extends Processor {
    private static final System.Logger logger = System.getLogger(BrowserProcessor.class.getName());
    protected static final String PAGE_ID_HEADER = "Heritrix-Request-ID";
    protected final CrawlController crawlController;
    protected MitmProxy proxy;
    protected final FetchHTTP2 fetcher;
    protected final List<Browser> pool = new CopyOnWriteArrayList<>();
    private final Object tabsLock = new Object();
    protected final ApplicationEventPublisher eventPublisher;
    protected final Map<String, BrowserPage> pages = new ConcurrentHashMap<>();
    protected final Map<BrowsingContext.Context, String> pageIdsByContext = new ConcurrentHashMap<>();
    protected final ProcessorChain extractorChain = new ProcessorChain();
    protected final AtomicLong subresourcesRecorded = new AtomicLong();
    protected List<Behavior> behaviors;
    protected String executable;
    protected List<String> options = List.of("--headless");
    protected int concurrency = 20;
    protected List<String> browsers = List.of("local");

    public BrowserProcessor(FetchHTTP2 fetcher, CrawlController crawlController, ApplicationEventPublisher eventPublisher,
                            UriErrorLoggerModule uriErrorLoggerModule) {
        this.crawlController = crawlController;
        this.fetcher = fetcher;
        this.eventPublisher = eventPublisher;
        this.behaviors = List.of(new ScrollDownBehavior(), new ExtractLinksBehavior(uriErrorLoggerModule));
    }

    public synchronized void stop() {
        if (!isRunning) return;
        super.stop();
        if (proxy != null) {
            try {
                proxy.stop();
            } catch (Exception e) {
                logger.log(ERROR, "Error stopping proxy server", e);
            }
        }
        synchronized (tabsLock) {
            tabsLock.notifyAll();
        }
        for (Browser browser : pool) {
            browser.close();
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        if (curi.getFetchStatus() != 200) return false;
        String scheme = curi.getUURI().getScheme();
        if (!scheme.equals("https") && !scheme.equals("http")) return false;
        if (!curi.hasContentType("text/html")) return false;
        if (StringUtils.startsWithIgnoreCase(curi.getHttpResponseHeader("Content-Disposition"), "attachment")) {
            return false;
        }
        return true;
    }

    @Override
    public void innerProcess(CrawlURI curi) {
        try {
            for (int attempt = 0; ; attempt++) {
                Browser browser = acquireTabSlot(curi.getClassKey());
                if (browser == null) return; // stopping
                LocalWebDriverBiDi driver = browser.driver;
                try {
                    visit(curi, driver);
                    return;
                } catch (WebDriverException e) {
                    if (attempt == 0 && !driver.isAlive()) {
                        logger.log(WARNING, "Browser died while visiting " + curi + ", restarting it", e);
                        if (browser.restart(driver)) continue; // retry once with the new browser
                    }
                    logger.log(WARNING, "WebDriver exception visiting " + curi, e);
                    return;
                } finally {
                    releaseTabSlot(browser);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Waits for a browser with a free tab slot, preferring the one the queue key hashes to so that a site
     * keeps the same browser's cookies and cache.
     *
     * @return the browser, or null if the processor is stopping
     */
    private Browser acquireTabSlot(String queueKey) throws InterruptedException {
        synchronized (tabsLock) {
            while (isRunning) {
                Browser browser = pool.get(Math.floorMod(Objects.hashCode(queueKey), pool.size()));
                if (browser.openTabs >= concurrency) {
                    browser = pool.stream().filter(b -> b.openTabs < concurrency)
                            .min(Comparator.comparingInt(b -> b.openTabs)).orElse(null);
                }
                if (browser != null) {
                    browser.openTabs++;
                    browser.pagesVisited++;
                    return browser;
                }
                tabsLock.wait();
            }
            return null;
        }
    }

    private void releaseTabSlot(Browser browser) {
        synchronized (tabsLock) {
            browser.openTabs--;
            tabsLock.notifyAll();
        }
    }

    @Override
    protected JSONObject toCheckpointJson() throws JSONException {
        return super.toCheckpointJson().put("subresourcesRecorded", subresourcesRecorded.get());
    }

    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);
        subresourcesRecorded.set(json.getLong("subresourcesRecorded"));
    }

    @Override
    public String report() {
        StringBuilder builder = new StringBuilder();
        builder.append(super.report());
        builder.append("  Pages visited: ").append(getURICount()).append("\n");
        builder.append("  Subresources recorded: ").append(subresourcesRecorded.get()).append("\n");
        synchronized (tabsLock) {
            for (Browser browser : pool) {
                builder.append("  Browser ").append(browser.id).append(": ")
                        .append(browser.openTabs).append(" open tabs, ")
                        .append(browser.pagesVisited).append(" pages visited, ")
                        .append(browser.restarts).append(" restarts\n");
            }
        }
        for (var behavior : behaviors) {
            builder.append(behavior.report());
        }
        return builder.toString();
    }

    private void visit(CrawlURI curi, LocalWebDriverBiDi driver) throws InterruptedException {
        String pageId = UUID.randomUUID().toString();
        var tab = driver.browsingContext().create(BrowsingContext.CreateType.tab).context();
        try {
            String userAgent = curi.getUserAgent();
            if (userAgent == null) userAgent = fetcher.getUserAgentProvider().getUserAgent();
            BrowserPage page = new BrowserPage(curi, new IdleBarrier(), driver, tab, fetcher.getProxy(), userAgent);
            pages.put(pageId, page);
            pageIdsByContext.put(tab, pageId);
            driver.network().addIntercept(List.of(Network.InterceptPhase.beforeRequestSent), List.of(tab),
                    List.of(new Network.UrlPatternPattern("pattern", "http"),
                            new Network.UrlPatternPattern("pattern", "https")));
            BrowsingContext.NavigateResult navigation;

            try {
                navigation = driver.browsingContext().navigate(tab, curi.getURI(), BrowsingContext.ReadinessState.complete);
            } catch (WebDriverException e) {
                if ("net::ERR_ABORTED".equals(e.getMessage())) return; // Chrome: probably download started
                throw e;
            }
            if (navigation.url().equals("about:blank")) return; // Firefox: probably download started

            logger.log(System.Logger.Level.DEBUG, "Navigated to {0}", navigation);

            // Wait for network activity to stop
            if (!page.networkActivity().awaitIdleFor(Duration.ofMillis(500), Duration.ofSeconds(30))) {
                logger.log(System.Logger.Level.DEBUG, "Timed out waiting network activity to stop on {0}", curi);
            }

            for (Behavior behavior : behaviors) {
                try {
                    behavior.run(page);
                } catch (WebDriverException e) {
                    if (!driver.isAlive()) throw e; // browser died: restart it and revisit the page
                    logger.log(ERROR, "Error running " + behavior.getClass().getName() + " on " + curi, e);
                } catch (Exception e) {
                    logger.log(ERROR, "Error running " + behavior.getClass().getName() + " on " + curi, e);
                }
            }

            curi.getAnnotations().add("browser");
        } finally {
            pageIdsByContext.remove(tab);
            pages.remove(pageId);
            try {
                driver.browsingContext().close(tab);
            } catch (WebDriverException e) {
                logger.log(WARNING, "Error closing tab for " + curi, e);
            }
        }
    }

    public void start() {
        if (isRunning) return;
        if (browsers.isEmpty()) throw new IllegalArgumentException("browsers must not be empty");
        for (String browser : browsers) {
            if (!browser.equals("local")) {
                throw new IllegalArgumentException("Unsupported browser '" + browser + "': only 'local' is supported");
            }
        }
        super.start();

        extractorChain.setProcessors(crawlController.getFetchChain().getProcessors().stream()
                .filter(processor -> processor instanceof Extractor).toList());
        Path scratchDir = crawlController.getScratchDir().getFile().toPath();
        try {
            // if this is the first launch, scratchDir may not exist yet
            Files.createDirectories(scratchDir);
        } catch (IOException e) {
            logger.log(ERROR, "Error creating scratch directory", e);
            throw new UncheckedIOException(e);
        }
        if (proxy == null) {
            proxy = new MitmProxy(this::handleProxyRequest,
                    scratchDir.resolve("proxy.keystore").toString());
        }
        try {
            proxy.start();
        } catch (Exception e) {
            logger.log(ERROR, "Error starting proxy server", e);
            throw new RuntimeException(e);
        }
        pool.clear();
        for (int i = 0; i < browsers.size(); i++) {
            pool.add(new Browser(i));
        }
        try {
            for (Browser browser : pool) {
                browser.launch();
            }
        } catch (Exception e) {
            logger.log(ERROR, "Error starting browser", e);
            throw new RuntimeException(e);
        }
    }

    private void handleBeforeRequestSent(LocalWebDriverBiDi driver, Network.BeforeRequestSent event) {
        if (event.request().url().startsWith("data:") || !event.isBlocked()) return;
        List<Network.Header> requestHeaders = event.request().headers();
        String pageId = pageIdsByContext.get(event.context());
        if (pageId != null) {
            requestHeaders.add(new Network.Header(PAGE_ID_HEADER, pageId));
            BrowserPage page = pages.get(pageId);
            if (page != null) {
                String userAgent = page.userAgent();
                if (userAgent != null) {
                    requestHeaders.removeIf(header -> header.name().equalsIgnoreCase("User-Agent"));
                    requestHeaders.add(new Network.Header("User-Agent", userAgent));
                }
            }
        }
        driver.network().continueRequestAsync(event.request().request(), requestHeaders)
                .exceptionally(e -> {
                    logger.log(WARNING, "Error continuing request " + event.request().url() + ": " + e);
                    return null;
                });
    }

    private void handleProxyRequest(MitmProxy.Request proxyRequest) throws IOException {
        logger.log(System.Logger.Level.DEBUG, "Handling proxy request {0}", proxyRequest);
        String pageId = proxyRequest.request().getHeaders().get(PAGE_ID_HEADER);
        BrowserPage page = pageId == null ? null : pages.get(pageId);
        if (page == null) {
            String message = "Missing or invalid " + PAGE_ID_HEADER + " header\n";
            proxyRequest.sendResponse(403, Map.of("Content-Type", "text/plain"),
                    new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
            return;
        }
        CrawlURI curi = page.curi();

        // FIXME: more accurate to annotate with another header indicating this is the main request?
        if (proxyRequest.request().getMethod().equals("GET") && curi.getURI().equals(proxyRequest.url())) {
            // Replay page response
            // FIXME: this probably doesn't handle duplicate headers (e.g. Set-Cookie) properly
            var headers = (Map<String,String>)curi.getData().get(A_HTTP_RESPONSE_HEADERS);
            proxyRequest.sendResponse(curi.getFetchStatus(), headers, curi.getRecorder().getContentReplayInputStream());
        } else {
            // Record exchange as a subresource
            proxyRequest.setListener(new SubresourceRecorder(page, proxyRequest.url()));
            proxyRequest.setUpstreamProxy(page.proxy);
        }
    }

    /**
     * A list of {@link Behavior}s to run on each page.
     */
    public void setBehaviors(List<Behavior> behaviors) {
        this.behaviors = behaviors;
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }

    /**
     * Webdriver executable to launch. If null, will try several common paths.
     * <p>
     * Firefox can be used directly as it implements WebDriver BiDI natively. To use Chrome set this to a
     * <a href="https://developer.chrome.com/docs/chromedriver">ChromeDriver</a> executable.
     */
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public String getExecutable() {
        return executable;
    }

    public List<String> getOptions() {
        return options;
    }

    /**
     * Extra command-line options to be passed to the webdriver executable.
     */
    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Maximum number of web pages that can be open in each browser at once.
     */
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public List<String> getBrowsers() {
        return browsers;
    }

    /**
     * Browsers to open pages in, one per entry. The only supported value is currently {@code local}, which
     * launches {@link #setExecutable(String) executable} on this machine. Pages from the same queue are opened in
     * the same browser while it has a free tab, so that a site keeps one browser's cookies and cache.
     */
    public void setBrowsers(List<String> browsers) {
        this.browsers = browsers;
    }

    /**
     * Records the request and response for a subresource the browser loads via the MITM proxy.
     */
    private class SubresourceRecorder implements MitmProxy.ExchangeListener {
        private final CrawlURI curi;
        private final WritableByteChannel requestRecorder;
        private final WritableByteChannel responseRecorder;
        private final BrowserPage page;
        private String method;
        private boolean recordingFailed;
        private boolean truncated;

        public SubresourceRecorder(BrowserPage page, String url) throws IOException {
            this.page = page;
            this.curi = page.curi().createCrawlURI(url, LinkContext.EMBED_MISC, Hop.EMBED);
            String requestId = UUID.randomUUID().toString();
            Recorder recorder = new Recorder(crawlController.getScratchDir().getFile(),
                    getClass().getSimpleName() + "-" + requestId,
                    crawlController.getRecorderOutBufferBytes(),
                    crawlController.getRecorderInBufferBytes());
            this.curi.setRecorder(recorder);
            fetcher.beginRecording(curi, recorder);
            this.curi.getAnnotations().add("subresource");
            this.requestRecorder = Channels.newChannel(recorder.outputWrap(null));
            //noinspection resource
            this.responseRecorder = Channels.newChannel(((RecordingInputStream)recorder.inputWrap(null)).asOutputStream());
            this.page.networkActivity().begin();
        }

        @Override
        public void onBegin(org.eclipse.jetty.client.Request request) {
            request.headers(headers -> headers.remove(PAGE_ID_HEADER));
        }

        @Override
        public void onHeaders(org.eclipse.jetty.client.Request request) {
            try {
                this.method = request.getMethod();
                FetchHTTP2.recordRequest(request, curi.getRecorder());
            } catch (Exception e) {
                logger.log(ERROR, "Error recording request header of {0}", curi, e);
                recordingFailed = true;
            }
        }

        @Override
        public void onContent(org.eclipse.jetty.client.Request request, ByteBuffer content) {
            try {
                requestRecorder.write(content.duplicate());
            } catch (Exception e) {
                logger.log(ERROR, "Error recording request body of {0}", curi, e);
                recordingFailed = true;
            }
        }

        @Override
        public void onHeaders(org.eclipse.jetty.client.Response response) {
            try {
                String header = FetchHTTP2.formatResponseHeader(response);
                responseRecorder.write(ByteBuffer.wrap(header.getBytes(StandardCharsets.US_ASCII)));
            } catch (Exception e) {
                logger.log(ERROR, "Error recording response header of " + curi, e);
                recordingFailed = true;
            }
            fetcher.updateCrawlURIWithResponseHeader(curi, response);
        }

        @Override
        public void onContent(org.eclipse.jetty.client.Response response, ByteBuffer content) {
            if (truncated) return; // already hit a limit; stop recording but treat as success
            try {
                responseRecorder.write(content.duplicate());
            } catch (RecorderLengthExceededException e) {
                curi.getAnnotations().add(LENGTH_TRUNC);
                truncated = true;
            } catch (RecorderTimeoutException e) {
                curi.getAnnotations().add(TIMER_TRUNC);
                truncated = true;
            } catch (Exception e) {
                logger.log(ERROR, "Error recording response body of " + curi, e);
                recordingFailed = true;
            }
        }

        @Override
        public void onComplete(Result result) {
            try {
                curi.getRecorder().close();
                fetcher.updateCrawlURIOnCompletion(curi, curi.getRecorder());

                if (recordingFailed) {
                    curi.setFetchStatus(FetchStatusCodes.S_RUNTIME_EXCEPTION);
                } else if (result.isFailed() && !truncated) {
                    Throwable failure = result.getFailure();
                    if (failure != null) curi.getNonFatalFailures().add(failure);
                    curi.setFetchStatus(failure instanceof TimeoutException
                            ? FetchStatusCodes.S_TIMEOUT : FetchStatusCodes.S_CONNECT_FAILED);
                } else {
                    subresourcesRecorded.incrementAndGet();
                    curi.getOverlayNames(); // for sideeffect of creating the overlayNames list

                    Frontier frontier = crawlController.getFrontier();
                    if ("GET".equals(method) && frontier != null) {
                        frontier.considerIncluded(curi);
                    }

                    KeyedProperties.loadOverridesFrom(curi);
                    try {
                        extractorChain.process(curi, null);

                        if (frontier != null) frontier.beginDisposition(curi);
                        try {
                            crawlController.getDispositionChain().process(curi, null);
                        } finally {
                            if (frontier != null) frontier.endDisposition();
                        }
                    } finally {
                        KeyedProperties.clearOverridesFrom(curi);
                    }
                }

                if (curi.isSuccess()) {
                    eventPublisher.publishEvent(new CrawlURIDispositionEvent(this, curi, SUCCEEDED));
                } else {
                    eventPublisher.publishEvent(new CrawlURIDispositionEvent(this, curi, FAILED));
                }

                if (crawlController.getLoggerModule() != null) {
                    curi.aboutToLog();
                    crawlController.getLoggerModule().getUriProcessing().log(java.util.logging.Level.INFO,
                            curi.getUURI().toString(), curi);
                }
            } catch (Exception e) {
                logger.log(ERROR, "Error completing " + curi, e);
            } finally {
                page.networkActivity().end();
                curi.getRecorder().cleanup();
            }
        }
    }

    /**
     * Launches a browser and restarts it if it dies. Tracks how many of its
     * {@link #setConcurrency tab slots} are in use.
     */
    protected class Browser {
        protected final int id;
        protected volatile LocalWebDriverBiDi driver;
        protected int openTabs; // guarded by tabsLock
        protected long pagesVisited; // guarded by tabsLock
        protected volatile int restarts;

        Browser(int id) {
            this.id = id;
        }

        synchronized void launch() throws Exception {
            driver = createWebDriver();
        }

        /**
         * Replaces a dead browser with a freshly launched one. Threads that lose the race to restart
         * still return true so they retry against the browser the winner launched.
         *
         * @return true if a live browser is now available, false if the restart failed or the
         *         processor is stopping
         */
        synchronized boolean restart(LocalWebDriverBiDi deadDriver) {
            if (!isRunning) return false;
            if (driver != deadDriver) return true; // another thread already restarted it
            try {
                deadDriver.close();
            } catch (Exception e) {
                logger.log(WARNING, "Error closing dead browser", e);
            }
            try {
                driver = createWebDriver();
                restarts++;
                return true;
            } catch (Exception e) {
                logger.log(ERROR, "Failed to restart browser", e);
                return false;
            }
        }

        synchronized void close() {
            if (driver == null) return;
            try {
                driver.close();
            } catch (Exception e) {
                logger.log(ERROR, "Error closing WebDriverBiDi", e);
            }
        }

        private LocalWebDriverBiDi createWebDriver() throws Exception {
            Path scratchDir = crawlController.getScratchDir().getFile().toPath();
            Path profileDir = scratchDir.resolve("profile-" + id);
            Files.createDirectories(profileDir);

            // Firefox doesn't seem to allow setting prefs via capabilities with bidi
            // so drop them in user.js instead
            Files.writeString(profileDir.resolve("user.js"), """
            // send localhost requests via the proxy too (for tests and local crawling)
            user_pref('network.proxy.allow_hijacking_localhost', true);

            // disable downloads by setting to something that can't be created as a directory
            user_pref('browser.download.dir', '/dev/null');
            user_pref('browser.download.folderList', 2);
            """);

            int proxyPort = proxy.getPort();
            var alwaysMatch = Map.of("acceptInsecureCerts", true,
                    "proxy", new Session.ProxyConfiguration("manual",
                            "127.0.0.1:" + proxyPort, "127.0.0.1:" + proxyPort));

            List<Map<String,Object>> firstMatch = List.of(
                    Map.of("browserName", "chrome",
                            "goog:chromeOptions", Map.of(
                                    "args", List.of("headless=new", "user-data-dir=" + profileDir,
                                            "proxy-bypass-list=<-loopback>"),
                                    "prefs", Map.of("download_restrictions", 3))),
                    // Fallback for other browsers
                    Map.of()
            );
            var capabilities = new Session.CapabilitiesRequest(alwaysMatch, firstMatch);

            var driver = new LocalWebDriverBiDi(executable, options, capabilities, profileDir);
            try {
                driver.session().subscribe(List.of("browsingContext.contextCreated",
                        "network.beforeRequestSent"), null);
                driver.on(Network.BeforeRequestSent.class, event -> handleBeforeRequestSent(driver, event));
            } catch (Exception e) {
                driver.close();
                throw e;
            }
            return driver;
        }
    }

    /**
     * A CrawlURI that's currently loaded as a page in a browser.
     */
    protected record BrowserPage(
            CrawlURI curi,
            IdleBarrier networkActivity,
            WebDriverBiDi webdriver,
            BrowsingContext.Context context,
            ProxyConfiguration.Proxy proxy,
            String userAgent
    ) implements Page {

        /**
         * Evaluates JavaScript and returns the result as simple Java objects (numbers, strings, maps, lists).
         */
        public <T> T eval(String script, Object... args) {
            return callFunction(script, false, args);
        }

        @SuppressWarnings("unchecked")
        private <T> T callFunction(String script, boolean awaitPromise, Object... args) {
            List<Script.LocalValue> argsLocal = null;
            if (args != null && args.length > 0) {
                argsLocal = Stream.of(args).map(Script.LocalValue::from).toList();
            }
            var result = webdriver().script().callFunction(script, scriptTarget(), awaitPromise, argsLocal);
            if (result instanceof Script.EvaluateResultSuccess success) {
                return (T) success.result().javaValue();
            } else if (result instanceof Script.EvaluateResultException failure) {
                throw new RuntimeException(failure.exceptionDetails().text());
            } else {
                throw new RuntimeException("Unexpected result from script evaluation: " + result);
            }
        }

        private Script.ContextTarget scriptTarget() {
            return new Script.ContextTarget(context(), "heritrix");
        }

        @Override
        public <T> T evalPromise(String script, Object... args) {
            return callFunction(script, true, args);
        }
    }
}