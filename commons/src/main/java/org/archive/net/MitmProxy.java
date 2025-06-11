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

package org.archive.net;

import org.archive.util.KeyTool;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.ProxyConfiguration;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.util.Callback;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.eclipse.jetty.http.HttpHeader.ACCEPT_ENCODING;

/**
 * An HTTP proxy server which intercepts TLS and records or replays responses.
 */
public class MitmProxy {
    private static final String UPSTREAM_PROXY = MitmProxy.class.getName() + ".upstreamProxy";
    private final SslConnectionFactory sslConnectionFactory = new SslConnectionFactory();
    private final Server server = new Server(0);
    private final RequestHandler requestHandler;

    public MitmProxy(RequestHandler requestHandler, String keystorePath) {
        this.requestHandler = requestHandler;
        generateKeystore(keystorePath);
        sslConnectionFactory.getSslContextFactory().setKeyStorePath(keystorePath);
        sslConnectionFactory.getSslContextFactory().setKeyStorePassword("password");
    }

    private void generateKeystore(String keystorePath) {
        File keystoreFile = new File(keystorePath);
        if(!keystoreFile.exists())  {
            String[] args = {
                    "-keystore", keystorePath,
                    "-storepass", "password",
                    "-keypass", "password",
                    "-alias", "adhoc",
                    "-genkey", "-keyalg", "RSA",
                    "-dname", "CN=Heritrix Recording Proxy Certificate",
                    "-validity", "3650"};
            KeyTool.main(args);
        }
    }

