package org.archive.net;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import org.apache.commons.io.input.CharSequenceReader;
import org.apache.commons.lang.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

import java.io.*;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Client for the BiDirectional WebDriver Protocol for remotely controlling browsers.
 *
 * @see <a href="https://www.w3.org/TR/webdriver-bidi/>WebDriver BiDi specification</a>
 */
public class WebdriverBiDi implements Closeable {
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

    public WebdriverBiDi(int proxyPort) throws ExecutionException, InterruptedException, IOException {
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
                System.err.println("browser: " + line);
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
        command.put("params", toJson(params));
        String message = JSONWriter.valueToString(command);
        System.out.println("send: " + message);
        CompletableFuture<JSONObject> future = new CompletableFuture<>();
        commands.put(id, future);
        webSocket.sendText(message, true);
        return future;
    }

    public <T extends Event> void on(Class<T> eventClass, BrowsingContext.Context context, Consumer<T> handler) {
        String eventName = StringUtils.uncapitalize(eventClass.getEnclosingClass().getSimpleName()) + "." +
                StringUtils.uncapitalize(eventClass.getSimpleName());
        session().subscribe(Collections.singletonList(eventName), Collections.singletonList(context));
        eventHandlers.put(eventName, node -> {
            handler.accept(fromJson(node, eventClass));
        });
    }

