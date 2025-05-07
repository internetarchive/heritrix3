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

    public LocalWebDriverBiDi(int proxyPort) throws ExecutionException, InterruptedException, IOException {
        this.process = new ProcessBuilder("firefox-developer-edition", "--remote-debugging-port=0")
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectErrorStream(true)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(process::destroyForcibly));
        new Thread(this::handleStderr).start();
        webSocket = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(URI.create(webSocketUrl.get() + "/session"), new Listener())
                .get();
        sessionId = session().new_(new Session.CapabilitiesRequest(new Session.CapabilityRequest(
                true, new Session.ProxyConfiguration("manual", "127.0.0.1:" + proxyPort, "127.0.0.1:" + proxyPort
        )))).sessionId();
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
                            for (int i = 0; i < args.length; i++) {
                                map.put(method.getParameters()[i].getName(), args[i]);
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
                                yield BiDiJson.fromJson(future.get(), method.getReturnType());
                            }
                        }
                    };
                });
    }

    @Override
    public void close() throws IOException {
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
                            future.completeExceptionally(new RuntimeException(message.getString("message")));
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
