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

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.proxy.ProxyHandler;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ConnectHandler;
import org.eclipse.jetty.util.Callback;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.Map;

import static org.eclipse.jetty.http.HttpHeader.ACCEPT_ENCODING;

/**
 * An HTTP proxy server which intercepts TLS and records or replays responses.
 */
public class MitmProxy {
    private final SslConnectionFactory sslConnectionFactory = new SslConnectionFactory();
    private final Server server = new Server(0);
    private final RequestHandler requestHandler;

    public MitmProxy(RequestHandler requestHandler) {
        this.requestHandler = requestHandler;
        sslConnectionFactory.getSslContextFactory().setKeyStorePath("adhoc.keystore");
        sslConnectionFactory.getSslContextFactory().setKeyStorePassword("password");
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
            HttpURI uri = super.rewriteHttpURI(clientToProxyRequest);
            String string = uri.asString();
            // HttpClient uses Java URI which unlike WHATWG URL doesn't allow "|" so percent encode it
            if (string.contains("|")) {
                return HttpURI.from(string.replace("|", "%7C"));
            } else {
                return uri;
            }
        }

        @Override
        protected void addProxyHeaders(org.eclipse.jetty.server.Request clientToProxyRequest, org.eclipse.jetty.client.Request proxyToServerRequest) {
            proxyToServerRequest.headers(headers -> {
                // Ensure we only get the encodings we support
                headers.put(ACCEPT_ENCODING, "gzip");

                // Host header is not allowed in HTTP/2
                headers.remove(HttpHeader.HOST);
            });
            var listener = (ExchangeListener)clientToProxyRequest.getAttribute(ExchangeListener.class.getName());
            if (listener != null) {
                proxyToServerRequest.onRequestHeaders(listener);
                proxyToServerRequest.onRequestContent(listener);
                proxyToServerRequest.onResponseHeaders(listener);
                proxyToServerRequest.onResponseContent(listener);
                proxyToServerRequest.onComplete(listener);
            }
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
