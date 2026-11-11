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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A running browser (or ChromeDriver) process and the WebDriver BiDi WebSocket URL it reports in its output.
 */
public class BrowserProcess {
    private static final System.Logger logger = System.getLogger(BrowserProcess.class.getName());
    private static final int LAUNCH_TIMEOUT_SECONDS = 60;
    private static final String[] BROWSERS = new String[]{
            "firefox", "/Applications/Firefox.app/Contents/MacOS/firefox", "chromedriver"};
    private final Process process;
    private final Thread shutdownHook;
    private final CompletableFuture<String> webSocketUrl = new CompletableFuture<>();

    /**
     * Takes ownership of a started process whose stderr is redirected to its stdout.
     */
    BrowserProcess(Process process) {
        this.process = process;
        shutdownHook = new Thread(process::destroyForcibly);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        var outputThread = new Thread(this::handleOutput, "BrowserProcess-output");
        outputThread.setDaemon(true);
        outputThread.start();
    }

    /**
     * Launches a browser on this machine. If executable is null tries several common paths.
     */
    public static BrowserProcess launchLocal(String executable, List<String> options, Path profileDir)
            throws IOException {
        return new BrowserProcess(executable == null ? launchAnyBrowser(options, profileDir)
                : launchBrowser(executable, options, profileDir));
    }

    private static Process launchAnyBrowser(List<String> options, Path profileDir) throws IOException {
        IOException lastException = null;
        for (String executable : BROWSERS) {
            try {
                return launchBrowser(executable, options, profileDir);
            } catch (IOException e) {
                lastException = e;
            }
        }
        throw new IOException("Failed to launch any of: " + List.of(BROWSERS), lastException);
    }

    private static Process launchBrowser(String executable, List<String> options, Path profileDir) throws IOException {
        var command = new ArrayList<String>();
        command.add(executable);
        command.add("--remote-debugging-port=0");
        if (executable.contains("firefox")) {
            command.add("--profile");
            command.add(profileDir.toAbsolutePath().toString());
        } else {
            command.add("--user-data-dir=" + profileDir.toAbsolutePath());
        }
        command.addAll(options);
        return new ProcessBuilder(command)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectErrorStream(true)
                .start();
    }

    private void handleOutput() {
        String firefoxListening = "WebDriver BiDi listening on ";
        String chromeDriverListening = "ChromeDriver was started successfully on port ";
        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith(firefoxListening)) {
                    webSocketUrl.complete(line.substring(firefoxListening.length()));
                } else if (line.startsWith(chromeDriverListening)) {
                    webSocketUrl.complete("ws://127.0.0.1:" + line.substring(chromeDriverListening.length(), line.length() - 1));
                } else if (line.startsWith("DevTools listening on ")) {
                    webSocketUrl.completeExceptionally(new WebDriverException("Chrome does not directly implement WebDriver BiDi. Please run ChromeDriver instead of running Chrome directly."));
                } else if (line.contains("Missing response info, network.responseCompleted will be skipped for URL:")) {
                    continue; // suppress noisy Firefox logs
                }
                logger.log(System.Logger.Level.INFO, "Browser: {0}", line);
            }
            webSocketUrl.completeExceptionally(new EOFException("EOF waiting for start message"));
        } catch (Exception e) {
            webSocketUrl.completeExceptionally(e);
        }
    }

    /**
     * Waits for the browser to report the URL of its WebDriver BiDi endpoint.
     */
    public String awaitWebSocketUrl() throws ExecutionException, InterruptedException, IOException {
        try {
            return webSocketUrl.get(LAUNCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IOException("Browser did not start within " + LAUNCH_TIMEOUT_SECONDS + " seconds", e);
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public void destroyForcibly() {
        // ChromeDriver's browser is its child and would survive it, keeping the profile locked
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        removeShutdownHook();
    }

    /**
     * Asks the process to exit, killing it if it hasn't after ten seconds.
     */
    public void close() {
        List<ProcessHandle> descendants = process.descendants().toList();
        process.destroy();
        try {
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            // ChromeDriver doesn't wait for the browser it launched to exit
            CompletableFuture.allOf(descendants.stream().map(ProcessHandle::onExit)
                    .toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException e) {
            descendants.forEach(ProcessHandle::destroyForcibly);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            descendants.forEach(ProcessHandle::destroyForcibly);
            Thread.currentThread().interrupt();
        }
        removeShutdownHook();
    }

    private void removeShutdownHook() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM is already shutting down
        }
    }
}
