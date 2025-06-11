package org.archive.net;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class MitmProxyTest {
    /**
     * Browsers will send requests with characters like '|' and '[' in the query string. java.net.URI disallows these
     * so this tests to make sure the proxy handles them OK.
     */
    @Test
    public void testProxyingDisallowedCharacters(@TempDir Path tempDir) throws Exception {
        var server = new Server(new InetSocketAddress(Inet4Address.getLoopbackAddress(), 0));
        server.setHandler(new Handler.Abstract() {
            @Override
            public boolean handle(Request request, Response response, Callback callback) throws Exception {
                response.write(true, ByteBuffer.wrap("Hello World".getBytes()), callback);
                return true;
            }
        });
        server.start();
        var proxy = new MitmProxy(request -> {}, tempDir.resolve("proxy.keystore").toString());
        proxy.start();
        try (var socket = new Socket(Inet4Address.getLoopbackAddress(), proxy.getPort())) {
            String url = "http://" + Inet4Address.getLoopbackAddress().getHostAddress() +
                         ":" + ((ServerConnector)server.getConnectors()[0]).getLocalPort() + "/?q=bad|chars[here]";
            socket.getOutputStream().write(("GET " + url + " HTTP/1.0\r\n\r\n").getBytes(US_ASCII));
            var response = new String(socket.getInputStream().readAllBytes(), US_ASCII);
            var lines = response.split("\r\n");
            assertEquals("HTTP/1.1 200 OK", lines[0]);
            assertEquals("Hello World", lines[lines.length - 1]);
        } finally {
            proxy.stop();
            server.stop();
        }
    }

}