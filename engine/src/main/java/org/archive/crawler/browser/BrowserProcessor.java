package org.archive.crawler.browser;

import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorChain;
import org.archive.modules.behaviors.Behavior;
import org.archive.modules.behaviors.ExtractLinksBehavior;
import org.archive.modules.extractor.Extractor;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.extractor.UriErrorLoggerModule;
import org.archive.modules.fetcher.FetchHTTP2;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.net.MitmProxy;
import org.archive.net.webdriver.BrowsingContext;
import org.archive.net.webdriver.Network;
import org.archive.net.webdriver.LocalWebDriverBiDi;
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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.System.Logger.Level.ERROR;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.FAILED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.SUCCEEDED;
import static org.archive.modules.CoreAttributeConstants.A_HTTP_RESPONSE_HEADERS;

/**
 * Processor which opens a web page in a real web browser via WebDriver BiDi. Subresources loaded by the
 * browser are recorded using a MITM proxy. Must be used in conjunction with {@link FetchHTTP2}.
 */
public class BrowserProcessor extends Processor {
    private static final System.Logger logger = System.getLogger(BrowserProcessor.class.getName());
    public static final String PAGE_ID_HEADER = "Heritrix-Request-ID";
    private final CrawlController crawlController;
    private final MitmProxy proxy = new MitmProxy(this::handleProxyRequest);
    private final FetchHTTP2 fetcher;
    private LocalWebDriverBiDi webdriver;
    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, BrowserPage> pages = new ConcurrentHashMap<>();
    private final Map<BrowsingContext.Context, String> pageIdsByContext = new ConcurrentHashMap<>();
    private final ProcessorChain extractorChain = new ProcessorChain();
    private List<Behavior> behaviors;

    public BrowserProcessor(FetchHTTP2 fetcher, CrawlController crawlController, ApplicationEventPublisher eventPublisher,
                            UriErrorLoggerModule uriErrorLoggerModule) {
        this.crawlController = crawlController;
        this.fetcher = fetcher;
        this.eventPublisher = eventPublisher;
        this.behaviors = List.of(new ExtractLinksBehavior(uriErrorLoggerModule));
    }

    public void stop() {
        if (!isRunning) return;
        super.stop();
        if (proxy != null) {
            try {
                proxy.stop();
            } catch (Exception e) {
                logger.log(ERROR, "Error stopping proxy server", e);
            }
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
            logger.log(System.Logger.Level.DEBUG, "Navigated to {}", navigation);

            // Wait for network activity to stop
            if (!page.networkActivity().awaitIdleFor(Duration.ofMillis(500), Duration.ofSeconds(30))) {
                logger.log(System.Logger.Level.DEBUG, "Timed out waiting network activity to stop on {0}", curi);
            }

            for (Behavior behavior : behaviors) {
                try {
                    behavior.run(page);
                } catch (Exception e) {
                    logger.log(ERROR, "Error running {0}", behavior.getClass().getName(), e);
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

        extractorChain.setProcessors(crawlController.getFetchChain().getProcessors().stream()
                .filter(processor -> processor instanceof Extractor).toList());

        try {
            proxy.start();
        } catch (Exception e) {
            logger.log(ERROR, "Error starting proxy server", e);
            throw new RuntimeException(e);
        }
        try {
            this.webdriver = new LocalWebDriverBiDi(proxy.getPort());
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

    private class SubresourceRecorder implements MitmProxy.ExchangeListener {
        private final CrawlURI curi;
        private final WritableByteChannel requestRecorder;
        private final WritableInputRecorder responseRecorder;
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
            this.responseRecorder = new WritableInputRecorder(recorder);
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
                logger.log(ERROR, "Error recording response header of {0}", curi, e);
                recordingFailed = true;
            }
            fetcher.updateCrawlURIWithResponseHeader(curi, response);
        }

        @Override
        public void onContent(org.eclipse.jetty.client.Response response, ByteBuffer content) {
            try {
                responseRecorder.write(content.duplicate());
            } catch (Exception e) {
                logger.log(ERROR, "Error recording response body of {0}", curi, e);
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

                    if ("GET".equals(method)) {
                        crawlController.getFrontier().considerIncluded(curi);
                    }

                    KeyedProperties.loadOverridesFrom(curi);
                    try {
                        extractorChain.process(curi, null);

                        crawlController.getFrontier().beginDisposition(curi);
                        try {
                            crawlController.getDispositionChain().process(curi, null);
                        } finally {
                            crawlController.getFrontier().endDisposition();
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

                curi.aboutToLog();
                crawlController.getLoggerModule().getUriProcessing().log(java.util.logging.Level.INFO,
                        curi.getUURI().toString(), curi);
            } catch (Exception e) {
                logger.log(ERROR, "Error completing {0}", curi, e);
            } finally {
                page.networkActivity().end();
                curi.getRecorder().cleanup();
            }
        }
    }

    /**
     * Adapts a Recorder's RecordingInputStream so it's writable.
     * TODO: Modify RecordingInputStream to enable writing directly as this is a bit of a hack.
     */
    private static class WritableInputRecorder {
        private final InputStream inputStream;
        private final byte[] discardBuffer = new byte[8192];
        private ByteBuffer inputBuffer;

        public WritableInputRecorder(Recorder recorder) throws IOException {
            this.inputStream = recorder.inputWrap(new InputStream() {
                @Override
                public int read(byte[] b, int off, int len) {
                    inputBuffer.get(b, off, len);
                    return len;
                }

                @Override
                public int read() {
                    throw new AssertionError();
                }
            });
        }

        void write(ByteBuffer data) throws IOException {
            this.inputBuffer = data;
            while (inputBuffer.hasRemaining()) {
                var n = inputStream.read(discardBuffer, 0, Math.min(discardBuffer.length, inputBuffer.remaining()));
                assert n >= 0;
            }
        }
    }
}
