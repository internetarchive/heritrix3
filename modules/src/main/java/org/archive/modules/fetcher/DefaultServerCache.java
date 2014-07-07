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
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.ObjectIdentityMemCache;
import org.archive.util.Supplier;


/**
 * Server and Host cache.
 * @author stack
 * @version $Date$, $Revision$
 */
public class DefaultServerCache extends ServerCache implements Closeable, Serializable {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("unused")
    private static Logger logger =
        Logger.getLogger(DefaultServerCache.class.getName());
    
    
    /**
     * hostname[:port] -> CrawlServer.
     * Set in the initialization.
     */
    protected ObjectIdentityCache<CrawlServer> servers = null;
    
    /**
     * hostname -> CrawlHost.
     * Set in the initialization.
     */
    protected ObjectIdentityCache<CrawlHost> hosts = null;
    
    /**
     * Constructor.
     */
    public DefaultServerCache() {
        this(
            new ObjectIdentityMemCache<CrawlServer>(), 
            new ObjectIdentityMemCache<CrawlHost>());
    }
    
    
    
    public DefaultServerCache(ObjectIdentityCache<CrawlServer> servers, 
            ObjectIdentityCache<CrawlHost> hosts) {
        this.servers = servers;
        this.hosts = hosts;
    }
    
    /**
     * Get the {@link CrawlServer} associated with <code>name</code>.
     * @param serverKey Server name we're to return server for.
     * @return CrawlServer instance that matches the passed server name.
     */
    public CrawlServer getServerFor(final String serverKey) {
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
     * Get the {@link CrawlHost} associated with <code>name</code>.
     * @param hostname Host name we're to return Host for.
     * @return CrawlHost instance that matches the passed Host name.
     */
    public CrawlHost getHostFor(final String hostname) {
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
        if (host != null && host.getIP() != null
                && "0.0.0.0".equals(host.getIP().getHostAddress())) {
            throw new IllegalStateException("got suspicious value 0.0.0.0 for " + hostname);
        }
        return host;
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

    /**
     * NOTE: Should not mutate the CrawlHost instance so retrieved; depending on
     * the hostscache implementation, the change may not be reliably persistent.  
     * 
     * @see org.archive.modules.net.ServerCache#forAllHostsDo(org.apache.commons.collections.Closure)
     */
    public void forAllHostsDo(Closure c) {
        for(String host : hosts.keySet()) {
            c.execute(hosts.get(host));
        }
    }
    
    public Set<String> hostKeys() {
        return hosts.keySet();
    }
}