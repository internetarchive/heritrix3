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

package org.archive.net.webdriver;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * WebDriver BiDi client for a browser process we launched and own.
 */
public class LocalWebDriverBiDi extends AbstractWebDriverBiDi {
    private final BrowserProcess process;

    public LocalWebDriverBiDi(String executable, List<String> options, Session.CapabilitiesRequest capabilities, Path profileDir)
            throws ExecutionException, InterruptedException, IOException {
        this(BrowserProcess.launchLocal(executable, options, profileDir), capabilities);
    }

    /**
     * Connects to a launched browser and starts a session. Kills the browser if that fails.
     */
    public LocalWebDriverBiDi(BrowserProcess process, Session.CapabilitiesRequest capabilities)
            throws ExecutionException, InterruptedException, IOException {
        this.process = process;
        try {
            String url = process.awaitWebSocketUrl();
            connect(HttpClient.newHttpClient().newWebSocketBuilder(), URI.create(url + "/session"));
            setSessionId(session().newAsync(capabilities).get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS).sessionId());
        } catch (TimeoutException e) {
            process.destroyForcibly();
            throw new IOException("Browser did not start within " + CONNECT_TIMEOUT_SECONDS + " seconds", e);
        } catch (Exception e) {
            process.destroyForcibly();
            throw e;
        }
    }

    @Override
    public boolean isAlive() {
        return process.isAlive() && super.isAlive();
    }

    @Override
    protected void destroy() {
        process.destroyForcibly();
    }

    @Override
    public void close() throws IOException {
        try {
            if (isAlive()) {
                browser().close();
            }
        } catch (Exception e) {
            logger.log(System.Logger.Level.ERROR, "Error closing browser", e);
        }
        process.close();
        super.close();
    }
}
