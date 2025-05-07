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
import org.archive.net.webdriver.BrowsingContext;
import org.archive.net.webdriver.Network;
import org.archive.net.webdriver.LocalWebDriverBiDi;
import org.archive.spring.KeyedProperties;
import org.archive.util.IdleBarrier;
import org.archive.util.Recorder;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.*;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.util.Callback;
import org.springframework.context.ApplicationEventPublisher;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.logging.Level.INFO;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.FAILED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.SUCCEEDED;
import static org.archive.modules.CoreAttributeConstants.A_HTTP_RESPONSE_HEADERS;
import static org.eclipse.jetty.http.HttpHeader.ACCEPT_ENCODING;

/**
 * HTML link extractor which uses a real web browser via WebDriver BiDi. Subresources loaded by the
 * browser are recorded using a MITM proxy. Must be used in conjunction with {@link FetchHTTP2}.
 */
public class BrowserProcessor extends Processor {
    private static final System.Logger log = System.getLogger(BrowserProcessor.class.getName());
    public static final String PAGE_ID_HEADER = "Heritrix-Request-ID";
    private final CrawlController crawlController;
    private SslConnectionFactory sslConnectionFactory;
    private Server proxyServer;
    private final FetchHTTP2 fetcher;
    private LocalWebDriverBiDi webdriver;
    private final ApplicationEventPublisher eventPublisher;
    private Map<String, BrowserPage> pages = new ConcurrentHashMap<>();
    private ProcessorChain extractorChain = new ProcessorChain();
    private Map<BrowsingContext.Context, String> pageIdsByContext = new ConcurrentHashMap<>();
    private List<Behavior> behaviors;

