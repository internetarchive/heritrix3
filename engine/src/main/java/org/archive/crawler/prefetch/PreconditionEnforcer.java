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
package org.archive.crawler.prefetch;

import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_PREREQUISITE_FAILURE;
import static org.archive.modules.fetcher.FetchStatusCodes.S_ROBOTS_PRECLUDED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_ROBOTS_PREREQUISITE_FAILURE;
import static org.archive.modules.fetcher.FetchStatusCodes.S_UNFETCHABLE_URI;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.credential.Credential;
import org.archive.modules.credential.CredentialStore;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.RobotsPolicy;
import org.archive.modules.net.ServerCache;
import org.archive.net.UURI;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Ensures the preconditions for a fetch -- such as DNS lookup 
 * or acquiring and respecting a robots.txt policy -- are
 * satisfied before a URI is passed to subsequent stages.
 *
 * @author gojomo
 */
public class PreconditionEnforcer extends Processor  {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;
    private static final Logger logger =
        Logger.getLogger(PreconditionEnforcer.class.getName());

    /**
     * The minimum interval for which a dns-record will be considered 
     * valid (in seconds). If the record's DNS TTL is larger, that will 
     * be used instead.
     */
    {
        setIpValidityDurationSeconds(6*60*60); // 6 hours
    }
    public int getIpValidityDurationSeconds() {
        return (Integer) kp.get("ipValidityDurationSeconds");
    }
    public void setIpValidityDurationSeconds(int duration) {
        kp.put("ipValidityDurationSeconds",duration);
    }

    /**
     * The time in seconds that fetched robots.txt information is considered to
     * be valid. If the value is set to '0', then the robots.txt information
     * will never expire.
     */
    {
        setRobotsValidityDurationSeconds(24*60*60); // 24 hours
    }
    public int getRobotsValidityDurationSeconds() {
        return (Integer) kp.get("robotsValidityDurationSeconds");
    }
    public void setRobotsValidityDurationSeconds(int duration) {
        kp.put("robotsValidityDurationSeconds",duration);
    }

    /**
     * Whether to only calculate the robots status of an URI, without actually
     * applying any exclusions found. If true, exlcuded URIs will only be
     * annotated in the crawl.log, but still fetched. Default is false.
     */
    {
        setCalculateRobotsOnly(false);
    }
    public boolean getCalculateRobotsOnly() {
        return (Boolean) kp.get("calculateRobotsOnly");
    }
    public void setCalculateRobotsOnly(boolean calcOnly) {
        kp.put("calculateRobotsOnly",calcOnly);
    }   
    
    /**
     * Auto-discovered module providing configured (or overridden)
     * User-Agent value and RobotsHonoringPolicy
     */
    protected CrawlMetadata metadata;
    public CrawlMetadata getMetadata() {
        return metadata;
    }
    @Autowired
    public void setMetadata(CrawlMetadata provider) {
        this.metadata = provider;
    }
    
    {
        // initialize with empty store so declaration not required
        setCredentialStore(new CredentialStore());
    }
    public CredentialStore getCredentialStore() {
        return (CredentialStore) kp.get("credentialStore");
    }
    @Autowired(required=false)
    public void setCredentialStore(CredentialStore credentials) {
        kp.put("credentialStore",credentials);
    }
    
    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    protected CrawlerLoggerModule loggerModule;
    public CrawlerLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(CrawlerLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }
    
    public PreconditionEnforcer() {
        super();
    }
    
    @Override
    protected boolean shouldProcess(CrawlURI puri) {
        return (puri instanceof CrawlURI);
    }
    
    
    @Override
    protected void innerProcess(CrawlURI puri) {
        throw new AssertionError();
    }

    
    @Override
    protected ProcessResult innerProcessResult(CrawlURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        if (considerDnsPreconditions(curi)) {
            return ProcessResult.FINISH;
        }

        // make sure we only process schemes we understand (i.e. not dns)
        String scheme = curi.getUURI().getScheme().toLowerCase();
        if (! (scheme.equals("http") || scheme.equals("https"))) {
            logger.fine("PolitenessEnforcer doesn't understand uri's of type " +
                scheme + " (ignoring)");
            return ProcessResult.PROCEED;
        }

        if (considerRobotsPreconditions(curi)) {
            return ProcessResult.FINISH;
        }

        if (!curi.isPrerequisite() && credentialPrecondition(curi)) {
            return ProcessResult.FINISH;
        }

        // OK, it's allowed

        // For all curis that will in fact be fetched, set appropriate delays.
        // TODO: SOMEDAY: allow per-host, per-protocol, etc. factors
        // curi.setDelayFactor(getDelayFactorFor(curi));
        // curi.setMinimumDelay(getMinimumDelayFor(curi));

        return ProcessResult.PROCEED;
    }

