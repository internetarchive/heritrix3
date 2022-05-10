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

package org.archive.net.chrome;

import org.archive.modules.extractor.ExtractorChrome;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.logging.Level.FINER;

/**
 * Manages starting and stopping a browser process.
 */
public class ChromeProcess implements Closeable {
    private static final Logger logger = Logger.getLogger(ExtractorChrome.class.getName());

    private static final String[] DEFAULT_EXECUTABLES = {"chromium-browser", "chromium", "google-chrome",
            "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
            "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
            "firefox"};
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 2;

    private static final Set<Process> runningProcesses = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Thread shutdownHook;

    private final Process process;
    private final String devtoolsUrl;

    public ChromeProcess(String executable, List<String> commandLineOptions) throws IOException {
        process = executable == null ? launchAny(commandLineOptions) : launch(executable, commandLineOptions);
        runningProcesses.add(process);
        registerShutdownHook();
        devtoolsUrl = readDevtoolsUriFromStderr(process);
    }

    private static Process launch(String executable, List<String> commandLineOptions) throws IOException {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("--headless");
        command.add("--remote-debugging-port=0");

        // https://github.com/GoogleChrome/chrome-launcher/blob/master/docs/chrome-flags-for-tools.md
        command.add("--disable-background-networking");
        command.add("--disable-background-timer-throttling");
        command.add("--disable-backgrounding-occluded-windows");
        command.add("--disable-breakpad");
        command.add("--disable-client-side-phishing-detection");
        command.add("--disable-component-extensions-with-background-pages");
        command.add("--disable-component-update");
        command.add("--disable-crash-reporter");
        command.add("--disable-default-apps");
        command.add("--disable-extensions");
        command.add("--disable-features=Translate");
        command.add("--disable-ipc-flooding-protection");
        command.add("--disable-popup-blocking");
        command.add("--disable-prompt-on-repost");
        command.add("--disable-renderer-backgrounding");
        command.add("--disable-sync");
        command.add("--metrics-recording-only");
        command.add("--mute-audio");
        command.add("--no-default-browser-check");
        command.add("--no-first-run");
        command.add("--password-store=basic");
        command.add("--use-mock-keychain");

        command.addAll(commandLineOptions);
        return new ProcessBuilder(command)
                .inheritIO()
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
    }

    /**
     * Try to launch the browser process using each of DEFAUSLT_EXECUTABLES in turn until one succeeds.
     */
    private static Process launchAny(List<String> extraCommandLineOptions) throws IOException {
        IOException lastException = null;
        for (String executable : DEFAULT_EXECUTABLES) {
            try {
                return launch(executable, extraCommandLineOptions);
            } catch (IOException e) {
                lastException = e;
            }
        }
        throw new IOException("Failed to launch any of " + Arrays.asList(DEFAULT_EXECUTABLES), lastException);
    }

    @Override
    public void close() {
        destroyProcess(process);
        runningProcesses.remove(process);
    }

    /**
     * Register a shutdown hook that destroys all running browser processes before exiting in case stop() is never
     * called. This can happen if the Heritrix exits abnormally.
     */
    private static synchronized void registerShutdownHook() {
        if (shutdownHook != null) return;
        shutdownHook = new Thread(ChromeProcess::destroyAllRunningProcesses, "ChromiumClient shutdown hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private static void destroyAllRunningProcesses() {
        for (Process process : runningProcesses) {
            process.destroy();
        }
        for (Process process : runningProcesses) {
            try {
                if (!process.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    break;
                }
            } catch (InterruptedException e) {
                break;
            }
        }
        for (Process process : runningProcesses) {
            process.destroyForcibly();
        }
    }

    private static void destroyProcess(Process process) {
        process.destroy();
        try {
            process.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            process.destroyForcibly();
        }
    }

    /**
     * Reads the stderr of a Chromium process and returns the DevTools URI. Once this method
     * returns stderr will continue to be consumed and logged by a background thread.
     */
    private static String readDevtoolsUriFromStderr(Process process) throws IOException {
        BufferedReader stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), ISO_8859_1));
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            String listenMsg = "DevTools listening on ";
            try {
                while (true) {
                    String line = stderr.readLine();
                    if (line == null) break;
                    if (!future.isDone() && line.startsWith(listenMsg)) {
                        future.complete(line.substring(listenMsg.length()));
                    }
                    logger.log(FINER, "Chromium STDERR: {0}", line);
                }
            } catch (IOException e) {
                future.completeExceptionally(e);
            }
        });
        thread.setName("Chromium stderr reader");
        thread.setDaemon(true);
        thread.start();

        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // unwrap the exception if we can to cut down on log noise
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException(e);
        }
    }

    public String getDevtoolsUrl() {
        return devtoolsUrl;
    }
}
