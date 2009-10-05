/* Copyright (C) 2003 Internet Archive.
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
 *
 * SimplePolitenessEnforcer.java
 * Created on May 22, 2003
 *
 * $Header$
 */
package org.archive.crawler.prefetch;

import static org.archive.modules.fetcher.FetchStatusCodes.S_DEFERRED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_PREREQUISITE_FAILURE;
import static org.archive.modules.fetcher.FetchStatusCodes.S_ROBOTS_PRECLUDED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_ROBOTS_PREREQUISITE_FAILURE;
import static org.archive.modules.fetcher.FetchStatusCodes.S_UNFETCHABLE_URI;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.credential.Credential;
import org.archive.modules.credential.CredentialAvatar;
import org.archive.modules.credential.CredentialStore;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.fetcher.UserAgentProvider;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.modules.net.ServerCacheUtil;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Ensures the preconditions for a fetch -- such as DNS lookup 
 * or acquiring and respecting a robots.txt policy -- are
 * satisfied before a URI is passed to subsequent stages.
 *
 * @author gojomo
 */
public class PreconditionEnforcer extends Processor  {

    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(PreconditionEnforcer.class.getName());


    public UserAgentProvider getUserAgentProvider() {
        return (UserAgentProvider) kp.get("userAgentProvider");
    }
    @Autowired
    public void setUserAgentProvider(UserAgentProvider provider) {
        kp.put("userAgentProvider",provider);
    }
    
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
    public void setCalculateRobotsOnly(boolean recheck) {
        kp.put("calculateRobotsOnly",recheck);
    }   
    
    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    public CredentialStore getCredentialStore() {
        return (CredentialStore) kp.get("credentialStore");
    }
    @Autowired
    public void setCredentialStore(CredentialStore credentials) {
        kp.put("credentialStore",credentials);
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
     *         we can precede to process this url.
     */
    private boolean considerRobotsPreconditions(CrawlURI curi) {
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
        
        CrawlServer cs = getServerFor(curi);
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
                markPrerequisite(curi, prereq);
            }
            catch (URIException e1) {
                logger.severe("Failed resolve using " + curi);
                throw new RuntimeException(e1); // shouldn't ever happen
            }
            return true;
        }
        // test against robots.txt if available
        if (cs.isValidRobots()) {
            String ua = getUserAgentProvider().getUserAgent();
            if(cs.getRobots().disallows(curi, ua)) {
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
    private boolean considerDnsPreconditions(CrawlURI curi) {
        if(curi.getUURI().getScheme().equals("dns")){
            // DNS URIs never have a DNS precondition
            curi.setPrerequisite(true);
            return false; 
        }
        
        CrawlServer cs = getServerFor(curi);
        if(cs == null) {
            curi.setFetchStatus(S_UNFETCHABLE_URI);
//            curi.skipToPostProcessing();
            return true;
        }

        // If we've done a dns lookup and it didn't resolve a host
        // cancel further fetch-processing of this URI, because
        // the domain is unresolvable
        CrawlHost ch = getHostFor(curi);
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
                markPrerequisite(curi, preq);
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
        CrawlHost host = getHostFor(curi);
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
    private boolean credentialPrecondition(final CrawlURI curi) {

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
                    CrawlServer server = getServerFor(curi);
                    logger.severe(server.getName() + " has "
                        + " credential(s) of type " + c + " but prereq"
                        + " is null.");
                } else {
                    try {
                        markPrerequisite(curi, prereq);
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
    private boolean authenticated(final Credential credential,
            final CrawlURI curi) {
        boolean result = false;
        CrawlServer server = getServerFor(curi);
        if (!server.hasCredentialAvatars()) {
            return result;
        }
        Set<CredentialAvatar> avatars = server.getCredentialAvatars();
        for (CredentialAvatar ca: avatars) {
            String key = null;
            key = credential.getKey();
            if (ca.match(credential.getClass(), key)) {
                result = true;
            }
        }
        return result;
    }


    /**
     * Do all actions associated with setting a <code>CrawlURI</code> as
     * requiring a prerequisite.
     *
     * @param lastProcessorChain Last processor chain reference.  This chain is
     * where this <code>CrawlURI</code> goes next.
     * @param preq Object to set a prerequisite.
     * @throws URIException
     */
    private void markPrerequisite(CrawlURI curi, String preq) 
    throws URIException {
        UURI src = curi.getUURI();
        UURI dest = UURIFactory.getInstance(preq);
        LinkContext lc = LinkContext.PREREQ_MISC;
        Hop hop = Hop.PREREQ;
        Link link = new Link(src, dest, lc, hop);
        CrawlURI caUri = curi.createCrawlURI(curi.getBaseURI(), link);
        // TODO: consider moving some of this to candidate-handling
        int prereqPriority = curi.getSchedulingDirective() - 1;
        if (prereqPriority < 0) {
            prereqPriority = 0;
            logger.severe("Unable to promote prerequisite " + caUri +
                " above " + this);
        }
        caUri.setSchedulingDirective(prereqPriority);
        caUri.setForceFetch(true);
        curi.setPrerequisiteUri(caUri);
        curi.incrementDeferrals();
        curi.setFetchStatus(S_DEFERRED);
        //skipToPostProcessing();
    }
    
    
    private CrawlServer getServerFor(CrawlURI curi) {
        return ServerCacheUtil.getServerFor(serverCache, curi.getUURI());
    }
    
    
    private CrawlHost getHostFor(CrawlURI curi) {
        return ServerCacheUtil.getHostFor(serverCache, curi.getUURI());
    }
}
