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
package org.archive.modules.fetcher;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;
import org.archive.httpclient.ConfigurableX509TrustManager;


/**
 * Implementation of the commons-httpclient SSLProtocolSocketFactory so we
 * can return SSLSockets whose trust manager is
 * {@link org.archive.httpclient.ConfigurableX509TrustManager}.
 * 
 * We also go to the heritrix cache to get IPs to use making connection.
 * To this, we have dependency on {@link HeritrixProtocolSocketFactory};
 * its assumed this class and it are used together.
 * See {@link HeritrixProtocolSocketFactory#getHostAddress(ServerCache,String)}.
 *
 * @author stack
 * @version $Id$
 * @see org.archive.httpclient.ConfigurableX509TrustManager
 */
public class HeritrixSSLProtocolSocketFactory
implements SecureProtocolSocketFactory {
    /***
     * Socket factory with default trust manager installed.
     */
    private SSLSocketFactory sslDefaultFactory = null;
    
    /**
     * Shutdown constructor.
     * @throws KeyManagementException
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     */
    public HeritrixSSLProtocolSocketFactory()
    throws KeyManagementException, KeyStoreException, NoSuchAlgorithmException{
        // Get an SSL context and initialize it.
        SSLContext context = SSLContext.getInstance("SSL");

        // I tried to get the default KeyManagers but doesn't work unless you
        // point at a physical keystore. Passing null seems to do the right
        // thing so we'll go w/ that.
        context.init(null, new TrustManager[] {
            new ConfigurableX509TrustManager(
                ConfigurableX509TrustManager.DEFAULT)}, null);
        this.sslDefaultFactory = context.getSocketFactory();
    }

    public Socket createSocket(String host, int port, InetAddress clientHost,
        int clientPort)
    throws IOException, UnknownHostException {
    	return this.sslDefaultFactory.createSocket(host, port,
    	    clientHost, clientPort);
    }

    public Socket createSocket(String host, int port)
    throws IOException, UnknownHostException {
        return this.sslDefaultFactory.createSocket(host, port);
    }

    public synchronized Socket createSocket(String host, int port,
    	InetAddress localAddress, int localPort, HttpConnectionParams params)
    throws IOException, UnknownHostException {
        // Below code is from the DefaultSSLProtocolSocketFactory#createSocket
        // method only it has workarounds to deal with pre-1.4 JVMs.  I've
        // cut these out.
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        Socket socket = null;
        int timeout = params.getConnectionTimeout();
        if (timeout == 0) {
            socket = createSocket(host, port, localAddress, localPort);
        } else {
        	SSLSocketFactory factory = (SSLSocketFactory)params.
                getParameter(FetchHTTP.SSL_FACTORY_KEY);
        	SSLSocketFactory f = (factory != null)? factory: this.sslDefaultFactory;
            socket = f.createSocket();
            
            Thread current = Thread.currentThread();
            InetAddress hostAddress;
            if (current instanceof HostResolver) {
                HostResolver resolver = (HostResolver)current;
                hostAddress = resolver.resolve(host);
            } else {
                hostAddress = null;
            }
            InetSocketAddress address = (hostAddress != null)?
                    new InetSocketAddress(hostAddress, port):
                    new InetSocketAddress(host, port);
            socket.bind(new InetSocketAddress(localAddress, localPort));
            try {
                socket.connect(address, timeout);
            } catch (SocketTimeoutException e) {
                // Add timeout info. to the exception.
                throw new SocketTimeoutException(e.getMessage() +
                    ": timeout set at " + Integer.toString(timeout) + "ms.");
            }
            assert socket.isConnected(): "Socket not connected " + host;
        }
        return socket;
    }
    
	public Socket createSocket(Socket socket, String host, int port,
        boolean autoClose)
    throws IOException, UnknownHostException {
        return this.sslDefaultFactory.createSocket(socket, host,
            port, autoClose);
	}
    
    public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().
            equals(HeritrixSSLProtocolSocketFactory.class));
    }

    public int hashCode() {
        return HeritrixSSLProtocolSocketFactory.class.hashCode();
    }
}