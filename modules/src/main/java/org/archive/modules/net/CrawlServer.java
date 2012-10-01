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

import static org.archive.modules.CrawlURI.FetchType.HTTP_GET;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_LOST;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DEEMED_NOT_FOUND;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.PredicateUtils;
import org.apache.commons.httpclient.NoHttpResponseException;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.bdb.AutoKryo;
import org.archive.modules.CrawlURI;
import org.archive.modules.credential.Credential;
import org.archive.modules.fetcher.FetchStats;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.IdentityCacheable;
import org.archive.util.ObjectIdentityCache;

/**
 * Represents a single remote "server".
 *
 * A server is a service on a host. There might be more than one service on a
 * host differentiated by a port number.
 *
 * @author gojomo
 */
public class CrawlServer implements Serializable, FetchStats.HasFetchStats, IdentityCacheable {
    private static final Logger logger =
        Logger.getLogger(CrawlServer.class.getName());
    private static final long serialVersionUID = 3L;

    public static final long ROBOTS_NOT_FETCHED = -1;
    /** only check if robots-fetch is perhaps superfluous 
     * after this many tries */
    public static final long MIN_ROBOTS_RETRIES = 3;

    private String server; // actually, host+port in the https case
    private int port;
    protected Robotstxt robotstxt;
    protected long robotsFetched = ROBOTS_NOT_FETCHED;
    protected boolean validRobots = false;
    protected FetchStats substats = new FetchStats();
    
    // how many consecutive connection errors have been encountered;
    // used to drive exponentially increasing retry timeout or decision
    // to 'freeze' entire class (queue) of URIs
    protected int consecutiveConnectionErrors = 0;

    /**
     * Set of credentials.
     */
    private transient Set<Credential> credentials =  null;

    /**
     * Creates a new CrawlServer object.
     *
     * @param h the host string for the server.
     */
    public CrawlServer(String h) {
        // TODO: possibly check for illegal host string
        server = h;
        int colonIndex = server.lastIndexOf(":");
        if (colonIndex < 0) {
            port = -1;
        } else {
            try {
                port = Integer.parseInt(server.substring(colonIndex + 1));
            } catch (NumberFormatException e) {
                port = -1;
            }
        }
    }

    public String toString() {
        return "CrawlServer("+server+")";
    }
    @Override
    public int hashCode() {
        return this.server != null ? this.server.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CrawlServer other = (CrawlServer) obj;
        if (this.server != other.server   // identity compare
                && (this.server == null 
                    || !this.server.equals(other.server))) {
            return false;
        }
        return true;
    }
    
    public Robotstxt getRobotstxt() {
        return robotstxt;
    }
    
    /** Update the robotstxt
    *
    * @param curi the crawl URI containing the fetched robots.txt
    * @throws IOException
    */
   public synchronized void updateRobots(CrawlURI curi) {

       robotsFetched = System.currentTimeMillis();
       
       boolean gotSomething = curi.getFetchType() == HTTP_GET 
           && (curi.getFetchStatus() > 0 || curi.getFetchStatus() == S_DEEMED_NOT_FOUND);
       
       
       if (!gotSomething && curi.getFetchAttempts() < MIN_ROBOTS_RETRIES) {
           // robots.txt lookup failed, still trying, no reason to consider IGNORE yet
           validRobots = false;
           return;
       }
              
       // special deeming for a particular kind of connection-lost (empty server response)
        if (curi.getFetchStatus() == S_CONNECT_LOST
                && CollectionUtils.exists(curi.getNonFatalFailures(),
                        PredicateUtils.instanceofPredicate(NoHttpResponseException.class))) {
            curi.setFetchStatus(S_DEEMED_NOT_FOUND);
            gotSomething = true;
        }
       
       if (!gotSomething) {
           // robots.txt fetch failed and exceptions (ignore/deeming) don't apply; no valid robots info yet
           validRobots = false;
           return;
       }
       
       int fetchStatus = curi.getFetchStatus();
       if (fetchStatus < 200 || fetchStatus >= 300) {
           // Not found or anything but a status code in the 2xx range is
           // treated as giving access to all of a sites' content.
           // This is the prevailing practice of Google, since 4xx
           // responses on robots.txt are usually indicative of a 
           // misconfiguration or blanket-block, not an intentional
           // indicator of partial blocking. 
           // TODO: consider handling server errors, redirects differently
           robotstxt = Robotstxt.NO_ROBOTS;
           validRobots = true;
           return;
       }

       InputStream contentBodyStream = null;
       try {
           BufferedReader reader;
           contentBodyStream = curi.getRecorder().getContentReplayInputStream();

           reader = new BufferedReader(new InputStreamReader(contentBodyStream));
           robotstxt = new Robotstxt(reader); 
           validRobots = true;
       } catch (IOException e) {
           robotstxt = Robotstxt.NO_ROBOTS;
           logger.log(Level.WARNING,"problem reading robots.txt for "+curi,e);
           validRobots = true;
           curi.getNonFatalFailures().add(e);
       } finally {
           IOUtils.closeQuietly(contentBodyStream);
       }
   }    

