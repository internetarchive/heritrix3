package org.archive.net;

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.util.Callback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

    @Test
    public void testAllowedAddresses(@TempDir Path tempDir) throws Exception {
        // skip interfaces that are down, such as an idle Docker bridge, whose NAT rules rewrite the source address
        InetAddress lanAddress = null;
        for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (address instanceof Inet4Address && lanAddress == null) lanAddress = address;
            }
        }
        Assumptions.assumeTrue(lanAddress != null, "needs a non-loopback IPv4 address");
        MitmProxy.RequestHandler handler = request -> request.sendResponse(200, Map.of(),
                new ByteArrayInputStream("ok".getBytes(US_ASCII)));
        String keystore = tempDir.resolve("proxy.keystore").toString();

        var proxy = new MitmProxy(handler, keystore, "0.0.0.0", 0, List.of());
        proxy.start();
        try {
            assertStatus(200, sendRequest(InetAddress.getLoopbackAddress(), proxy.getPort()));
            assertStatus(403, sendRequest(lanAddress, proxy.getPort()));
        } finally {
            proxy.stop();
        }

        proxy = new MitmProxy(handler, keystore, lanAddress.getHostAddress(), 0,
                List.of(lanAddress.getHostAddress() + "/32"));
        proxy.start();
        try {
            assertStatus(200, sendRequest(lanAddress, proxy.getPort()));
            assertStatus(200, sendRequest(InetAddress.getLoopbackAddress(), proxy.getPort())); // also on loopback
        } finally {
            proxy.stop();
        }
        proxy.start(); // again, as when a job is relaunched
        try {
            assertStatus(200, sendRequest(InetAddress.getLoopbackAddress(), proxy.getPort()));
        } finally {
            proxy.stop();
        }
    }

    @Test
    public void testRepeatedResponseHeaders(@TempDir Path tempDir) throws Exception {
        MitmProxy.RequestHandler handler = request -> request.sendResponse(200,
                HttpFields.build().add("Set-Cookie", "a=1").add("Set-Cookie", "b=2"),
                new ByteArrayInputStream("ok".getBytes(US_ASCII)));
        var proxy = new MitmProxy(handler, tempDir.resolve("proxy.keystore").toString());
        proxy.start();
        try {
            String response = sendRequest(InetAddress.getLoopbackAddress(), proxy.getPort());
            assertTrue(response.contains("\r\nSet-Cookie: a=1\r\nSet-Cookie: b=2\r\n"), response);
        } finally {
            proxy.stop();
        }
    }

    private static void assertStatus(int status, String response) {
        assertTrue(response.startsWith("HTTP/1.1 " + status + " "), "Unexpected response: " + response);
    }

    /**
     * Sends a request to the proxy at the given address and port, with the given address as the source address too.
     */
    private static String sendRequest(InetAddress address, int port) throws IOException {
        try (var socket = new Socket(address, port, address, 0)) {
            socket.getOutputStream().write("GET http://example.invalid/ HTTP/1.0\r\n\r\n".getBytes(US_ASCII));
            return new String(socket.getInputStream().readAllBytes(), US_ASCII);
        }
    }
}
