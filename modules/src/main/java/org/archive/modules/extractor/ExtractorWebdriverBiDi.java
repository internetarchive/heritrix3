package org.archive.modules.extractor;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.DispositionChain;
import org.archive.modules.Processor;
import org.archive.modules.fetcher.FetchHTTP2;
import org.archive.net.UURIFactory;
import org.archive.net.WebdriverBiDi;
import org.archive.net.WebdriverBiDi.Network;
import org.archive.spring.ConfigPath;
import org.archive.util.Recorder;
import org.eclipse.jetty.io.*;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.util.Callback;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExtractorWebdriverBiDi extends Processor {
    private static final System.Logger log = System.getLogger(ExtractorWebdriverBiDi.class.getName());
    public static final String REQUEST_ID_HEADER = "Heritrix-Request-ID";
    private final DispositionChain dispositionChain;
    private SslConnectionFactory sslConnectionFactory;
    private Server proxyServer;
    private final Map<String, RequestRecorder> requestRecorders = new ConcurrentHashMap<>();
    private final FetchHTTP2 fetcher;
    private ConfigPath scratchDir = new ConfigPath("scratch subdirectory", "scratch");
    private WebdriverBiDi browser;

    public ExtractorWebdriverBiDi(FetchHTTP2 fetcher, DispositionChain dispositionChain) {
        this.fetcher = fetcher;
        this.dispositionChain = dispositionChain;
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
    protected boolean shouldProcess(CrawlURI uri) {
        String scheme = uri.getUURI().getScheme();
        return scheme.equals("https") || scheme.equals("http");
    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        var tab = browser.browsingContext().create(WebdriverBiDi.BrowsingContext.CreateType.tab).context();
        try {
            browser.network().addIntercept(List.of(Network.InterceptPhase.beforeRequestSent), List.of(tab),
                    List.of(new Network.UrlPatternPattern("pattern", "http"),
                            new Network.UrlPatternPattern("pattern", "https")));
            browser.session().subscribe(List.of("network.beforeRequestSent"), List.of(tab));
            browser.on(Network.BeforeRequestSent.class, event -> {
                if (event.request().url().startsWith("data:") || !event.isBlocked()) return;
                CrawlURI subCuri;
                try {
                    subCuri = curi.createCrawlURI(event.request().url(), LinkContext.EMBED_MISC, Hop.EMBED);
                } catch (URIException e) {
                    log.log(System.Logger.Level.ERROR, "Error creating CrawlURI, ignoring request", e);
                    browser.network().continueRequestAsync(event.request().request());
                    return;
                }
                var requestId = UUID.randomUUID().toString();
                subCuri.setRecorder(new Recorder(scratchDir.getFile(), getClass().getSimpleName() + "-" + requestId));
                subCuri.setFetchBeginTime(System.currentTimeMillis());
                requestRecorders.put(requestId, new RequestRecorder(subCuri, event));
                List<Network.Header> requestHeaders = event.request().headers();
                requestHeaders.add(new Network.Header(REQUEST_ID_HEADER, requestId));
                browser.network().continueRequestAsync(event.request().request(), requestHeaders);
            });
            browser.browsingContext().navigate(tab, "https://www.abc.net.au/");

            // TODO: wait for idle

            Thread.sleep(10000);

        } finally {
            browser.browsingContext().close(tab);
        }
    }

    public void start() {
        if (isRunning) return;
        super.start();
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
            this.browser = new WebdriverBiDi(proxyPort);
        } catch (Exception e) {
            log.log(System.Logger.Level.ERROR, "Error starting browser", e);
            throw new RuntimeException(e);
        }
    }


    public static void main(String[] args) throws Exception {
        ExtractorWebdriverBiDi extractorWebdriverBiDi = new ExtractorWebdriverBiDi(new FetchHTTP2(null, null), null);
        extractorWebdriverBiDi.start();
        try {
            extractorWebdriverBiDi.innerProcess(new CrawlURI(UURIFactory.getInstance("https://www.abc.net.au/")));
        } finally {
            extractorWebdriverBiDi.stop();
        }
    }

    public ConfigPath getScratchDir() {
        return scratchDir;
    }

    public void setScratchDir(ConfigPath scratchDir) {
        this.scratchDir = scratchDir;
    }

    private class RecordingProxyHandler extends ProxyHandler.Forward {
        @Override
        protected void addProxyHeaders(Request clientToProxyRequest, org.eclipse.jetty.client.Request proxyToServerRequest) {
            String requestId = proxyToServerRequest.getHeaders().get(REQUEST_ID_HEADER);
            if (requestId != null) {
                var recorder = requestRecorders.get(requestId);
                if (recorder != null) {
                    clientToProxyRequest.setAttribute(RequestRecorder.class.getName(), recorder);
                    recorder.handleRequestHeader(proxyToServerRequest);
                }

                // Remove our own marker header
                proxyToServerRequest.headers(headers -> headers.remove(REQUEST_ID_HEADER));
            }

            // Don't call super to avoid adding the Via and Forwarded proxy headers as
            // the proxy shouldn't be visible to the server.
            super.addProxyHeaders(clientToProxyRequest, proxyToServerRequest);
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

    public class RequestRecorder {
        private final CrawlURI curi;
        private final Network.BeforeRequestSent beforeEvent;
        private org.eclipse.jetty.client.Request request;
        private org.eclipse.jetty.client.Response response;
        private final WritableByteChannel requestBodyChannel;
        private ByteBuffer responseBodyBuffer;
        private final byte[] buffer = new byte[8192];

        public RequestRecorder(CrawlURI curi, Network.BeforeRequestSent beforeEvent) {
            this.curi = curi;
            this.beforeEvent = beforeEvent;
            try {
                File responseTempFile = File.createTempFile("heritrix-bidi", ".tmp");
                responseTempFile.deleteOnExit();
                this.requestBodyChannel = Channels.newChannel(curi.getRecorder().outputWrap(null));
                curi.getRecorder().inputWrap(new InputStream() {
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
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public void handleRequestHeader(org.eclipse.jetty.client.Request request) {
            this.request = request;
            try {
                FetchHTTP2.recordRequest(request, curi.getRecorder());
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "Error recording request", e);
                throw new RuntimeException(e);
            }
        }

        public void handleResponseHeader(org.eclipse.jetty.client.Response response) {
            this.response = response;
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
                if (dispositionChain != null) dispositionChain.process(curi, null);
            } catch (Exception e) {
                log.log(System.Logger.Level.ERROR, "Error processing disposition", e);
            }
        }
    }
}
