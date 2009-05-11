/* ServerCache
 * 
 * Created on Nov 19, 2004
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

import java.io.Closeable;
import java.io.Serializable;
import java.util.Map;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.net.UURI;


/**
 * Server and Host cache.
 * @author stack
 * @version $Date$, $Revision$
 */
public class DefaultServerCache implements ServerCache, Closeable, Serializable {


    private static final long serialVersionUID = 1L;


    private static Logger logger =
        Logger.getLogger(DefaultServerCache.class.getName());
    
    
    /**
     * hostname[:port] -> CrawlServer.
     * Set in the initialization.
     */
    protected Map<String,CrawlServer> servers = null;
    
    /**
     * hostname -> CrawlHost.
     * Set in the initialization.
     */
    protected Map<String,CrawlHost> hosts = null;
    
    /**
     * Constructor.
     */
    public DefaultServerCache() {
        this(new Hashtable<String,CrawlServer>(), new Hashtable<String,CrawlHost>());
    }
    
    
    
    public DefaultServerCache(Map<String,CrawlServer> servers, 
            Map<String,CrawlHost> hosts) {
        this.servers = servers;
        this.hosts = hosts;
    }
    
    /**
     * Get the {@link CrawlServer} associated with <code>name</code>.
     * @param serverKey Server name we're to return server for.
     * @return CrawlServer instance that matches the passed server name.
     */
    public synchronized CrawlServer getServerFor(String serverKey) {
        CrawlServer cserver = (CrawlServer)this.servers.get(serverKey);
        return (cserver != null)? cserver: createServerFor(serverKey);
    }
    
    protected CrawlServer createServerFor(String s) {
        CrawlServer cserver = (CrawlServer)this.servers.get(s);
        if (cserver != null) {
            return cserver;
        }
        // Ensure key is private object
        String skey = new String(s);
        cserver = new CrawlServer(skey);
        servers.put(skey,cserver);
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Created server " + s);
        }
        return cserver;
    }

    /**
     * Get the {@link CrawlServer} associated with <code>curi</code>.
     * @param uuri CandidateURI we're to get server from.
     * @return CrawlServer instance that matches the passed CandidateURI.
     */
    public CrawlServer getServerFor(UURI uuri) {
        CrawlServer cs = null;
        try {
            String key = CrawlServer.getServerKey(uuri);
            // TODOSOMEDAY: make this robust against those rare cases
            // where authority is not a hostname.
            if (key != null) {
                cs = getServerFor(key);
            }
        } catch (URIException e) {
            logger.severe(e.getMessage() + ": " + uuri);
            e.printStackTrace();
        } catch (NullPointerException npe) {
            logger.severe(npe.getMessage() + ": " + uuri);
            npe.printStackTrace();
        }
        return cs;
    }
    
    /**
     * Get the {@link CrawlHost} associated with <code>name</code>.
     * @param hostname Host name we're to return Host for.
     * @return CrawlHost instance that matches the passed Host name.
     */
    public synchronized CrawlHost getHostFor(String hostname) {
        if (hostname == null || hostname.length() == 0) {
            return null;
        }
        CrawlHost host = (CrawlHost)this.hosts.get(hostname);
        return (host != null)? host: createHostFor(hostname);
    }
    
    protected CrawlHost createHostFor(String hostname) {
        if (hostname == null || hostname.length() == 0) {
            return null;
        }
        CrawlHost host = (CrawlHost)this.hosts.get(hostname);
        if (host != null) {
            return host;
        }
        String hkey = new String(hostname); 
        host = new CrawlHost(hkey);
        this.hosts.put(hkey, host);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Created host " + hostname);
        }
        return host;
    }
    
    /**
     * Get the {@link CrawlHost} associated with <code>curi</code>.
     * @param uuri CandidateURI we're to return Host for.
     * @return CandidateURI instance that matches the passed Host name.
     */
    public CrawlHost getHostFor(UURI uuri) {
        CrawlHost h = null;
        try {
            h = getHostFor(uuri.getReferencedHost());
        } catch (URIException e) {
            e.printStackTrace();
        }
        return h;
    }

    /**
     * @param serverKey Key to use doing lookup.
     * @return True if a server instance exists.
     */
    public boolean containsServer(String serverKey) {
        return (CrawlServer) servers.get(serverKey) != null; 
    }

    /**
     * @param hostKey Key to use doing lookup.
     * @return True if a host instance exists.
     */
    public boolean containsHost(String hostKey) {
        return (CrawlHost) hosts.get(hostKey) != null; 
    }

    /**
     * Called when shutting down the cache so we can do clean up.
     */
    public void close() {
        if (this.hosts != null) {
            // If we're using a bdb bigmap, the call to clear will
            // close down the bdb database.
            this.hosts.clear();
            this.hosts = null;
        }
        if (this.servers != null) { 
            this.servers.clear();
            this.servers = null;
        }
    }

    /* (non-Javadoc)
     * @see org.archive.modules.net.ServerCache#forAllHostsDo(org.apache.commons.collections.Closure)
     */
    public void forAllHostsDo(Closure c) {
        for(String host : hosts.keySet()) {
            c.execute(hosts.get(host));
        }
    }
}