    private boolean isRunning = false;

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
        if (proxyServer != null) {
            try {
                proxyServer.stop();
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "Error stopping proxy server", e);
            }
        }
        if (sslConnectionFactory != null) {
            try {
                sslConnectionFactory.stop();
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "Error stopping SSL connection factory", e);
            }
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

    private void handleBeforeRequestSent(Network.BeforeRequestSent event) {
        if (event.request().url().startsWith("data:") || !event.isBlocked()) return;
        List<Network.Header> requestHeaders = event.request().headers();
        String pageId = pageIdsByContext.get(event.context());
        if (pageId != null) requestHeaders.add(new Network.Header(PAGE_ID_HEADER, pageId));
        webdriver.network().continueRequestAsync(event.request().request(), requestHeaders);
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
            System.out.println("Navigated to " + navigation);

            // Wait for network activity to stop
            if (!page.networkActivity().awaitIdleFor(Duration.ofMillis(500), Duration.ofSeconds(30))) {
                log.log(System.Logger.Level.DEBUG, "Timed out waiting network activity to stop on {0}", curi);
            }

            for (Behavior behavior : behaviors) {
                try {
                    behavior.run(page);
                } catch (Exception e) {
                    log.log(System.Logger.Level.ERROR, "Error running {0}", behavior.getClass().getName(), e);
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
                .filter(processor -> processor instanceof Extractor
                        && !(processor instanceof BrowserProcessor)).toList());

        try {
            this.sslConnectionFactory = new SslConnectionFactory();
            sslConnectionFactory.getSslContextFactory().setKeyStorePath("adhoc.keystore");
            sslConnectionFactory.getSslContextFactory().setKeyStorePassword("password");
            sslConnectionFactory.start();

            this.proxyServer = new Server(0);
            var proxyHandler = new RecordingProxyHandler();

            proxyServer.setHandler(new Handler.Sequence(
                    // Handle CONNECT by upgrading to TLS
                    new ConnectHandler() {
                        @Override
                        protected void handleConnect(Request request, Response response, Callback callback, String serverAddress) {
                            EndPoint clientEP = request.getTunnelSupport().getEndPoint();
                            var sslConnection = sslConnectionFactory.newConnection(proxyServer.getConnectors()[0], clientEP);
                            request.setAttribute(HttpStream.UPGRADE_CONNECTION_ATTRIBUTE, sslConnection);
                            response.setStatus(200);
                            callback.succeeded();
                        }
                    },
                    proxyHandler));
            proxyServer.start();
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Error starting proxy server", e);
            throw new RuntimeException(e);
        }
        try {
            fetcher.start();
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Error starting fetcher", e);
            throw new RuntimeException(e);
        }
        try {
            int proxyPort = ((InetSocketAddress) ((ServerSocketChannel) proxyServer.getConnectors()[0].getTransport()).getLocalAddress()).getPort();
            this.webdriver = new LocalWebDriverBiDi(proxyPort);
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Error starting browser", e);
            throw new RuntimeException(e);
        }
        webdriver.session().subscribe(List.of("browsingContext.contextCreated",
                "network.beforeRequestSent"), null);
        webdriver.on(Network.BeforeRequestSent.class, this::handleBeforeRequestSent);
    }

    private class RecordingProxyHandler extends ProxyHandler.Forward {
        @Override
        protected HttpClient newHttpClient() {
            // FIXME: Transports aren't intended to be shared, and we're kind of clobbering
            //   the fetcher's. Might be better to share the whole HttpClient but we to do that need
            //   we need to rework the DNS resolver and cookie store
            HttpClient httpClient = new HttpClient(fetcher.getHttpClient().getTransport());
            httpClient.setMaxConnectionsPerDestination(6);
            httpClient.setFollowRedirects(false);
            httpClient.setUserAgentField(null);
            return httpClient;
        }

        @Override
        protected HttpURI rewriteHttpURI(Request clientToProxyRequest) {
            HttpURI uri = super.rewriteHttpURI(clientToProxyRequest);
            String string = uri.asString();
            // HttpClient uses Java URI which unlike WhatWG doesn't allow "|" so for now just percent encode it
            if (string.contains("|")) {
                return HttpURI.from(string.replace("|", "%7C"));
            } else {
                return uri;
            }
        }

        @Override
        public boolean handle(Request request, Response response, Callback callback) {
            // Check if we've already fetched the page, if so just replay the responses
            String pageId = request.getHeaders().get(PAGE_ID_HEADER);
            if (pageId != null) {
                BrowserPage page = pages.get(pageId);
                if (page != null) {
                    CrawlURI curi = page.curi();
                    // FIXME: probably should annotate with another header indicating this is the main request
                    if (curi.getURI().equals(request.getHttpURI().toString())) {
                        try {
                            var headers = (Map<String,String>)curi.getData().get(A_HTTP_RESPONSE_HEADERS);
                            headers.forEach((k,v) -> response.getHeaders().put(k, v));
                            response.setStatus(curi.getFetchStatus());
                            curi.getRecorder().getContentReplayInputStream()
                                    .transferTo(Content.Sink.asOutputStream(response));
                            callback.succeeded();
                            return true;
                        } catch (IOException e) {
                            callback.failed(e);
                        }

                    }
                }
            }
            return super.handle(request, response, callback);
        }

        @Override
        protected void addProxyHeaders(Request clientToProxyRequest, org.eclipse.jetty.client.Request proxyToServerRequest) {
            String pageId = proxyToServerRequest.getHeaders().get(PAGE_ID_HEADER);
            if (pageId != null) {
                var page = pages.get(pageId);
                if (page != null) {
                    try {
                        var recorder = new RequestRecorder(page, proxyToServerRequest);
                        clientToProxyRequest.setAttribute(RequestRecorder.class.getName(), recorder);
                        recorder.handleRequestHeader(proxyToServerRequest);
                    } catch (Exception e) {
                        log.log(System.Logger.Level.ERROR, "Error recording request", e);
                    }
                }

                // Remove our own marker header
                proxyToServerRequest.headers(headers -> headers.remove(PAGE_ID_HEADER));

                // Ensure we only get encodings we support
                proxyToServerRequest.headers(headers -> headers.remove(ACCEPT_ENCODING)
                        .put(ACCEPT_ENCODING, "gzip"));
            }

            // Host header is not allowed in HTTP/2
            proxyToServerRequest.headers(headers -> headers.remove(HttpHeader.HOST));

            // Don't call super to avoid adding the Via and Forwarded proxy headers as
            // the proxy shouldn't be visible to the server.
            //super.addProxyHeaders(clientToProxyRequest, proxyToServerRequest);
        }

        @Override
        protected org.eclipse.jetty.client.Request.Content newProxyToServerRequestContent(Request clientToProxyRequest, Response proxyToClientResponse, org.eclipse.jetty.client.Request proxyToServerRequest) {
            RequestRecorder requestRecorder = (RequestRecorder) clientToProxyRequest.getAttribute(RequestRecorder.class.getName());
            if (requestRecorder == null) {
                return super.newProxyToServerRequestContent(clientToProxyRequest, proxyToClientResponse, proxyToServerRequest);
            }
            return new ProxyRequestContent(clientToProxyRequest) {
                @Override
                public Content.Chunk read() {
                    Content.Chunk chunk = super.read();
                    if (chunk != null) {
                        requestRecorder.handleRequestBodyChunk(chunk);
                    }
                    return chunk;
                }
            };
        }

        @Override
        protected org.eclipse.jetty.client.Response.CompleteListener newServerToProxyResponseListener(Request clientToProxyRequest, org.eclipse.jetty.client.Request proxyToServerRequest, Response proxyToClientResponse, Callback proxyToClientCallback) {
            RequestRecorder requestRecorder = (RequestRecorder) clientToProxyRequest.getAttribute(RequestRecorder.class.getName());
            if (requestRecorder == null) {
                return super.newServerToProxyResponseListener(clientToProxyRequest, proxyToServerRequest, proxyToClientResponse, proxyToClientCallback);
            }
            return new ProxyResponseListener(clientToProxyRequest, proxyToServerRequest, proxyToClientResponse, proxyToClientCallback) {
                @Override
                public void onHeaders(org.eclipse.jetty.client.Response serverToProxyResponse) {
                    requestRecorder.handleResponseHeader(serverToProxyResponse);
                    super.onHeaders(serverToProxyResponse);
                }

                @Override
                public void onContent(org.eclipse.jetty.client.Response serverToProxyResponse, Content.Chunk serverToProxyChunk, Runnable serverToProxyDemander) {
                    requestRecorder.handleResponseBodyChunk(serverToProxyChunk);
                    super.onContent(serverToProxyResponse, serverToProxyChunk, serverToProxyDemander);
                }

                {
                    whenComplete((r, t) -> {
                        requestRecorder.handleCompletion(t);
                    });
                }
            };
        }
    }

    class RequestRecorder {
        private final CrawlURI curi;
        private final WritableByteChannel requestBodyChannel;
        private final BrowserPage page;
        private ByteBuffer responseBodyBuffer;
        private final byte[] buffer = new byte[8192];
        private String method;

        public RequestRecorder(BrowserPage page, org.eclipse.jetty.client.Request request) throws IOException {
            this.page = page;
            this.curi = page.curi().createCrawlURI(request.getURI().toString(), LinkContext.EMBED_MISC, Hop.EMBED);
            var requestId = UUID.randomUUID().toString();
            Recorder recorder = new Recorder(crawlController.getScratchDir().getFile(),
                    getClass().getSimpleName() + "-" + requestId,
                    crawlController.getRecorderOutBufferBytes(),
                    crawlController.getRecorderInBufferBytes());
            this.curi.setRecorder(recorder);
            this.curi.setFetchBeginTime(System.currentTimeMillis());
            this.curi.getAnnotations().add("subresource");

            this.requestBodyChannel = Channels.newChannel(recorder.outputWrap(null));
            this.curi.getRecorder().inputWrap(new InputStream() {
                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    responseBodyBuffer.get(b, off, len);
                    return len;
                }

                @Override
                public int read() throws IOException {
                    throw new IOException("Not implemented");
                }
            });
            this.page.networkActivity().begin();
        }

        public void handleRequestHeader(org.eclipse.jetty.client.Request request) {
            try {
                this.method = request.getMethod();
                FetchHTTP2.recordRequest(request, curi.getRecorder());
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "Error recording request", e);
                throw new RuntimeException(e);
            }
        }

        public void handleResponseHeader(org.eclipse.jetty.client.Response response) {
            writeToResponse(ByteBuffer.wrap(FetchHTTP2.formatResponseHeader(response).getBytes(StandardCharsets.US_ASCII)));
            fetcher.updateCrawlURIWithResponseHeader(curi, response);
        }

        public void handleRequestBodyChunk(Content.Chunk chunk) {
            try {
                requestBodyChannel.write(chunk.getByteBuffer().duplicate());
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "Error recording chunk", e);
                throw new RuntimeException(e);
            }
        }

        public void handleResponseBodyChunk(Content.Chunk chunk) {
            writeToResponse(chunk.getByteBuffer().duplicate());
        }

        private void writeToResponse(ByteBuffer byteBuffer) {
            var stream = curi.getRecorder().getRecordedInput();
            try {
                this.responseBodyBuffer = byteBuffer;
                while (responseBodyBuffer.hasRemaining()) {
                    stream.read(buffer, 0, Math.min(buffer.length, responseBodyBuffer.remaining()));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public void handleCompletion(Throwable throwable) {
            try {
                curi.getRecorder().close();
                fetcher.updateCrawlURIOnCompletion(curi, curi.getRecorder());

                curi.getOverlayNames(); // for side-effect of creating the overlayNames list

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

                if (curi.isSuccess()) {
                    eventPublisher.publishEvent(new CrawlURIDispositionEvent(this, curi, SUCCEEDED));
                } else {
                    eventPublisher.publishEvent(new CrawlURIDispositionEvent(this, curi, FAILED));
                }

                curi.aboutToLog();
                crawlController.getLoggerModule().getUriProcessing().log(INFO, curi.getUURI().toString(), curi);

            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "Error processing disposition", e);
            } finally {
                page.networkActivity().end();
                curi.getRecorder().cleanup();
            }
        }
    }
}
