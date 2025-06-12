package org.archive.crawler.processor;

import com.sun.net.httpserver.HttpServer;
import org.archive.crawler.framework.CrawlController;
import org.archive.modules.*;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.modules.fetcher.FetchHTTP2;
import org.archive.net.UURIFactory;
import org.archive.url.URIException;
import org.archive.util.Recorder;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static java.lang.System.Logger.Level.DEBUG;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "runBrowserTests", matches = "true")
class BrowserProcessorTest {
    private static final System.Logger logger = System.getLogger(BrowserProcessorTest.class.getName());

    private static HttpServer httpServer;
    private static FetchHTTP2 fetcher;
    private static BrowserProcessor browserProcessor;
    private static String baseUrl;
    private static ArrayList<CrawlURI> subrequests;
    private static CrawlController crawlController;
    private final Set<Recorder> recorders = new HashSet<>();
    @TempDir
    Path tempDir;

    @Test
    public void test() throws IOException, InterruptedException {
        CrawlURI crawlURI = newCrawlURI(baseUrl);
        fetcher.process(crawlURI);
        assertEquals(200, crawlURI.getFetchStatus());
        browserProcessor.innerProcess(crawlURI);

        var outLinks = new ArrayList<>(crawlURI.getOutLinks());
        assertEquals("/link", outLinks.get(0).getUURI().getPath());
        assertTrue(crawlURI.getAnnotations().contains("browser"));

        logger.log(DEBUG, "Subrequests: {0}", subrequests);
        var subrequestByPath = new HashMap<String,CrawlURI>();
        for (CrawlURI curi : subrequests) {
            subrequestByPath.put(curi.getUURI().getPath(), curi);
        }
        CrawlURI gzip = subrequestByPath.get("/gzip");
        assertNotNull(gzip);
        assertEquals("gzip", gzip.getRecorder().getContentEncoding());
        assertEquals("/*hello world*/", gzip.getRecorder().getContentReplayPrefixString(100));
    }

    @Test
    public void testDownload() throws IOException, InterruptedException {
        CrawlURI crawlURI = newCrawlURI(baseUrl + "download.bin");
        fetcher.process(crawlURI);
        assertEquals(200, crawlURI.getFetchStatus());
        assertFalse(browserProcessor.shouldProcess(crawlURI), "content-disposition header should skip processing");

        // force processing anyway to test the behavior for other download reasons (e.g. non-HTML)
        browserProcessor.innerProcess(crawlURI);
        assertFalse(crawlURI.getAnnotations().contains("browser"), "navigation should have aborted");
    }

    @Test
    public void testHttpProxy() throws Exception {
        InetAddress localhost = Inet4Address.getLoopbackAddress();
        Server proxyServer = new Server(new InetSocketAddress(localhost, 0));
        proxyServer.setHandler(new ProxyHandler.Forward());
        proxyServer.start();
        try {
            fetcher.setHttpProxyHost(localhost.getHostAddress());
            fetcher.setHttpProxyPort(((ServerConnector)proxyServer.getConnectors()[0]).getLocalPort());

            CrawlURI crawlURI = newCrawlURI(baseUrl);
            fetcher.process(crawlURI);
            assertEquals(200, crawlURI.getFetchStatus());
            browserProcessor.innerProcess(crawlURI);

            var outLinks = new ArrayList<>(crawlURI.getOutLinks());
            assertEquals("/link", outLinks.get(0).getUURI().getPath());
            assertTrue(crawlURI.getAnnotations().contains("browser"));
            assertEquals("true", crawlURI.getHttpResponseHeader("Used-Proxy"));
            assertEquals("true", subrequests.get(0).getHttpResponseHeader("Used-Proxy"));

            logger.log(DEBUG, "Subrequests: {0}", subrequests);
        } finally {
            proxyServer.stop();
        }
    }

    private CrawlURI newCrawlURI(String uri) throws URIException {
        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(uri));
        Recorder recorder = new Recorder(tempDir.toFile(), "fetcher");
        recorders.add(recorder);
        curi.setRecorder(recorder);
        return curi;
    }

    @BeforeEach
    void setUp() {
        crawlController.getScratchDir().setPath(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        subrequests.clear();
        for (Recorder recorder : recorders) {
            recorder.cleanup();
        }
    }

    @BeforeAll
    static void setUpAll() throws Exception {
        startHttpServer();
        startProcessors();
    }

    static void startHttpServer() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), -1);
        httpServer.createContext("/", exchange -> {
            logger.log(DEBUG, "Server received request: {0} {1}",  exchange.getRequestMethod(), exchange.getRequestURI());
            String contentType = "text/html";
            int status = 200;
            String body = "";
            switch (exchange.getRequestURI().getPath()) {
                case "/" -> body = "<link href=style.css rel=stylesheet><link href=gzip rel=stylesheet><a href=\"/link\">link</a><img src=img.jpg>";
                case "/style.css" -> {
                    body = "body { color: red; background: url(bg.jpg); }";
                    contentType = "text/css";
                }
                case "/download.bin" -> {
                    body = "sample-download-file";
                    exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=heritrix-test.bin");
                }
                case "/gzip" -> {
                    exchange.getResponseHeaders().add("Content-Encoding", "gzip");
                    exchange.sendResponseHeaders(200, 0);
                    var gzip = new GZIPOutputStream(exchange.getResponseBody());
                    gzip.write("/*hello world*/".getBytes());
                    gzip.close();
                    exchange.close();
                    return;
                }
                default -> status = 404;
            }
            if (exchange.getRequestHeaders().containsKey("Via")) {
                exchange.getResponseHeaders().add("Used-Proxy", "true");
            }
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, 0);
            exchange.getResponseBody().write(body.getBytes());
            exchange.close();
        });
        httpServer.start();
        baseUrl = "http://" + httpServer.getAddress().getAddress().getHostAddress() + ":" +
                     httpServer.getAddress().getPort() + "/";
    }

    static void startProcessors() {
        fetcher = new FetchHTTP2(new DefaultServerCache(), null);
        fetcher.setUserAgentProvider(new CrawlMetadata());
        fetcher.start();
        crawlController = new CrawlController();
        FetchChain fetchChain = new FetchChain();
        fetchChain.setProcessors(List.of());
        crawlController.setFetchChain(fetchChain);

        subrequests = new ArrayList<>();

        DispositionChain dispositionChain = new DispositionChain();
        dispositionChain.setProcessors(List.of(new Processor() {
            @Override
            protected boolean shouldProcess(CrawlURI uri) {
                return true;
            }

            @Override
            protected void innerProcess(CrawlURI uri) {
                subrequests.add(uri);
            }
        }));
        crawlController.setDispositionChain(dispositionChain);
        browserProcessor = new BrowserProcessor(fetcher, crawlController, event -> {}, null);
            browserProcessor.start();
    }

    @AfterAll
    static void tearDownAll() {
        if (httpServer != null) httpServer.stop(0);
        if (browserProcessor != null) browserProcessor.stop();
        if (fetcher != null) fetcher.stop();
    }

}