    /**
     * @return The server string which might include a port number.
     */
    public String getName() {
       return server;
    }

    /** Get the port number for this server.
     *
     * @return the port number or -1 if not known (uses default for protocol)
     */
    public int getPort() {
        return port;
    }


    public void incrementConsecutiveConnectionErrors() {
        this.consecutiveConnectionErrors++;
    }

    public void resetConsecutiveConnectionErrors() {
        this.consecutiveConnectionErrors = 0;
    }

    /**
     * @return Credential avatars for this server.  Returns null if none.
     */
    public Set<Credential> getCredentials() {
        return this.credentials;
    }

    /**
     * @return True if there are avatars attached to this instance.
     */
    public boolean hasCredentials() {
        return this.credentials != null && this.credentials.size() > 0;
    }

    /**
     * Add an avatar.
     *
     * @param ca Credential avatar to add to set of avatars.
     */
    public void addCredential(Credential cred) {
        if (this.credentials == null) {
            this.credentials = new HashSet<Credential>();
        }
        this.credentials.add(cred);
    }
    
	/**
     * If true then valid robots.txt information has been retrieved. If false
     * either no attempt has been made to fetch robots.txt or the attempt
     * failed.
     *
	 * @return Returns the validRobots.
	 */
	public synchronized boolean isValidRobots() {
		return validRobots;
	}
    
    /**
     * Get key to use doing lookup on server instances.
     * 
     * @param cauri  CandidateURI we're to get server key for.
     * @return String to use as server key.
     * @throws URIException
     */
    public static String getServerKey(UURI uuri) throws URIException {
        // TODO: evaluate if this is really necessary -- why not
        // make the server of a dns CandidateURI the looked-up domain,
        // also simplifying FetchDNS?
        String key = uuri.getAuthorityMinusUserinfo();
        if (key == null) {
            // Fallback for cases where getAuthority() fails (eg 'dns:'.
            // DNS UURIs have the 'domain' in the 'path' parameter, not
            // in the authority).
            key = uuri.getCurrentHierPath();
            if (key != null && !key.matches("[-_\\w\\.:]+")) {
                // Not just word chars and dots and colons and dashes and
                // underscores; throw away
                key = null;
            }
        }
        if (key != null && uuri.getScheme().equals(UURIFactory.HTTPS)) {
            // If https and no port specified, add default https port to
            // distinuish https from http server without a port.
            if (!key.matches(".+:[0-9]+")) {
                key += UURIFactory.HTTPS_PORT;
            }
        }
        return key;
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.CrawlSubstats.HasCrawlSubstats#getSubstats()
     */
    public FetchStats getSubstats() {
        return substats;
    }

    /**
     * Is the robots policy expired.
     *
     * This method will also return true if we haven't tried to get the
     * robots.txt for this server.
     *
     * @param curi
     * @return true if the robots policy is expired.
     */
    public synchronized boolean isRobotsExpired(int validityDuration) {
        if (robotsFetched == ROBOTS_NOT_FETCHED) {
            // Have not attempted to fetch robots
            return true;
        }
        long duration = validityDuration*1000L;
        if (duration == 0) {
            // When zero, robots should be valid forever
            return false;
        }
        if (robotsFetched + duration < System.currentTimeMillis()) {
            // Robots is still valid
            return true;
        }
        return false;
    }
    
    // Kryo support
//    public CrawlServer() {}
    public static void autoregisterTo(AutoKryo kryo) {
        kryo.register(CrawlServer.class);
        kryo.autoregister(FetchStats.class); 
        kryo.autoregister(Robotstxt.class);
        kryo.setRegistrationOptional(true); 
    }
    
    //
    // IdentityCacheable support
    //
    transient private ObjectIdentityCache<?> cache;
    @Override
    public String getKey() {
        return getName();
    }

    @Override
    public void makeDirty() {
        cache.dirtyKey(getKey());
    }

    @Override
    public void setIdentityCache(ObjectIdentityCache<?> cache) {
        this.cache = cache; 
    }
    
    transient private Map<String,String> httpAuthChallenges;
    public Map<String,String> getHttpAuthChallenges() {
        return httpAuthChallenges;
    }
    public void setHttpAuthChallenges(Map<String, String> httpAuthChallenges) {
        this.httpAuthChallenges = httpAuthChallenges;
    }
}
