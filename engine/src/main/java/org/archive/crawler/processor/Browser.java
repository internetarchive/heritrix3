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

import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Frontier;
import org.archive.io.RecordingInputStream;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorChain;
import org.archive.modules.behaviors.Behavior;
import org.archive.modules.behaviors.ExtractLinks;
import org.archive.modules.behaviors.Page;
import org.archive.modules.behaviors.ScrollDown;
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
import org.eclipse.jetty.client.Result;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

import static java.lang.System.Logger.Level.ERROR;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.FAILED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.SUCCEEDED;
import static org.archive.modules.CoreAttributeConstants.A_HTTP_RESPONSE_HEADERS;

/**
 * Processor which opens a web page in a real web browser via WebDriver BiDi and runs {@link Behavior} scripts.
 * Subresources loaded by the browser are recorded using a MITM proxy. Must be used in conjunction with
 * {@link FetchHTTP2}.
 */
public class Browser extends Processor {
    private static final System.Logger logger = System.getLogger(Browser.class.getName());
    protected static final String PAGE_ID_HEADER = "Heritrix-Request-ID";
    protected final CrawlController crawlController;
    protected final MitmProxy proxy = new MitmProxy(this::handleProxyRequest);
    protected final FetchHTTP2 fetcher;
    protected LocalWebDriverBiDi webdriver;
    protected final ApplicationEventPublisher eventPublisher;
    protected final Map<String, BrowserPage> pages = new ConcurrentHashMap<>();
    protected final Map<BrowsingContext.Context, String> pageIdsByContext = new ConcurrentHashMap<>();
    protected final ProcessorChain extractorChain = new ProcessorChain();
    protected List<Behavior> behaviors;
    protected String executable;
    protected List<String> options = List.of("--headless");
    protected int concurrency = 20;
    protected Semaphore semaphore;

    public Browser(FetchHTTP2 fetcher, CrawlController crawlController, ApplicationEventPublisher eventPublisher,
                   UriErrorLoggerModule uriErrorLoggerModule) {
        this.crawlController = crawlController;
        this.fetcher = fetcher;
        this.eventPublisher = eventPublisher;
        this.behaviors = List.of(new ScrollDown(), new ExtractLinks(uriErrorLoggerModule));
    }

    public void stop() {
        if (!isRunning) return;
        super.stop();
        try {
            proxy.stop();
        } catch (Exception e) {
            logger.log(ERROR, "Error stopping proxy server", e);
        }
        try {
            webdriver.close();
        } catch (Exception e) {
            logger.log(ERROR, "Error closing WebDriverBiDi", e);
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        if (curi.getFetchStatus() != 200) return false;
        String scheme = curi.getUURI().getScheme();
        if (!scheme.equals("https") && !scheme.equals("http")) return false;
        String mime = curi.getContentType().toLowerCase();
        if (!mime.startsWith("text/html")) return false;
        return true;
    }

    @Override
    public void innerProcess(CrawlURI curi) {
        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            visit(curi);
        } finally {
            semaphore.release();
        }
    }

    private void visit(CrawlURI curi) {
        String pageId = UUID.randomUUID().toString();
        var tab = webdriver.browsingContext().create(BrowsingContext.CreateType.tab).context();
        try {
            BrowserPage page = new BrowserPage(curi, new IdleBarrier(), webdriver, tab);
            pages.put(pageId, page);
            pageIdsByContext.put(tab, pageId);
            webdriver.network().addIntercept(List.of(Network.InterceptPhase.beforeRequestSent), List.of(tab),
                    List.of(new Network.UrlPatternPattern("pattern", "http"),
                            new Network.UrlPatternPattern("pattern", "https")));
            var navigation = webdriver.browsingContext().navigate(tab, curi.getURI(), BrowsingContext.ReadinessState.complete);
            logger.log(System.Logger.Level.DEBUG, "Navigated to {0}", navigation);

            // Wait for network activity to stop
            if (!page.networkActivity().awaitIdleFor(Duration.ofMillis(500), Duration.ofSeconds(30))) {
                logger.log(System.Logger.Level.DEBUG, "Timed out waiting network activity to stop on {0}", curi);
            }

            for (Behavior behavior : behaviors) {
                try {
                    behavior.run(page);
                } catch (Exception e) {
                    logger.log(ERROR, "Error running " + behavior.getClass().getName() + " on " + curi, e);
                }
            }

            curi.getAnnotations().add("browser");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pageIdsByContext.remove(tab);
            pages.remove(pageId);
            webdriver.browsingContext().close(tab);
        }
    }

