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

package org.archive.modules.extractor;

import org.apache.commons.io.IOUtils;
import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Frontier;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorChain;
import org.archive.net.chrome.*;
import org.archive.spring.KeyedProperties;
import org.archive.util.Recorder;
import org.archive.util.UriUtils;
import org.json.JSONArray;
import org.springframework.context.ApplicationEventPublisher;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Collections.enumeration;
import static java.util.logging.Level.*;
import static java.util.regex.Pattern.CASE_INSENSITIVE;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.FAILED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.SUCCEEDED;
import static org.archive.modules.CoreAttributeConstants.A_HTTP_RESPONSE_HEADERS;
import static org.archive.modules.CrawlURI.FetchType.*;

/**
 * Extracts links using a web browser via the Chrome Devtools Protocol.
 * <p>
 * To use, first define this as a top-level bean:
 * <pre>
 * &lt;bean id=&quot;extractorChrome&quot; class=&quot;org.archive.modules.extractor.ExtractorChrome&quot;&gt;
 *   &lt;!-- &lt;property name=&quot;captureRequests&quot; value=&quot;true&quot; /&gt; --&gt;
 *   &lt;!-- &lt;property name=&quot;devtoolsUrl&quot; value=&quot;ws://127.0.0.1:1234/devtools/browser/2bc831e8-6c02-4c9b-affd-14c93b8579d7&quot; /&gt; --&gt;
 *   &lt;!-- &lt;property name=&quot;executable&quot; value=&quot;chromium-browser&quot; /&gt; --&gt;
 *   &lt;!-- &lt;property name=&quot;loadTimeoutSeconds&quot; value=&quot;30&quot; /&gt; --&gt;
 *   &lt;!-- &lt;property name=&quot;maxOpenWindows&quot; value=&quot;16&quot; /&gt; --&gt;
 *   &lt;!-- &lt;property name=&quot;windowWidth&quot; value=&quot;1366&quot; /&gt; --&gt;
 *   &lt;!-- &lt;property name=&quot;windowWidth&quot; value=&quot;768&quot; /&gt; --&gt;
 * &lt;/bean&gt;
 * </pre>
 * Then add <code>&lt;ref bean="extractorChrome"/&gt;</code> to the fetch chain before <code>extractorHTML</code>.
 * <p>
 * By default an instance of the browser will be run as a subprocess for the duration of the crawl. Alternatively set
 * <code>devtoolsUrl</code> to connect to an existing instance of the browser (run with
 * <code>--headless --remote-debugging-port=1234</code>).
 */
public class ExtractorChrome extends ContentExtractor {
    private static final Logger logger = Logger.getLogger(ExtractorChrome.class.getName());
    private static final AtomicLong nextRecorderId = new AtomicLong();

    private static final Pattern TRANSFER_ENCODING_RE = Pattern.compile("\r\nTransfer-Encoding:[^\n\r]+", CASE_INSENSITIVE);

    /**
     * The maximum number of browser windows that are allowed to be opened simultaneously. Feel free to increase this
     * if you have lots of RAM available.
     */
    private int maxOpenWindows = 16;

    /**
     * URL of the devtools server to connect. If null a new browser process will be launched.
     */
    private String devtoolsUrl = null;

    /**
     * The name or path to the browser executable. If null common locations will be searched. Not used if devtoolsUrl
     * is set.
     */
    private String executable = null;

    /**
     * Extra command-line options passed to the browser process. Not used if devtoolsUrl is null.
     */
    private List<String> commandLineOptions = new ArrayList<>();

    /**
     * Width of the browser window.
     */
    private int windowWidth = 1366;

    /**
     * Height of the browser window.
     */
    private int windowHeight = 768;

    /**
     * Number of seconds to wait for the page to load.
     */
    private int loadTimeoutSeconds = 30;

    /**
     * Capture requests made by the browser.
     */
    private boolean captureRequests = true;

    /**
     * The maximum size response body that can be replayed to the browser. Setting this to -1 will cause all requests by
     * the browser to be made against the live web.
     */
    private int maxReplayLength = 100 * 1024 * 1024;

    private Semaphore openWindowsSemaphore = null;
    private ChromeProcess process = null;
    private ChromeClient client = null;

    private final CrawlController controller;
    private final ApplicationEventPublisher eventPublisher;
    private ProcessorChain extractorChain;

    public ExtractorChrome(CrawlController controller, ApplicationEventPublisher eventPublisher) {
        this.controller = controller;
        this.eventPublisher = eventPublisher;
    }

    @Override
    protected boolean shouldExtract(CrawlURI uri) {
        return uri.getContentType().startsWith("text/html") && uri.is2XXSuccess();
    }

