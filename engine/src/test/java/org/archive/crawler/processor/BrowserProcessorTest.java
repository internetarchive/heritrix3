package org.archive.crawler.processor;

import com.sun.net.httpserver.HttpServer;
import org.archive.crawler.framework.CrawlController;
import org.archive.modules.*;
import org.archive.modules.behaviors.PaginationBehavior;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import static java.lang.System.Logger.Level.DEBUG;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "runBrowserTests", matches = "true")
class BrowserProcessorTest {
    private static final System.Logger logger = System.getLogger(BrowserProcessorTest.class.getName());
    private static final String JSON_POST_BODY = "{\"event\":\"browser-subresource\",\"count\":3}";

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

        String expectedUserAgent = fetcher.getUserAgentProvider().getUserAgent();

        var outLinks = new ArrayList<>(crawlURI.getOutLinks());
        assertEquals("/link", outLinks.get(0).getUURI().getPath());
        assertTrue(crawlURI.getAnnotations().contains("browser"));
        assertEquals(expectedUserAgent, crawlURI.getHttpResponseHeader("Reflected-User-Agent"));
        assertNotNull(crawlURI.getContentDigest());

        logger.log(DEBUG, "Subrequests: {0}", subrequests);
        var subrequestByPath = new HashMap<String,CrawlURI>();
        for (CrawlURI curi : subrequests) {
            subrequestByPath.put(curi.getUURI().getPath(), curi);
        }
        CrawlURI gzip = subrequestByPath.get("/gzip");
        assertNotNull(gzip);
        assertEquals("gzip", gzip.getRecorder().getContentEncoding());
        assertEquals("/*hello world*/", gzip.getRecorder().getContentReplayPrefixString(100));

        CrawlURI postJson = subrequestByPath.get("/post-json");
        assertNotNull(postJson);
        String postRequest = httpRequestString(postJson);
        assertTrue(postRequest.startsWith("POST /post-json HTTP/1.1\r\n"), postRequest);
        assertTrue(postRequest.toLowerCase(Locale.ROOT).contains("content-type: application/json\r\n"), postRequest);
        assertTrue(postRequest.endsWith("\r\n\r\n" + JSON_POST_BODY), postRequest);
        assertEquals(expectedUserAgent, postJson.getHttpResponseHeader("Reflected-User-Agent"));
        assertNotNull(postJson.getContentDigest());
    }

    @Test
    public void testPaginationBehavior() throws IOException, InterruptedException {
        var defaultBehaviors = browserProcessor.getBehaviors();
        browserProcessor.setBehaviors(List.of(new PaginationBehavior(null)));
        try {
            CrawlURI crawlURI = newCrawlURI(baseUrl + "paginated");
            fetcher.process(crawlURI);
            assertEquals(200, crawlURI.getFetchStatus());
            browserProcessor.innerProcess(crawlURI);

            var paths = new HashSet<String>();
            for (CrawlURI link : crawlURI.getOutLinks()) {
                paths.add(link.getUURI().getPath());
            }
            assertEquals(Set.of("/item1", "/item2", "/item3"), paths,
                    "should have extracted the links from every page of the pagination");
        } finally {
            browserProcessor.setBehaviors(defaultBehaviors);
        }
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
            var subrequestByPath = new HashMap<String,CrawlURI>();
            for (CrawlURI curi : subrequests) {
                subrequestByPath.put(curi.getUURI().getPath(), curi);
            }
            assertEquals("true", subrequestByPath.get("/style.css").getHttpResponseHeader("Used-Proxy"));

            logger.log(DEBUG, "Subrequests: {0}", subrequests);
        } finally {
            fetcher.getKeyedProperties().remove("httpProxyHost");
            fetcher.getKeyedProperties().remove("httpProxyPort");
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

    private static String httpRequestString(CrawlURI curi) throws IOException {
        return new String(curi.getRecorder().getRecordedOutput().getReplayInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
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
            String userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            if (userAgent != null) exchange.getResponseHeaders().add("Reflected-User-Agent", userAgent);
            String contentType = "text/html";
            int status = 200;
            String body = "";
            switch (exchange.getRequestURI().getPath()) {
                case "/" -> body = """
                        <link href=style.css rel=stylesheet>
                        <link href=gzip rel=stylesheet>
                        <a href="/link">link</a>
                        <img src=img.jpg>
                        <script>
                            fetch('/post-json', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/json'},
                                body: '%s'
                            });
                        </script>
                        """.formatted(JSON_POST_BODY);
                case "/style.css" -> {
                    body = "body { color: red; background: url(bg.jpg); }";
                    contentType = "text/css";
                }
                case "/post-json" -> {
                    assertEquals("POST", exchange.getRequestMethod());
                    assertEquals(JSON_POST_BODY, new String(exchange.getRequestBody().readAllBytes(),
                            StandardCharsets.UTF_8));
                    body = "{\"ok\":true}";
                    contentType = "application/json";
                }
                case "/paginated" -> body = """
                        <div id=items><a href="/item1">item</a></div>
                        <button id=next title="Next" onclick="nextPage()">Next</button>
                        <script>
                            let page = 1;
                            function nextPage() {
                                page++;
                                document.getElementById('items').innerHTML =
                                    '<a href="/item' + page + '">item</a>';
                                if (page >= 3) document.getElementById('next').disabled = true;
                            }
                        </script>""";
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
