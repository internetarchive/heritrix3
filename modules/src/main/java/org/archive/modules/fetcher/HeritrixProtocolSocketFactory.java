/* HeritrixProtocolSocketFactory
 * 
 * Created on Oct 8, 2004
 *
 * Copyright (C) 2004 Internet Archive.
 * 
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 * 
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 * 
 * Heritrix is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.modules.fetcher;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;


/**
 * Version of protocol socket factory that tries to get IP from heritrix IP
 * cache -- if its been set into the HttpConnectionParameters.
 * 
 * Copied the guts of DefaultProtocolSocketFactory.  This factory gets
 * setup by {@link FetchHTTP}.
 * 
 * @author stack
 * @version $Date$, $Revision$
 */
public class HeritrixProtocolSocketFactory
implements ProtocolSocketFactory {
    /**
     * Constructor.
     */
    public HeritrixProtocolSocketFactory() {
        super();
    }

    /**
     * @see #createSocket(java.lang.String,int,java.net.InetAddress,int)
     */
    public Socket createSocket(
        String host,
        int port,
        InetAddress localAddress,
        int localPort
    ) throws IOException, UnknownHostException {
        return new Socket(host, port, localAddress, localPort);
    }

    /**
     * Attempts to get a new socket connection to the given host within the
     * given time limit.
     * <p>
     * This method employs several techniques to circumvent the limitations
     * of older JREs that do not support connect timeout. When running in
     * JRE 1.4 or above reflection is used to call
     * Socket#connect(SocketAddress endpoint, int timeout) method. When
     * executing in older JREs a controller thread is executed. The
     * controller thread attempts to create a new socket within the given
     * limit of time. If socket constructor does not return until the
     * timeout expires, the controller terminates and throws an
     * {@link ConnectTimeoutException}
     * </p>
     *
     * @param host the host name/IP
     * @param port the port on the host
     * @param localAddress the local host name/IP to bind the socket to
     * @param localPort the port on the local machine
     * @param params {@link HttpConnectionParams Http connection parameters}
     *
     * @return Socket a new socket
     *
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * @throws IOException if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     * determined
     * @throws ConnectTimeoutException if socket cannot be connected within the
     *  given time limit
     *
     * @since 3.0
     */
    public Socket createSocket(
        final String host,
        final int port,
        final InetAddress localAddress,
        final int localPort,
        final HttpConnectionParams params)
    throws IOException, UnknownHostException, ConnectTimeoutException {
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
            socket = new Socket();
            
            InetAddress hostAddress;
            Thread current = Thread.currentThread();
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
    
    /**
     * Get host address using first the heritrix cache of addresses, then,
     * failing that, go to the dnsjava cache.
     * 
     * Default access and static so can be used by other classes in this
     * package.
     *
     * @param host Host whose address we're to fetch.
     * @return an IP address for this host or null if one can't be found
     * in caches.
     * @exception IOException If we fail to get host IP from ServerCache.
     */
    /*
    static InetAddress getHostAddress(final ServerCache cache,
            final String host) throws IOException {
        InetAddress result = null;
        if (cache != null) {
        	CrawlHost ch = cache.getHostFor(host);
            if (ch != null) {
                result = ch.getIP();
            }
        }
        if (result ==  null) {
            throw new IOException("Failed to get host " + host +
                " address from ServerCache");
        }
        return result;
    }
    */

    /**
     * @see ProtocolSocketFactory#createSocket(java.lang.String,int)
     */
    public Socket createSocket(String host, int port)
            throws IOException, UnknownHostException {
        return new Socket(host, port);
    }

    /**
     * All instances of DefaultProtocolSocketFactory are the same.
     * @param obj Object to compare.
     * @return True if equal
     */
    public boolean equals(Object obj) {
        return ((obj != null) &&
            obj.getClass().equals(HeritrixProtocolSocketFactory.class));
    }

    /**
     * All instances of DefaultProtocolSocketFactory have the same hash code.
     * @return Hash code for this object.
     */
    public int hashCode() {
        return HeritrixProtocolSocketFactory.class.hashCode();
    }
}