    @Override
    protected boolean innerExtract(CrawlURI uri) {
        ensureConnected();
        try {
            openWindowsSemaphore.acquire();
            try {
                visit(uri);
            } finally {
                openWindowsSemaphore.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private void visit(CrawlURI curi) throws InterruptedException {
        try (ChromeWindow window = client.createWindow(windowWidth, windowHeight)) {
            window.interceptRequests(request -> handleInterceptedRequest(curi, request));

            if (captureRequests) {
                window.captureRequests(request -> handleCapturedRequest(curi, request));
            }

            try {
                window.navigateAsync(curi.getURI()).get(loadTimeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out navigating to " + curi.getURI());
            }

            JSONArray links = window.eval("Array.from(document.querySelectorAll('a[href], area[href]'))" +
                    ".map(link => link.protocol + '//' + link.host + link.pathname + link.search + link.hash)")
                    .getJSONArray("value");
            for (int i = 0; i < links.length(); i++) {
                addOutlink(curi, links.getString(i), LinkContext.NAVLINK_MISC, Hop.NAVLINK);
            }
        }
    }

    private void handleInterceptedRequest(CrawlURI curi, InterceptedRequest interceptedRequest) {
        ChromeRequest request = interceptedRequest.getRequest();
        if (request.getMethod().equals("GET") && request.getUrl().equals(curi.getURI())) {
            replayResponseToBrowser(curi, interceptedRequest);
        } else {
            interceptedRequest.continueNormally();
        }
    }

    @SuppressWarnings("unchecked")
    private void replayResponseToBrowser(CrawlURI curi, InterceptedRequest interceptedRequest) {
        // There seems to be no easy way to stream the body to the browser so we slurp it into
        // memory with a size limit. The one way I can see to achieve streaming is to have Heritrix
        // serve the request over its HTTP server and pass a Heritrix URL to Fetch.fulfillRequest
        // instead of the body directly. We might need to do that if memory pressure becomes a
        // problem but for now just keep it simple.

        long bodyLength = curi.getRecorder().getResponseContentLength();
        if (bodyLength > maxReplayLength) {
            logger.log(FINE, "Page body too large to replay: {0}", curi.getURI());
            interceptedRequest.continueNormally();
            return;
        }

        byte[] body = new byte[(int)bodyLength];
        try (InputStream stream = curi.getRecorder().getContentReplayInputStream()) {
            IOUtils.readFully(stream, body);
        } catch (IOException e) {
            logger.log(WARNING, "Error reading back page body: " + curi.getURI(), e);
            interceptedRequest.continueNormally();
            return;
        }

        Map<String,String> headers = (Map<String, String>) curi.getData().get(A_HTTP_RESPONSE_HEADERS);
        if (headers == null) {
            logger.log(WARNING, "Response headers unavailable in CrawlURI. Letting the browser " +
                    "refetch {0}", curi.getURI());
            interceptedRequest.continueNormally();
            return;
        }
        interceptedRequest.fulfill(curi.getFetchStatus(), headers.entrySet(), body);
    }

    private void handleCapturedRequest(CrawlURI via, ChromeRequest request) {
        if (request.isResponseFulfilledByInterception() || UriUtils.isDataUri(request.getUrl())) {
            return;
        }

        String recorderBaseName = "ExtractorChrome-" + nextRecorderId.getAndIncrement();
        Recorder recorder = new Recorder(new File(controller.getScratchDir().getFile(), recorderBaseName),
                controller.getRecorderOutBufferBytes(), controller.getRecorderInBufferBytes());
        try {
            String digestAlgorithm = "sha1";
            recorder.getRecordedInput().setDigest(digestAlgorithm);
            recorder.getRecordedOutput().write(request.getRequestHeader().getBytes(US_ASCII));
            recorder.getRecordedOutput().write(request.getRequestBody());

            // strip the Transfer-Encoding header since we only have access to the decoded body
            String responseHeader = TRANSFER_ENCODING_RE.matcher(request.getResponseHeader()).replaceAll("");

            recorder.inputWrap(new SequenceInputStream(enumeration(Arrays.asList(
                    new ByteArrayInputStream(responseHeader.getBytes(US_ASCII)),
                    new InputStream() {
                        public int read() {
                            recorder.markContentBegin();
                            return -1;
                        }
                    },
                    new ByteArrayInputStream(request.getResponseBody())))));
            recorder.getRecordedInput().readFully();
            recorder.closeRecorders();

            CrawlURI curi = via.createCrawlURI(request.getUrl(), LinkContext.EMBED_MISC, Hop.EMBED);
            curi.getAnnotations().add("browser");
            curi.setContentDigest(digestAlgorithm, recorder.getRecordedInput().getDigestValue());
            curi.setContentSize(recorder.getRecordedInput().getSize());
            curi.setContentType(request.getResponseContentType());
            curi.setFetchBeginTime(request.getBeginTime());
            curi.setFetchCompletedTime(System.currentTimeMillis());
            curi.setFetchStatus(request.getStatus());
            curi.setRecorder(recorder);
            curi.setServerIP(request.getRemoteIPAddress());
            curi.setThreadNumber(via.getThreadNumber());

            switch (request.getMethod()) {
                case "GET":
                    curi.setFetchType(HTTP_GET);
                    break;
                case "POST":
                    curi.setFetchType(HTTP_POST);
                    break;
                default:
                    curi.setFetchType(UNKNOWN);
                    break;
            }

            Frontier frontier = controller.getFrontier();
            curi.getOverlayNames(); // for side-effect of creating the overlayNames list

            // inform the frontier we've already seen this uri so it won't schedule it
            // we only do this for GETs so a POST doesn't prevent scheduling a GET of the same URI
            if (request.getMethod().equals("GET")) {
                frontier.considerIncluded(curi);
            }

            KeyedProperties.loadOverridesFrom(curi);
            try {
                // perform link extraction
                extractorChain.process(curi, null);

                // send the result to the disposition chain to dispatch outlinks and write warcs
                frontier.beginDisposition(curi);
                controller.getDispositionChain().process(curi,null);
            } finally {
                KeyedProperties.clearOverridesFrom(curi);
            }

            curi.aboutToLog();
            controller.getLoggerModule().getUriProcessing().log(INFO, curi.getUURI().toString(), curi);

            if (curi.isSuccess()) {
                eventPublisher.publishEvent(new CrawlURIDispositionEvent(this, curi, SUCCEEDED));
            } else {
                eventPublisher.publishEvent(new CrawlURIDispositionEvent(this, curi, FAILED));
            }
            frontier.endDisposition();
        } catch (Exception e) {
            logger.log(WARNING, "Exception handling subrequest " + request.getUrl(), e);
        } finally {
            recorder.cleanup();
        }
    }

    @Override
    public void start() {
        if (isRunning) return;
        super.start();
        openWindowsSemaphore = new Semaphore(maxOpenWindows);

        // If we're enabled by default launch the browser now to get early feedback if there's a connection error.
        // Otherwise, we launch it on demand so that we don't create browser processes for jobs that don't need it.
        if (getEnabled()) {
            ensureConnected();
        }

        if (extractorChain == null) {
            // The fetch chain normally includes some preprocessing, fetch and extractor processors, but we want just
            // the extractors as we let the browser fetch subresources. So we construct a new chain consisting of the
            // extractors only.
            List<Processor> extractors = new ArrayList<>();
            for (Processor processor : controller.getFetchChain().getProcessors()) {
                if (processor instanceof Extractor) {
                    extractors.add(processor);
                }
            }
            extractorChain = new ProcessorChain();
            extractorChain.setProcessors(extractors);
        }
    }

    private synchronized void ensureConnected() {
        if (client != null) return;
        if (devtoolsUrl != null) {
            client = new ChromeClient(devtoolsUrl);
        } else {
            try {
                process = new ChromeProcess(executable, commandLineOptions);
            } catch (IOException e) {
                throw new RuntimeException("Failed to launch browser process", e);
            }
            client = new ChromeClient(process.getDevtoolsUrl());
        }
    }

    @Override
    public void stop() {
        super.stop();
        if (client != null) {
            client.close();
            client = null;
        }
        if (process != null) {
            process.close();
            process = null;
        }
    }

    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public int getMaxOpenWindows() {
        return maxOpenWindows;
    }

    public void setMaxOpenWindows(int maxOpenWindows) {
        this.maxOpenWindows = maxOpenWindows;
    }

    public String getDevtoolsUrl() {
        return devtoolsUrl;
    }

    public void setDevtoolsUrl(String devtoolsUrl) {
        this.devtoolsUrl = devtoolsUrl;
    }

    public int getWindowWidth() {
        return windowWidth;
    }

    public void setWindowWidth(int windowWidth) {
        this.windowWidth = windowWidth;
    }

    public int getWindowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int windowHeight) {
        this.windowHeight = windowHeight;
    }

    public int getLoadTimeoutSeconds() {
        return loadTimeoutSeconds;
    }

    public void setLoadTimeoutSeconds(int loadTimeoutSeconds) {
        this.loadTimeoutSeconds = loadTimeoutSeconds;
    }

    public List<String> getCommandLineOptions() {
        return commandLineOptions;
    }

    public void setCommandLineOptions(List<String> commandLineOptions) {
        this.commandLineOptions = commandLineOptions;
    }
}