    public int getPort() {
        try {
            return ((InetSocketAddress) ((ServerSocketChannel) server.getConnectors()[0].getTransport())
                    .getLocalAddress()).getPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void start() throws Exception {
        sslConnectionFactory.start();
        server.setHandler(new Handler.Sequence(
                new SslConnectHandler(),
                new MitmProxyHandler()));
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
        sslConnectionFactory.stop();
    }

    public record Request(org.eclipse.jetty.server.Request request, Response response, Callback callback) {
        public String url() {
            return request().getHttpURI().asString();
        }

        public void setListener(ExchangeListener listener) {
            request.setAttribute(ExchangeListener.class.getName(), listener);
        }

        public void sendResponse(int status, Map<String,String> headers, InputStream body) throws IOException {
            response.setStatus(status);
            headers.forEach((k,v) -> response.getHeaders().put(k, v));
            body.transferTo(Content.Sink.asOutputStream(response));
            callback().succeeded();
        }

        public void setUpstreamProxy(ProxyConfiguration.Proxy proxy) {
            request.setAttribute(UPSTREAM_PROXY, proxy);
        }
    }

    public interface RequestHandler {
        /**
         * Handles a request to the proxy server. Must either:
         * <ul>
         *     <li>write an immediate response, in which case the proxy won't make an upstream request
         *     <li>return an {@link ExchangeListener} to record the exchange with the upstream server
         *     <li>return null to let the proxy make an upstream request without recording it
         * </ul>
         */
        void handle(Request request) throws IOException;
    }

    public interface ExchangeListener extends
            org.eclipse.jetty.client.Request.BeginListener,
            org.eclipse.jetty.client.Request.HeadersListener,
            org.eclipse.jetty.client.Request.ContentListener,
            org.eclipse.jetty.client.Response.ContentListener,
            org.eclipse.jetty.client.Response.HeadersListener,
            org.eclipse.jetty.client.Response.CompleteListener {
    }

    private class MitmProxyHandler extends ProxyHandler.Forward {
        @Override
        protected HttpClient newHttpClient() {
            HttpClient httpClient = super.newHttpClient();
            httpClient.setMaxConnectionsPerDestination(6);
            httpClient.setFollowRedirects(false);
            httpClient.setUserAgentField(null);
            return httpClient;
        }

        @Override
        public boolean handle(org.eclipse.jetty.server.Request request, Response response, Callback callback) {
            try {
                requestHandler.handle(new Request(request, response, callback));
                if (response.isCommitted()) return true;
                return super.handle(request, response, callback);
            } catch (Throwable t) {
                callback.failed(t);
                return true;
            }
        }

        @Override
        protected HttpURI rewriteHttpURI(org.eclipse.jetty.server.Request clientToProxyRequest) {
            var uri = HttpURI.build(super.rewriteHttpURI(clientToProxyRequest));
            // HttpClient uses java.net.URI which is stricter about percent encoding than browsers
            if (uri.getPath() != null) uri.path(percentEncode(uri.getPath(), JAVA_URI_PATH_CHARS));
            if (uri.getQuery() != null) uri.query(percentEncode(uri.getQuery(), JAVA_URI_QUERY_CHARS));
            return uri;
        }

        private static final boolean[] JAVA_URI_PATH_CHARS = new boolean[128];
        private static final boolean[] JAVA_URI_QUERY_CHARS;

        static {
            // slash and @
            JAVA_URI_PATH_CHARS['/'] = true; JAVA_URI_PATH_CHARS['@'] = true;
            // alphanum
            for (char c = '0'; c <= '9'; c++) JAVA_URI_PATH_CHARS[c] = true;
            for (char c = 'A'; c <= 'Z'; c++) JAVA_URI_PATH_CHARS[c] = true;
            for (char c = 'a'; c <= 'z'; c++) JAVA_URI_PATH_CHARS[c] = true;
            // unreserved
            for (char c : "_-!.~'()*".toCharArray()) JAVA_URI_PATH_CHARS[c] = true;
            // punct
            for (char c : ",;:$&+=".toCharArray()) JAVA_URI_PATH_CHARS[c] = true;

            JAVA_URI_QUERY_CHARS = Arrays.copyOf(JAVA_URI_PATH_CHARS, 128);
            JAVA_URI_QUERY_CHARS['?'] = true;
        }

        private static String percentEncode(String s, boolean[] chars) {
            byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder(bytes.length);
            for (byte b : bytes) {
                if ((b & 0xff) < 128 && chars[b & 0xff]) {
                    sb.append((char)b);
                } else {
                    sb.append('%');
                    sb.append(upperHexDigit((b >> 4) & 0xf));
                    sb.append(upperHexDigit(b & 0xf));
                }
            }
            return sb.toString();
        }

        private static char upperHexDigit(int n) {
            return (char) ((n < 10 ? '0' : 'A' - 10) + n);
        }

        @Override
        protected void addProxyHeaders(org.eclipse.jetty.server.Request clientToProxyRequest, org.eclipse.jetty.client.Request proxyToServerRequest) {
            proxyToServerRequest.headers(headers -> {
                // Ensure we only get the encodings we support
                headers.put(ACCEPT_ENCODING, "gzip");

                // Host header is not allowed in HTTP/2
                headers.remove(HttpHeader.HOST);
            });

            ProxyConfiguration.Proxy upstreamProxy = (HttpProxy)clientToProxyRequest.getAttribute(UPSTREAM_PROXY);
            if (upstreamProxy != null) {
                addUpstreamProxyIfAbsent(upstreamProxy);
                proxyToServerRequest.tag(upstreamProxy);
            }

            var listener = (ExchangeListener)clientToProxyRequest.getAttribute(ExchangeListener.class.getName());
            if (listener != null) {
                proxyToServerRequest.onRequestBegin(listener);
                proxyToServerRequest.onRequestHeaders(listener);
                proxyToServerRequest.onRequestContent(listener);
                proxyToServerRequest.onResponseHeaders(listener);
                proxyToServerRequest.onResponseContent(listener);
                proxyToServerRequest.onComplete(listener);
            }
        }

        private void addUpstreamProxyIfAbsent(ProxyConfiguration.Proxy proxy) {
            for (var existingProxy : getHttpClient().getProxyConfiguration().getProxies()) {
                if (existingProxy == proxy) return;
            }
            getHttpClient().getProxyConfiguration().addProxy(proxy);
        }
    }

    /**
     * Handles the CONNECT method by upgrading the connection to SSL.
     */
    private class SslConnectHandler extends ConnectHandler {
        @Override
        protected void handleConnect(org.eclipse.jetty.server.Request request, Response response, Callback callback, String serverAddress) {
            EndPoint clientEP = request.getTunnelSupport().getEndPoint();
            var sslConnection = sslConnectionFactory.newConnection(server.getConnectors()[0], clientEP);
            request.setAttribute(HttpStream.UPGRADE_CONNECTION_ATTRIBUTE, sslConnection);
            response.setStatus(200);
            callback.succeeded();
        }
    }
}
