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

import org.archive.modules.CrawlURI;
import org.archive.net.chrome.ChromeClient;
import org.archive.net.chrome.ChromeProcess;
import org.archive.net.chrome.ChromeWindow;
import org.json.JSONArray;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Extracts links using a web browser via the Chrome Devtools Protocol.
 * <p>
 * To use, first define this as a top-level bean:
 * <pre>
 * &lt;bean id=&quot;extractorChrome&quot; class=&quot;org.archive.modules.extractor.ExtractorChrome&quot;&gt;
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
     * The name or path to the browser executable. If null common locations will be searched.
     */
    private String executable = null;

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

    private Semaphore openWindowsSemaphore = null;
    private ChromeProcess process = null;
    private ChromeClient client = null;

    @Override
    protected boolean shouldExtract(CrawlURI uri) {
        return uri.getContentType().startsWith("text/html");
    }

    @Override
    protected boolean innerExtract(CrawlURI uri) {
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

    private void visit(CrawlURI uri) throws InterruptedException {
        try (ChromeWindow window = client.createWindow(windowWidth, windowHeight)) {
            try {
                window.navigateAsync(uri.getURI()).get(loadTimeoutSeconds, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new RuntimeException(e.getCause());
            } catch (TimeoutException e) {
                throw new RuntimeException("Timed out navigating to " + uri.getURI());
            }

            JSONArray links = window.eval("Array.from(document.querySelectorAll('a[href], area[href]'))" +
                    ".map(link => link.protocol + '//' + link.host + link.pathname + link.search + link.hash)")
                    .getJSONArray("value");
            for (int i = 0; i < links.length(); i++) {
                addOutlink(uri, links.getString(i), LinkContext.NAVLINK_MISC, Hop.NAVLINK);
            }
        }
    }

    @Override
    public void start() {
        if (isRunning) return;
        super.start();
        openWindowsSemaphore = new Semaphore(maxOpenWindows);
        if (devtoolsUrl != null) {
            client = new ChromeClient(devtoolsUrl);
        } else {
            try {
                process = new ChromeProcess(executable);
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
}