package org.archive.crawler.restlet;

import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.restlet.Server;
import org.restlet.ext.jetty.HttpsServerHelper;

/**
 * Subclass of HttpServerHelper which disables the SNI host check. This is to main backwards
 * compatibility with the existing Heritrix ad-hoc certificates that don't include a hostname.
 */
public class NoSniHostCheckHttpsServerHelper extends HttpsServerHelper {
    public NoSniHostCheckHttpsServerHelper(Server server) {
        super(server);
    }

    @Override
    protected org.eclipse.jetty.server.Server getWrappedServer() {
        org.eclipse.jetty.server.Server wrappedServer = super.getWrappedServer();
        disableSniHostCheck(wrappedServer);
        return wrappedServer;
    }

    private static void disableSniHostCheck(org.eclipse.jetty.server.Server jettyServer) {
        for (var connector : jettyServer.getConnectors()) {
            if (connector instanceof ServerConnector serverConnector) {
                var connectionFactory = serverConnector.getConnectionFactory(HttpConnectionFactory.class);
                if (connectionFactory != null) {
                    var secureRequestCustomizer = connectionFactory.getHttpConfiguration().getCustomizer(SecureRequestCustomizer.class);
                    if (secureRequestCustomizer != null) {
                        secureRequestCustomizer.setSniHostCheck(false);
                    }
                }
            }
        }
    }
}
