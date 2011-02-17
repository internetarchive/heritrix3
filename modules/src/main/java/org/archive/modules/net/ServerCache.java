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

package org.archive.modules.net;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.apache.commons.httpclient.URIException;
import org.archive.net.UURI;

/**
 * Abstract class for crawl-global registry of CrawlServer (host:port) and
 * CrawlHost (hostname) objects.
 */
public abstract class ServerCache {

    public abstract CrawlHost getHostFor(String host);

    public abstract CrawlServer getServerFor(String serverKey);

    /**
     * Utility for performing an action on every CrawlHost. 
     * 
     * @param action 1-argument Closure to apply to each CrawlHost
     */
    public abstract void forAllHostsDo(Closure action);

    
    private static Logger logger =
        Logger.getLogger(ServerCache.class.getName());
    
    /**
     * Get the {@link CrawlHost} associated with <code>curi</code>.
     * @param uuri CandidateURI we're to return Host for.
     * @return CandidateURI instance that matches the passed Host name.
     */
    public CrawlHost getHostFor(UURI uuri) {
        CrawlHost h = null;
        try {
            if (uuri.getScheme().equals("dns")) {
                h = getHostFor("dns:");
            } else if (uuri.getScheme().equals("whois")) {
                h = getHostFor("whois:");
            } else {
                h = getHostFor(uuri.getReferencedHost());
            }
        } catch (URIException e) {
            logger.log(Level.SEVERE, uuri.toString(), e);
        }
        return h;
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
            logger.log(
                Level.FINE, "No server key obtainable: "+uuri.toString(), e);
        } catch (NullPointerException npe) {
            logger.log(
                Level.FINE, "No server key obtainable: "+uuri.toString(), npe);
        }
        return cs;
    }

    abstract public Set<String> hostKeys();


}