    public void start() {
        if (isRunning) return;
        super.start();

        semaphore = new Semaphore(concurrency);
        extractorChain.setProcessors(crawlController.getFetchChain().getProcessors().stream()
                .filter(processor -> processor instanceof Extractor).toList());

        try {
            proxy.start();
        } catch (Exception e) {
            logger.log(ERROR, "Error starting proxy server", e);
            throw new RuntimeException(e);
        }
        try {
            Path profileDir = crawlController.getScratchDir().getFile().toPath().resolve("profile");
            Files.createDirectories(profileDir);

            // Firefox: send localhost requests via the proxy too (for tests and local crawling)
            Files.writeString(profileDir.resolve("user.js"), "user_pref('network.proxy.allow_hijacking_localhost', true);");

            this.webdriver = new LocalWebDriverBiDi(executable, options, proxy.getPort(), profileDir);
        } catch (Exception e) {
            logger.log(ERROR, "Error starting browser", e);
            throw new RuntimeException(e);
        }
        webdriver.session().subscribe(List.of("browsingContext.contextCreated",
                "network.beforeRequestSent"), null);
        webdriver.on(Network.BeforeRequestSent.class, this::handleBeforeRequestSent);
    }

    private void handleBeforeRequestSent(Network.BeforeRequestSent event) {
        if (event.request().url().startsWith("data:") || !event.isBlocked()) return;
        List<Network.Header> requestHeaders = event.request().headers();
        String pageId = pageIdsByContext.get(event.context());
        if (pageId != null) requestHeaders.add(new Network.Header(PAGE_ID_HEADER, pageId));
        webdriver.network().continueRequestAsync(event.request().request(), requestHeaders);
    }

    private void handleProxyRequest(MitmProxy.Request proxyRequest) throws IOException {
        logger.log(System.Logger.Level.DEBUG, "Handling proxy request {0}", proxyRequest);
        if (proxyRequest.url().startsWith("https://firefox.settings.services.mozilla.com/")) {
            proxyRequest.sendResponse(403, Map.of(), InputStream.nullInputStream());
            return;
        }
        String pageId = proxyRequest.request().getHeaders().get(PAGE_ID_HEADER);
        if (pageId == null) return;
        BrowserPage page = pages.get(pageId);
        if (page == null) return;
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
        }
    }

    /**
     * Sets a list of {@link Behavior}s to run on each page.
     */
    public void setBehaviors(List<Behavior> behaviors) {
        this.behaviors = behaviors;
    }

    public List<Behavior> getBehaviors() {
        return behaviors;
    }

    /**
     * Sets the webdriver executable to launch. If null, will try several common paths.
     * <p>
     * Firefox can be used directly as implements WebDriver BiDI natively. To use Chrome set this to a
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

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getConcurrency() {
        return concurrency;
    }

    /**
     * Sets the maximum number of web pages that can be open in the browser at once.
     */
    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
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

        public SubresourceRecorder(BrowserPage page, String url) throws IOException {
            this.page = page;
            this.curi = page.curi().createCrawlURI(url, LinkContext.EMBED_MISC, Hop.EMBED);
            String requestId = UUID.randomUUID().toString();
            Recorder recorder = new Recorder(crawlController.getScratchDir().getFile(),
                    getClass().getSimpleName() + "-" + requestId,
                    crawlController.getRecorderOutBufferBytes(),
                    crawlController.getRecorderInBufferBytes());
            this.curi.setRecorder(recorder);
            this.curi.setFetchBeginTime(System.currentTimeMillis());
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
            try {
                responseRecorder.write(content.duplicate());
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
                } else {
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
     * A CrawlURI that's currently loaded as a page in a browser.
     */
    record BrowserPage(CrawlURI curi,
                       IdleBarrier networkActivity,
                       WebDriverBiDi webdriver,
                       BrowsingContext.Context context) implements Page {

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