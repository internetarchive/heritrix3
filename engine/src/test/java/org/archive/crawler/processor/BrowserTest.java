package org.archive.crawler.processor;

import com.sun.net.httpserver.HttpServer;
import org.archive.crawler.framework.CrawlController;
import org.archive.modules.*;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.modules.fetcher.FetchHTTP2;
import org.archive.net.UURIFactory;
import org.archive.util.Recorder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.lang.System.Logger.Level.DEBUG;
import static org.junit.jupiter.api.Assertions.*;

class BrowserTest {
    private static final System.Logger logger = System.getLogger(BrowserTest.class.getName());

    private static HttpServer httpServer;

    @TempDir
    Path tempDir;

    @Test
    @EnabledIfSystemProperty(named = "runBrowserTests", matches = "true")
    public void test() throws IOException, InterruptedException {
        String url = "http://" + httpServer.getAddress().getAddress().getHostAddress() + ":" +
                httpServer.getAddress().getPort() + "/";
        var fetcher = new FetchHTTP2(new DefaultServerCache(), null);
        fetcher.setUserAgentProvider(new CrawlMetadata());
        fetcher.start();
        try {
            var crawlController = new CrawlController();
            FetchChain fetchChain = new FetchChain();
            fetchChain.setProcessors(List.of());
            crawlController.setFetchChain(fetchChain);

            var subrequests = new ArrayList<CrawlURI>();

            DispositionChain dispositionChain = new DispositionChain();
            dispositionChain.setProcessors(List.of(new Processor() {
                @Override
                protected boolean shouldProcess(CrawlURI uri) {
                    return true;
                }

                @Override
                protected void innerProcess(CrawlURI uri) throws InterruptedException {
                    subrequests.add(uri);
                }
            }));
            crawlController.setDispositionChain(dispositionChain);
            crawlController.getScratchDir().setPath(tempDir.toString());
            var browserProcessor = new Browser(fetcher, crawlController, event -> {}, null);
            try {
                browserProcessor.start();

                CrawlURI crawlURI = new CrawlURI(UURIFactory.getInstance(url));
                crawlURI.setRecorder(new Recorder(tempDir.toFile(), "fetcher"));
                fetcher.process(crawlURI);
                assertEquals(200, crawlURI.getFetchStatus());
                browserProcessor.innerProcess(crawlURI);

                var outLinks = new ArrayList<>(crawlURI.getOutLinks());
                assertEquals("/link", outLinks.get(0).getUURI().getPath());
                assertTrue(crawlURI.getAnnotations().contains("browser"));

                logger.log(DEBUG, "Subrequests: {0}", subrequests);
            } finally {
                browserProcessor.stop();
            }
        } finally {
            fetcher.stop();
        }
    }

    @BeforeAll
    static void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), -1);
        httpServer.createContext("/", exchange -> {
            logger.log(DEBUG, "Server received request: {0} {1}",  exchange.getRequestMethod(), exchange.getRequestURI());

            String contentType = "text/html";
            int status = 200;
            String body = "";
            switch (exchange.getRequestURI().getPath()) {
                case "/" -> {
                    body = "<link href=style.css rel=stylesheet><a href=\"/link\">link</a><img src=img.jpg>";
                }
                case "/style.css" -> {
                    body = "body { color: red; background: url(bg.jpg); }";
                    contentType = "text/css";
                }
                default -> status = 404;
            }
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, 0);
            exchange.getResponseBody().write(body.getBytes());
            exchange.close();
        });
        httpServer.start();
    }

    @AfterAll
    static void stopHttpServer() {
        if (httpServer != null) httpServer.stop(0);
    }

}