    /**
     * Consider the robots precondition.
     *
     * @param curi CrawlURI we're checking for any required preconditions.
     * @return True, if this <code>curi</code> has a precondition or processing
     *         should be terminated for some other reason.  False if
     *         we can proceed to process this url.
     */
    protected boolean considerRobotsPreconditions(CrawlURI curi) {
        // treat /robots.txt fetches specially
        UURI uuri = curi.getUURI();
        try {
            if (uuri != null && uuri.getPath() != null &&
                    curi.getUURI().getPath().equals("/robots.txt")) {
                // allow processing to continue
                curi.setPrerequisite(true);
                return false;
            }
        } catch (URIException e) {
            logger.severe("Failed get of path for " + curi);
        }
        
        CrawlServer cs = serverCache.getServerFor(curi.getUURI());
        // require /robots.txt if not present
        if (cs.isRobotsExpired(getRobotsValidityDurationSeconds())) {
        	// Need to get robots
            if (logger.isLoggable(Level.FINE)) {
                logger.fine( "No valid robots for " + cs  +
                    "; deferring " + curi);
            }

            // Robots expired - should be refetched even though its already
            // crawled.
            try {
                String prereq = curi.getUURI().resolve("/robots.txt").toString();
                curi.markPrerequisite(prereq);
            }
            catch (URIException e1) {
                logger.severe("Failed resolve using " + curi);
                throw new RuntimeException(e1); // shouldn't ever happen
            }
            return true;
        }
        // test against robots.txt if available
        if (cs.isValidRobots()) {
            String ua = metadata.getUserAgent();
            RobotsPolicy robots = metadata.getRobotsPolicy();
            if(!robots.allows(ua, curi, cs.getRobotstxt())) {
                if(getCalculateRobotsOnly()) {
                    // annotate URI as excluded, but continue to process normally
                    curi.getAnnotations().add("robotExcluded");
                    return false; 
                }
                // mark as precluded; in FetchHTTP, this will
                // prevent fetching and cause a skip to the end
                // of processing (unless an intervening processor
                // overrules)
                curi.setFetchStatus(S_ROBOTS_PRECLUDED);
                curi.setError("robots.txt exclusion");
                logger.fine("robots.txt precluded " + curi);
                return true;
            }
            return false;
        }
        // No valid robots found => Attempt to get robots.txt failed
//        curi.skipToPostProcessing();
        curi.setFetchStatus(S_ROBOTS_PREREQUISITE_FAILURE);
        curi.setError("robots.txt prerequisite failed");
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("robots.txt prerequisite failed " + curi);
        }
        return true;
    }

    /**
     * @param curi CrawlURI whose dns prerequisite we're to check.
     * @return true if no further processing in this module should occur
     */
    protected boolean considerDnsPreconditions(CrawlURI curi) {
        if(curi.getUURI().getScheme().equals("dns")){
            // DNS URIs never have a DNS precondition
            curi.setPrerequisite(true);
            return false; 
        } else if (curi.getUURI().getScheme().equals("whois")) {
            return false;
        }
        
        CrawlServer cs = serverCache.getServerFor(curi.getUURI());
        if(cs == null) {
            curi.setFetchStatus(S_UNFETCHABLE_URI);
//            curi.skipToPostProcessing();
            return true;
        }

        // If we've done a dns lookup and it didn't resolve a host
        // cancel further fetch-processing of this URI, because
        // the domain is unresolvable
        CrawlHost ch = serverCache.getHostFor(curi.getUURI());
        if (ch == null || ch.hasBeenLookedUp() && ch.getIP() == null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine( "no dns for " + ch +
                    " cancelling processing for CrawlURI " + curi.toString());
            }
            curi.setFetchStatus(S_DOMAIN_PREREQUISITE_FAILURE);
//            curi.skipToPostProcessing();
            return true;
        }

        // If we haven't done a dns lookup  and this isn't a dns uri
        // shoot that off and defer further processing
        if (isIpExpired(curi) && !curi.getUURI().getScheme().equals("dns")) {
            logger.fine("Deferring processing of CrawlURI " + curi.toString()
                + " for dns lookup.");
            String preq = "dns:" + ch.getHostName();
            try {
                curi.markPrerequisite(preq);
            } catch (URIException e) {
                throw new RuntimeException(e); // shouldn't ever happen
            }
            return true;
        }
        
        // DNS preconditions OK
        return false;
    }

    /** Return true if ip should be looked up.
     *
     * @param curi the URI to check.
     * @return true if ip should be looked up.
     */
    public boolean isIpExpired(CrawlURI curi) {
        CrawlHost host = serverCache.getHostFor(curi.getUURI());
        if (!host.hasBeenLookedUp()) {
            // IP has not been looked up yet.
            return true;
        }

        if (host.getIpTTL() == CrawlHost.IP_NEVER_EXPIRES) {
            // IP never expires (numeric IP)
            return false;
        }

        long duration = getIpValidityDurationSeconds();
        if (duration == 0) {
            // Never expire ip if duration is null (set by user or more likely,
            // set to zero in case where we tried in FetchDNS but failed).
            return false;
        }
        
        long ttl = host.getIpTTL();
        if (ttl > duration) {
            // Use the larger of the operator-set minimum duration 
            // or the DNS record TTL
            duration = ttl;
        }

        // Duration and ttl are in seconds.  Convert to millis.
        if (duration > 0) {
            duration *= 1000;
        }

        return (duration + host.getIpFetched()) < System.currentTimeMillis();
    }

   /**
    * Consider credential preconditions.
    *
    * Looks to see if any credential preconditions (e.g. html form login
    * credentials) for this <code>CrawlServer</code>. If there are, have they
    * been run already? If not, make the running of these logins a precondition
    * of accessing any other url on this <code>CrawlServer</code>.
    *
    * <p>
    * One day, do optimization and avoid running the bulk of the code below.
    * Argument for running the code everytime is that overrides and refinements
    * may change what comes back from credential store.
    *
    * @param curi CrawlURI we're checking for any required preconditions.
    * @return True, if this <code>curi</code> has a precondition that needs to
    *         be met before we can proceed. False if we can precede to process
    *         this url.
    */
    protected boolean credentialPrecondition(final CrawlURI curi) {

        boolean result = false;

        CredentialStore cs = getCredentialStore();
        if (cs == null) {
            logger.severe("No credential store for " + curi);
            return result;
        }

        for (Credential c: cs.getAll()) {
            if (c.isPrerequisite(curi)) {
                // This credential has a prereq. and this curi is it.  Let it
                // through.  Add its avatar to the curi as a mark.  Also, does
                // this curi need to be posted?  Note, we do this test for
                // is it a prereq BEFORE we do the check that curi is of the
                // credential domain because such as yahoo have you go to
                // another domain altogether to login.
                c.attach(curi);
                curi.setFetchType(CrawlURI.FetchType.HTTP_POST);
                break;
            }

            if (!c.rootUriMatch(serverCache, curi)) {
                continue;
            }

            if (!c.hasPrerequisite(curi)) {
                continue;
            }

            if (!authenticated(c, curi)) {
                // Han't been authenticated.  Queue it and move on (Assumption
                // is that we can do one authentication at a time -- usually one
                // html form).
                String prereq = c.getPrerequisite(curi);
                if (prereq == null || prereq.length() <= 0) {
                    CrawlServer server = serverCache.getServerFor(curi.getUURI());
                    logger.severe(server.getName() + " has "
                        + " credential(s) of type " + c + " but prereq"
                        + " is null.");
                } else {
                    try {
                        curi.markPrerequisite(prereq);
                    } catch (URIException e) {
                        logger.severe("unable to set credentials prerequisite "+prereq);
                        loggerModule.logUriError(e,curi.getUURI(),prereq);
                        return false; 
                    }
                    result = true;
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Queueing prereq " + prereq + " of type " +
                            c + " for " + curi);
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * Has passed credential already been authenticated.
     *
     * @param credential Credential to test.
     * @param curi CrawlURI.
     * @return True if already run.
     */
    protected boolean authenticated(final Credential credential, final CrawlURI curi) {
        CrawlServer server = serverCache.getServerFor(curi.getUURI());
        if (!server.hasCredentials()) {
            return false;
        }
        Set<Credential> credentials = server.getCredentials();
        for (Credential cred: credentials) {
            if (cred.getKey().equals(credential.getKey()) 
                    && cred.getClass().isInstance(credential)) {
                return true; 
            }
        }
        return false;
    }

}
