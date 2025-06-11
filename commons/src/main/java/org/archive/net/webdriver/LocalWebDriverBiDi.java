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

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.lang.StringUtils;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

import java.io.*;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Client for the BiDirectional WebDriver Protocol for remotely controlling browsers.
 *
 * @see <a href="https://www.w3.org/TR/webdriver-bidi/>WebDriver BiDi specification</a>
 */
public class LocalWebDriverBiDi implements WebDriverBiDi, Closeable {
    private final System.Logger logger = System.getLogger(getClass().getName());
    private final Process process;
    private final CompletableFuture<String> webSocketUrl = new CompletableFuture<>();
    private final WebSocket webSocket;
    private final AtomicLong requestId = new AtomicLong(0);
    private final Map<Long, CompletableFuture<JSONObject>> commands = new ConcurrentHashMap<>();
    private final Map<String, Consumer<JSONObject>> eventHandlers = new ConcurrentHashMap<>();
    private final ExecutorService eventExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
            .setNameFormat("ClientBiDi-event-%d")
            .setDaemon(true)
            .build());
    private final String sessionId;
    private static final String[] BROWSERS = new String[]{
            "firefox", "/Applications/Firefox.app/Contents/MacOS/firefox", "chromedriver"};

    public LocalWebDriverBiDi(String executable, List<String> options, Session.CapabilitiesRequest capabilities, Path profileDir)
            throws ExecutionException, InterruptedException, IOException {
        this.process = executable == null ? launchAnyBrowser(options, profileDir) : launchBrowser(executable, options, profileDir);
        Runtime.getRuntime().addShutdownHook(new Thread(this.process::destroyForcibly));
        new Thread(this::handleStderr).start();
        webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(webSocketUrl.get() + "/session"), new Listener())
                .get();
        sessionId = session().new_(capabilities).sessionId();
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

    private void handleStderr() {
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

    public CompletableFuture<JSONObject> send(String method, Map<String, Object> params) {
        long id = requestId.incrementAndGet();
        var command = new JSONObject();
        command.put("id", id);
        command.put("method", method);
        if (sessionId != null) command.put("sessionId", sessionId);
        command.put("params", BiDiJson.toJson(params));
        String message = JSONWriter.valueToString(command);
        logger.log(System.Logger.Level.TRACE, "BiDi SEND {0}", message);
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        commands.put(id, future);
        webSocket.sendText(message, true);
        return future;
    }

    public <T extends BiDiEvent> void on(Class<T> eventClass, Consumer<T> handler) {
        String eventName = StringUtils.uncapitalize(eventClass.getEnclosingClass().getSimpleName()) + "." +
                StringUtils.uncapitalize(eventClass.getSimpleName());
        eventHandlers.put(eventName, node -> {
            handler.accept(BiDiJson.fromJson(node, eventClass));
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BiDiModule> T module(Class<T> moduleClass) {
        String prefix = moduleClass.getSimpleName().substring(0, 1).toLowerCase(Locale.ROOT)
                + moduleClass.getSimpleName().substring(1) + ".";
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{moduleClass},
                (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> moduleClass.getName();
                        default -> {
                            var map = new LinkedHashMap<String, Object>();
                            if (args != null) {
                                for (int i = 0; i < args.length; i++) {
                                    map.put(method.getParameters()[i].getName(), args[i]);
                                }
                            }
                            String methodName = method.getName();
                            if (methodName.endsWith("_")) methodName = methodName.substring(0, methodName.length() - 1);
                            if (methodName.endsWith("Async"))
                                methodName = methodName.substring(0, methodName.length() - 5);
                            var future = send(prefix + methodName, map);
                            if (method.getReturnType() == CompletableFuture.class &&
                                    method.getGenericReturnType() instanceof ParameterizedType parameterizedType &&
                                    parameterizedType.getActualTypeArguments()[0] instanceof Class<?> typeClass) {
                                yield future.thenApply(result -> BiDiJson.fromJson(result, typeClass));
                            } else {
                                int timeout = 30;
                                try {
                                    yield BiDiJson.fromJson(future.get(timeout, TimeUnit.SECONDS), method.getReturnType());
                                } catch (ExecutionException e) {
                                    if (e.getCause() instanceof RuntimeException re) throw re;
                                    throw e;
                                } catch (TimeoutException e) {
                                    throw new WebDriverTimeoutException(prefix + methodName + " took more than " + timeout + " seconds");
                                }
                            }
                        }
                    };
                });
    }

    @Override
    public void close() throws IOException {
        try {
            if (process.isAlive() && !webSocket.isInputClosed()) {
                browser().close();
            }
        } catch (Exception e) {
            logger.log(System.Logger.Level.ERROR, "Error closing browser", e);
        }
        process.destroy();
    }

    private class Listener implements WebSocket.Listener {
        StringBuffer buffer = new StringBuffer();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1L);
            buffer.append(data);
            if (!last) {
                return null;
            }
            data = buffer;
            buffer = new StringBuffer();
            logger.log(System.Logger.Level.TRACE, "BiDi RECV {0}", data);
            try {
                var message = new JSONObject(new JSONTokener(new CharSequenceReader(data)));
                switch (message.getString("type")) {
                    case "success" -> {
                        var future = commands.remove(message.getLong("id"));
                        if (future != null) future.complete(message.getJSONObject("result"));
                    }
                    case "error" -> {
                        var future = commands.remove(message.getLong("id"));
                        if (future != null)
                            future.completeExceptionally(new WebDriverException(message.getString("message")));
                    }
                    case "event" -> {
                        var handler = eventHandlers.get(message.getString("method"));
                        if (handler != null) {
                            JSONObject params = message.getJSONObject("params");
                            eventExecutor.execute(() -> handler.accept(params));
                        }
                    }
                    default ->
                            throw new UnsupportedOperationException("Unsupported message type: " + message.getString("type"));
                }
            } catch (Exception e) {
                logger.log(System.Logger.Level.ERROR, "Error handling message", e);
                webSocket.abort();
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            logger.log(System.Logger.Level.ERROR, "WebSocket error", error);
        }
    }
}
