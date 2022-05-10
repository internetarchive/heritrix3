package org.archive.modules.fetcher;

import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

public class SocksSocketFactory extends PlainConnectionSocketFactory {
    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        InetSocketAddress socksAddress = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksAddress);
        return new Socket(proxy);
    }
}