    @SuppressWarnings("unchecked")
    public <T> T domain(Class<T> domainClass) {
        String prefix = domainClass.getSimpleName().substring(0, 1).toLowerCase(Locale.ROOT)
                + domainClass.getSimpleName().substring(1) + ".";
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{domainClass},
                (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    return switch (method.getName()) {
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "toString" -> domainClass.getName();
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
                                yield future.thenApply(result -> fromJson(result, typeClass));
                            } else {
                                yield fromJson(future.get(), method.getReturnType());
                            }
                        }
                    };
                });
    }

    public Session session() {
        return domain(Session.class);
    }

    public Network network() {
        return domain(Network.class);
    }

    public BrowsingContext browsingContext() {
        return domain(BrowsingContext.class);
    }

    public Script script() {
        return domain(Script.class);
    }

    private static Object toJson(Object value) {
        if (value == null) return null;
        if (value instanceof String) return value;
        if (value instanceof Number) return value;
        if (value instanceof Boolean) return value;
        if (value instanceof JSONObject) return value;
        if (value instanceof Identifier identifier) return identifier.id();
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        if (value instanceof Map<?, ?>) {
            var map = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                map.put(entry.getKey().toString(), toJson(entry.getValue()));
            }
            return map;
        }
        if (value instanceof Collection) {
            var list = new JSONArray();
            for (Object item : (Collection<?>) value) {
                list.put(toJson(item));
            }
            return list;
        }
        if (value.getClass().isRecord()) {
            var object = new JSONObject();
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    Object item = toJson(component.getAccessor().invoke(value));
                    if (item != null) object.put(component.getName(), item);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            return object;
        }
        if (value instanceof byte[] bytes) {
            var object = new JSONObject();
            try {
                object.put("type", "string");
                object.put("value", StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString());
            } catch (CharacterCodingException e) {
                object.put("type", "base64");
                object.put("value", Base64.getEncoder().encodeToString(bytes));
            }
            return object;
        }
        throw new UnsupportedOperationException("Unsupported type: " + value.getClass());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fromJson(Object value, Class<T> type) {
        try {
            if (value == JSONObject.NULL) {
                return null;
            }
            if (value == null || type.isPrimitive() || type.isAssignableFrom(value.getClass())) {
                return (T) value;
            }
            if (type == Long.class && value instanceof Number number) {
                return (T) (Long) number.longValue();
            }
            if (type == byte[].class && value instanceof JSONObject jsonObject) {
                switch (jsonObject.getString("type")) {
                    case "base64" -> {
                        return (T) Base64.getDecoder().decode(jsonObject.getString("value"));
                    }
                    case "string" -> {
                        return (T) jsonObject.getString("value").getBytes(StandardCharsets.UTF_8);
                    }
                }
            }
            if (Identifier.class.isAssignableFrom(type) && value instanceof String string) {
                return type.getDeclaredConstructor(String.class).newInstance(string);
            }
            if (type.isAssignableFrom(List.class) && value instanceof JSONArray array) {
                return (T) array.toList();
            }
            if (type.isEnum() && value instanceof String string) {
                return (T) Enum.valueOf((Class<Enum>) type, string);
            }
            if (type.isRecord()) {
                if (value instanceof JSONObject jsonObject) {
                    RecordComponent[] components = type.getRecordComponents();
                    Class<?>[] componentTypes = new Class<?>[components.length];
                    Object[] values = new Object[components.length];
                    for (int i = 0; i < components.length; i++) {
                        componentTypes[i] = components[i].getType();
                        var componentValue = jsonObject.opt(components[i].getName());
                        if (components[i].getGenericType() instanceof ParameterizedType parameterizedType) {
                            if (parameterizedType.getRawType() == List.class && componentValue instanceof JSONArray array) {
                                Class<?> listType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                                var list = new ArrayList<>();
                                for (Object item : array) {
                                    list.add(fromJson(item, listType));
                                }
                                values[i] = list;
                            } else {
                                throw new UnsupportedOperationException("Unsupported type: " + parameterizedType + " for " + components[i] + " in " + type + " (value '" + jsonObject + "')");
                            }
                        } else {
                            values[i] = fromJson(componentValue, components[i].getType());
                        }
                    }
                    return (T) type.getDeclaredConstructor(componentTypes).newInstance(values);
                } else if (value instanceof String string) {
                    return type.getConstructor(String.class).newInstance(string);
                } else {
                    throw new UnsupportedOperationException("Unsupported type: " + type);
                }
            }
            if (type.isInterface() && type.isSealed() && value instanceof JSONObject jsonObject) {
                String typeName = jsonObject.getString("type");
                if (typeName == null) throw new UnsupportedOperationException("Missing 'type' field in " + jsonObject);
                return (T) fromJson(jsonObject, getSubclassByTypeName(type, typeName));
            }
            throw new UnsupportedOperationException("Unsupported type: " + type + " (value '" + value + "')");
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException | IllegalArgumentException e) {
            throw new RuntimeException("Couldn't map to " + type + ": " + e.getMessage(), e);
        }
    }

    private static Class<?> getSubclassByTypeName(Class<?> type, String typeName) {
        for (Class<?> subclass : type.getPermittedSubclasses()) {
            TypeName annotation = subclass.getDeclaredAnnotation(TypeName.class);
            if (annotation != null && annotation.value().equals(typeName)) return subclass;
        }
        throw new UnsupportedOperationException("Unsupported 'type' for " + type + ": " + typeName);
    }

    @Override
    public void close() throws IOException {
        process.destroy();
    }

    interface Event {
    }

    interface Identifier {
        String id();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface TypeName {
        String value();
    }

    // https://w3c.github.io/webdriver-bidi/#module-browsingContext
    public interface BrowsingContext {
        CreateResult create(CreateType type);

        NavigateResult navigate(Context context, String url, ReadinessState wait);

        void close(Context context);

        enum CreateType {tab, window}

        enum ReadinessState {none, interactive, complete}

        record CreateResult(Context context) {
        }

        record Load(Context context, Navigation navigation, long timestamp, String url) implements Event {
        }

        record NavigateResult(Navigation navigation, String url) {
        }

        record Context(String id) implements Identifier {
        }

        record Navigation(String id) implements Identifier {
        }
    }

    // https://w3c.github.io/webdriver-bidi/#module-network
    public interface Network {
        AddInterceptResult addIntercept(List<InterceptPhase> phases,
                                        List<BrowsingContext.Context> contexts,
                                        List<UrlPattern> urlPatterns);

        void continueRequest(Request request);

        void continueRequest(Request request, List<Header> headers);

        void continueRequest(Request request, String method, String url);

        CompletableFuture<Void> continueRequestAsync(Request request);

        CompletableFuture<Void> continueRequestAsync(Request request, List<Header> headers);

        void provideResponse(Request request, byte[] body, List<Header> headers, String reasonPhrase, int statusCode);

        enum InterceptPhase {beforeRequestSent, responseStarted, authRequired}

        record AddInterceptResult(Intercept intercept) {
        }

        record Intercept(String id) implements Identifier {
        }

        record BeforeRequestSent(
                BrowsingContext.Context context,
                boolean isBlocked,
                BrowsingContext.Navigation navigation,
                long redirectCount,
                RequestData request,
                long timestamp,
                List<Intercept> intercepts,
                Initiator initiator) implements Event {
        }

        record Cookie(
                String name,
                byte[] value,
                String domain,
                String path,
                Long size,
                Boolean httpOnly,
                Boolean secure,
                SameSite sameSite,
                Long expiry) {
        }

        record Header(String name, byte[] value) {
            public Header(String name, String value) {
                this(name, value.getBytes(StandardCharsets.UTF_8));
            }
        }

        record Initiator(Long columnNumber, Long lineNumber, InitiatorType type) {
        }

        enum InitiatorType {parser, script, preflight, other}

        record Request(String id) implements Identifier {
        }

        record RequestData(
                Request request,
                String url,
                String method,
                List<Header> headers,
                List<Cookie> cookies,
                long headersSize,
                long bodySize,
                String destination,
                String initiatorType
        ) {
        }

        enum SameSite {strict, lax, none}

        interface UrlPattern {
            String type();
        }

        record UrlPatternPattern(String type, String protocol) implements UrlPattern {
        }
    }

    // https://w3c.github.io/webdriver-bidi/#module-session
    public interface Session {
        NewResult new_(CapabilitiesRequest capabilities);

        void subscribe(List<String> events, List<BrowsingContext.Context> contexts);

        record NewResult(String sessionId, JSONObject capabilities) {
        }

        record CapabilitiesRequest(CapabilityRequest alwaysMatch) {
        }

        record CapabilityRequest(
                boolean acceptInsecureCerts,
                ProxyConfiguration proxy
        ) {
        }

        record ProxyConfiguration(
                String proxyType,
                String httpProxy,
                String sslProxy
        ) {
        }
    }

    // https://www.w3.org/TR/webdriver-bidi/#module-script
    public interface Script {
        EvaluateResult evaluate(String expression, Target target, boolean awaitPromise);

        default EvaluateResultSuccess evaluateOrThrow(String expression, Target target, boolean awaitPromise) {
            var result = evaluate(expression, target, awaitPromise);
            if (result instanceof EvaluateResultSuccess success) {
                return success;
            } else if (result instanceof EvaluateResultException exception) {
                throw new RuntimeException(exception.exceptionDetails().text());
            } else {
                throw new RuntimeException("Unexpected EvaluateResult: " + result);
            }
        }

        sealed interface EvaluateResult permits EvaluateResultSuccess, EvaluateResultException {}

        @TypeName("success")
        record EvaluateResultSuccess(RemoteValue result, Realm realm) implements EvaluateResult {
        }

        @TypeName("exception")
        record EvaluateResultException(ExceptionDetails exceptionDetails, Realm realm) implements EvaluateResult {
        }

        record ExceptionDetails(String text /* TODO */) {
        }

        sealed interface RemoteValue {
            Object javaValue();
        }

        @TypeName("number")
        record NumberValue(Number value) implements RemoteValue {
            @Override
            public Number javaValue() {
                return value;
            }
        }

        @TypeName("string")
        record StringValue(String value) implements RemoteValue {
            @Override
            public String javaValue() {
                return value;
            }
        }

        @TypeName("array")
        record ArrayRemoteValue(List<RemoteValue> value) implements RemoteValue {
            @Override
            public List<?> javaValue() {
                return value.stream().map(RemoteValue::javaValue).collect(Collectors.toList());
            }
        }

        sealed interface Target {
        }

        record ContextTarget(BrowsingContext.Context context, String sandbox) implements Target {
        }

        record RealmTarget(Realm realm) implements Target {
        }

        record Realm(String id) implements Identifier {
        }

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
            System.out.println("onText: " + data);
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
