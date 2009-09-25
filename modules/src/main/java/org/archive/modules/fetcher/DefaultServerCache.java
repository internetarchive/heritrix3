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

import java.io.Closeable;
import java.io.Serializable;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.net.UURI;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.ObjectIdentityMemCache;
import org.archive.util.Supplier;


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
    protected ObjectIdentityCache<String,CrawlServer> servers = null;
    
    /**
     * hostname -> CrawlHost.
     * Set in the initialization.
     */
    protected ObjectIdentityCache<String,CrawlHost> hosts = null;
    
    /**
     * Constructor.
     */
    public DefaultServerCache() {
        this(
            new ObjectIdentityMemCache<CrawlServer>(), 
            new ObjectIdentityMemCache<CrawlHost>());
    }
    
    
    
    public DefaultServerCache(ObjectIdentityCache<String,CrawlServer> servers, 
            ObjectIdentityCache<String,CrawlHost> hosts) {
        this.servers = servers;
        this.hosts = hosts;
    }
    
    /**
     * Get the {@link CrawlServer} associated with <code>name</code>.
     * @param serverKey Server name we're to return server for.
     * @return CrawlServer instance that matches the passed server name.
     */
    public synchronized CrawlServer getServerFor(final String serverKey) {
        CrawlServer cserver = servers.getOrUse(
                serverKey,
                new Supplier<CrawlServer>() {
                    public CrawlServer get() {
                        String skey = new String(serverKey); // ensure private minimal key
                        return new CrawlServer(skey);
                    }});
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
    public synchronized CrawlHost getHostFor(final String hostname) {
        if (hostname == null || hostname.length() == 0) {
            return null;
        }
        CrawlHost host = hosts.getOrUse(
                hostname,
                new Supplier<CrawlHost>() {
                    public CrawlHost get() {
                        String hkey = new String(hostname); // ensure private minimal key
                        return new CrawlHost(hkey);
                    }});
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
            this.hosts.close();
            this.hosts = null;
        }
        if (this.servers != null) { 
            this.servers.close();
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