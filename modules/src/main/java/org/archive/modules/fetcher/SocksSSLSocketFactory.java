package org.archive.modules.fetcher;


import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

public class SocksSSLSocketFactory extends SSLConnectionSocketFactory {
    public SocksSSLSocketFactory(final SSLContext sslContext) {
        // TODO: site certificate ignore needs implementing here if Heritrix supports it
        super(sslContext);
    }

    @Override
    public Socket createSocket(final HttpContext context) throws IOException {
        InetSocketAddress socksAddress = (InetSocketAddress) context.getAttribute("socks.address");
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksAddress);
        return new Socket(proxy);
    }
}
