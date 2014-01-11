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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.net.whois.WhoisClient;
import org.archive.bdb.BdbModule;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.Recorder;
import org.archive.util.TextUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.google.common.net.InternetDomainName;
import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * WHOIS Fetcher (RFC 3912). If this fetcher is enabled, Heritrix will attempt
 * WHOIS lookups on the topmost assigned domain and the IP address of each URL.
 * 
 * <h3>WHOIS URIs</h3>
 * <p>
 * There is no pre-existing, canonical specification for WHOIS URIs. What
 * follows is the the format that Heritrix uses, which we propose for general
 * use.
 * </p>
 * 
 * <p>
 * Syntax in ABNF as used in RFC 3986 <i>Uniform Resource Identifier (URI):
 * Generic Syntax</i>:
 * </p>
 * 
 * <blockquote>whoisurl = "whois:" [ "//" host [ ":" port ] "/" ] whoisquery</blockquote>
 * 
 * <p>
 * <b>whoisquery</b> is a url-encoded string. In ABNF,
 * <code>whoisquery = 1*pchar</code> where pchar is defined in RFC 3986.
 * <b>host</b> and <b>port</b> also as defined in RFC 3986.
 * </p>
 * 
 * <p>
 * To resolve a WHOIS URI which specifies host[:port], open a TCP connection to
 * the host at the specified port (default 43), send the query (whoisquery,
 * url-decoded) followed by CRLF, and read the response until the server closes
 * the connection. For more details see RFC 3912.
 * </p>
 * 
 * <p>
 * Resolution of a "serverless" WHOIS URI, which does not specify host[:port],
 * is implementation-dependent.
 * </p>
 * 
 * <h3>Serverless WHOIS URIs in Heritrix</h3>
 * 
 * <p>
 * For each non-WHOIS URI processed which has an authority, FetchWhois adds 1 or
 * 2 serverless WHOIS URIs to the CrawlURI's outlinks. These are
 * "whois:{ipAddress}" and, if the authority includes a hostname,
 * "whois:{topLevelDomain}". See {@link #addWhoisLinks(CrawlURI)}.
 * </p>
 * 
 * <p>
 * Heritrix resolves serverless WHOIS URIs by first querying an initial server,
 * then following referrals to other servers. In pseudocode:
 * 
 * <pre>
 * if query is an IPv4 address
 *     resolve whois://{@link #DEFAULT_IP_WHOIS_SERVER}/whoisquery</div>
 * else
 *     let domainSuffix = part of query after the last '.' (or the whole query if no '.'), url-encoded
 *     resolve whois://{@link #ULTRA_SUFFIX_WHOIS_SERVER}/domainSuffix
 * 
 * while last response refers to another server, i.e. matches regex {@link #WHOIS_SERVER_REGEX}
 *     if we have a special query formatting rule for this whois server, apply it - see {@link #specialQueryTemplates}
 *     resolve whois://referralServer/whoisquery
 * </pre>
 * 
 * <p>See {@link #deferOrFinishGeneric(CrawlURI, String)}</p>
 * 
 * @contributor nlevitt
 */
public class FetchWhois extends Processor implements CoreAttributeConstants,
        FetchStatusCodes, Lifecycle {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;

    private static Logger logger = Logger.getLogger(FetchWhois.class.getName());

    public static final String IP_ADDRESS_REGEX = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}";

    // North America-centric, but it should refer us to the right server
    // e.g. "ReferralServer: whois://whois.apnic.net" 
    protected static final String DEFAULT_IP_WHOIS_SERVER = "whois.arin.net";

    // look up "com" "net" "fr" "info" etc 
    protected static final String ULTRA_SUFFIX_WHOIS_SERVER = "whois.iana.org";
    
    // [whois://whois.arin.net/192.102.239.53] ReferralServer: whois://whois.apnic.net
    // [whois://whois.arin.net/208.49.199.10] ReferralServer: rwhois://rwhois.gblx.net:4321
    // [whois://whois.arin.net/195.154.120.129] ReferralServer: whois://whois.ripe.net:43
    // (obsolete) [whois://whois.iana.org/fr] Whois Server (port 43): whois.nic.fr
    // [whois://whois.iana.org/fr] whois:        whois.nic.fr
    // [whois://whois.verisign-grs.com/domain%201stbattalion9thmarinesfirebase.net]    Whois Server: whois.fastdomain.com
    // (false positive fixed) WHOIS lookup made at 23:48:04 13-Jan-2011
    protected static String WHOIS_SERVER_REGEX = "(?i)^\\s*(?:whois server|ReferralServer|whois)[^:]*:.*?([a-zA-Z0-9-]+\\.[a-zA-Z0-9.:-]+)/*$";

    protected enum UrlStatus {IN_PROGRESS, DONE};
    private transient Database whoisDb;
    private transient StoredSortedMap<String,String> referralServers;
    private transient StoredSortedMap<String,Integer> urlProgress;
    
    protected BdbModule bdb;

    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }

    protected Map<String,String> specialQueryTemplates;
    {
        // Default special templates. Keep commented out section of
        // profile-crawler-beans.xml in synch with this.
        specialQueryTemplates = new HashMap<String, String>();
        specialQueryTemplates.put("whois.verisign-grs.com", "domain %s");
        specialQueryTemplates.put("whois.arin.net", "z + %s");
        specialQueryTemplates.put("whois.denic.de", "-T dn %s");
    }
    public void setSpecialQueryTemplates(Map<String,String> m) {
        this.specialQueryTemplates.clear();
        this.specialQueryTemplates.putAll(m);
    }

    /**
     * If the socket is unresponsive for this number of milliseconds, give up.
     * Set to zero for no timeout (Not. recommended. Could hang a thread on an
     * unresponsive server). This timeout is used timing out socket opens and
     * for timing out each socket read. Make sure this value is &lt;
     * {@link #TIMEOUT_SECONDS} for optimal configuration: ensures at least one
     * retry read.
     */
    {
        setSoTimeoutMs(20*1000); // 20 seconds
    }
    public int getSoTimeoutMs() {
        return (Integer) kp.get("soTimeoutMs");
    }
    public void setSoTimeoutMs(int timeout) {
        kp.put("soTimeoutMs",timeout);
    }

    private boolean isRunning = false; 
    public void start() {
        if(isRunning()) {
            return;
        }
        
        try {
            BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
            dbConfig.setTransactional(false);

            boolean isRecovery = recoveryCheckpoint != null;
            dbConfig.setAllowCreate(!isRecovery);
            whoisDb = bdb.openDatabase("whoisKnowledge", dbConfig, isRecovery);

            referralServers = new StoredSortedMap<String, String>(whoisDb,
                    new StringBinding(), new StringBinding(), true);
            urlProgress = new StoredSortedMap<String, Integer>(whoisDb,
                    new StringBinding(), new IntegerBinding(), true);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        
        isRunning = true; 
    }

    public boolean isRunning() {
        return isRunning;
    }
    
    public void stop() {
        isRunning = false;

        // BdbModule will handle closing of DB
        // XXX happens at finish; move to teardown?
        bdb = null;
    }

    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) throws InterruptedException {
        if (curi.getUURI().getScheme().equals("whois")) {
            curi.setFetchBeginTime(System.currentTimeMillis());

            String whoisServer = getWhoisServer(curi);
            String whoisQuery = getWhoisQuery(curi);

            if (whoisServer == null) {
                // e.g. whois:foo.org
                ProcessResult ret = deferOrFinishGeneric(curi, whoisQuery);
                return ret;
            } else {
                // e.g. whois://whois.pir.org/foo.org
                fetch(curi, whoisServer, whoisQuery);
                return ProcessResult.PROCEED;
            }
            
        } else {
            addWhoisLinks(curi);
            return ProcessResult.PROCEED;
        }
    }

    // handle serverless whois url
    protected ProcessResult deferOrFinishGeneric(CrawlURI curi, String domainOrIp) {
        String tryThis = null;
        String ultraSuffix = domainOrIp.substring(domainOrIp.lastIndexOf('.') + 1).toLowerCase();

        if (referralServers.containsKey(domainOrIp)) {
            tryThis = "whois://" + referralServers.get(domainOrIp) + '/' + domainOrIp;
        } else if (TextUtils.getMatcher(IP_ADDRESS_REGEX, domainOrIp).matches()) {
            tryThis = makeWhoisUrl(DEFAULT_IP_WHOIS_SERVER, domainOrIp);
        } else if (referralServers.containsKey(ultraSuffix)) {
            tryThis = makeWhoisUrl(referralServers.get(ultraSuffix), domainOrIp);
        } else if (urlProgress.get(makeWhoisUrl(ULTRA_SUFFIX_WHOIS_SERVER, ultraSuffix)) == null) {
            tryThis = makeWhoisUrl(ULTRA_SUFFIX_WHOIS_SERVER, ultraSuffix);
        } else {
            logger.warning("apparently no whois server for \"" + domainOrIp + "\"");
            curi.setFetchStatus(S_OTHER_PREREQUISITE_FAILURE);
            return ProcessResult.PROCEED;
        }

        assert(tryThis != null);
        Integer progress = urlProgress.get(tryThis);
        if (tryThis == null || (progress != null && progress == UrlStatus.DONE.ordinal())) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("finished with generic serverless whois uri " + curi);
            }
            curi.setFetchStatus(S_WHOIS_GENERIC_FINISHED);
            return ProcessResult.PROCEED;
        } else {
            if (progress == null) {
                try {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine(curi + " marking prerequisite " + tryThis + " and deferring");
                    }
                    CrawlURI caUri = curi.markPrerequisite(tryThis);
                    caUri.setForceFetch(false);
                    urlProgress.put(tryThis, UrlStatus.IN_PROGRESS.ordinal());
                } catch (URIException e) {
                    throw new RuntimeException(e);
                }
                
            } else {
                // this shouldn't happen because prereqs should be crawled before we're attempted again
                curi.incrementDeferrals();
                curi.setFetchStatus(S_DEFERRED);
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(curi + ": prerequisite " + tryThis + " is in progress, deferring");
                }
            }
            
            return ProcessResult.FINISH;
        }
    }
    
    protected String makeWhoisUrl(String server, String principal) {
        try {
            String query;
            String template = specialQueryTemplates.get(server.toLowerCase());
            if (template != null) {
                query = template.replaceAll("%s", principal);
            } else {
                query = principal;
            }
            
            return "whois://" + server + "/" + URLEncoder.encode(query, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    protected void fetch(CrawlURI curi, String whoisServer, String whoisQuery) {
        WhoisClient client = new WhoisClient();
        Recorder recorder = curi.getRecorder();
        
        try {
            client.setConnectTimeout(getSoTimeoutMs());
            client.setDefaultTimeout(getSoTimeoutMs());
            
            if (curi.getUURI().getPort() > 0) {
                client.connect(whoisServer, curi.getUURI().getPort());
            } else {
                client.connect(whoisServer);
            }

            client.setSoTimeout(getSoTimeoutMs()); // must be after connect()
            
            curi.getData().put(CoreAttributeConstants.A_WHOIS_SERVER_IP, 
                    client.getRemoteAddress().getHostAddress());
            
            recorder.inputWrap(client.getInputStream(whoisQuery));

            // look for info about whois server in the response
            // XXX run regex on the whole thing, rather than line by line?
            BufferedReader reader = new BufferedReader(new InputStreamReader(recorder.getRecordedInput(), "ASCII"));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                Matcher matcher = TextUtils.getMatcher(WHOIS_SERVER_REGEX, line);
                if (matcher.find()) {
                    // gets rid of "domain " for whois.verisign-grs.com queries
                    String key = whoisQuery.replaceFirst("(\\S+\\s+)+", "").toLowerCase();
                    referralServers.put(key, matcher.group(1).toLowerCase());
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("added referral server " + matcher.group(1) + " to server list for " + key);
                    }
                }
            }

            curi.setContentType("text/plain");
            curi.setFetchStatus(S_WHOIS_SUCCESS);
        } catch (IOException e) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("failed to connect to whois server for uri " + curi + ": " + e);
            }
            curi.getNonFatalFailures().add(e);
            curi.setFetchStatus(S_CONNECT_FAILED);
        } finally {
            recorder.close();
            curi.setContentSize(recorder.getRecordedInput().getSize());
            logger.fine(curi + ": " + recorder.getRecordedInput().getSize() + " bytes read");

            if (client != null && client.isConnected()) try {
                client.disconnect();
            } catch (IOException e) {
                logger.fine("problem closing connection to whois server for uri " + curi + ": " + e);
            }

            urlProgress.put(curi.toString(), UrlStatus.DONE.ordinal());
        }
    }

    protected String getWhoisQuery(CrawlURI curi) {
        try {
            if (curi.getUURI().getAuthority() == null) {
                // whois:archive-it.org - returns "archive-it.org"
                return curi.getUURI().getPathQuery();
            } else {
                // whois://whois.pir.org/archive-it.org - getPathQuery() returns"/archive-it.org", so chop off "/"
                return curi.getUURI().getPathQuery().substring(1);
            }
        } catch (URIException e) {
            logger.log(Level.SEVERE, "Failed to get path/query from uri " + curi, e);
            return null;
        }
    }
    
    protected String getWhoisServer(CrawlURI curi) {
        String whoisServer = null;

        try {
            whoisServer = curi.getUURI().getHost();
            if (whoisServer != null && whoisServer.length() == 0) {
                whoisServer = null;
            }
        } catch (URIException e) {
            logger.warning("Failed to get host from uri " + curi + ": " + e);
            whoisServer = null;
        }

        return whoisServer;
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        // process all uris - non-whois uris get whois outlinks added
        return true;
    }

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    protected void addWhoisLink(CrawlURI curi, String query) {
        String whoisUrl = "whois:" + query;
        try {
            Link.add(curi, Integer.MAX_VALUE, whoisUrl, LinkContext.INFERRED_MISC, Hop.INFERRED);
        } catch (URIException e) {
            logger.log(Level.WARNING, "problem with url " + whoisUrl, e);
        }
    }

    /**
     * Adds outlinks to whois:{domain} and whois:{ipAddress} 
     */
    protected void addWhoisLinks(CrawlURI curi) throws InterruptedException {
        CrawlHost ch = serverCache.getHostFor(curi.getUURI());

        if (ch == null) {
            return;
        }

        if (ch.getIP() != null) {
            // do a whois lookup on the ip address
            addWhoisLink(curi, ch.getIP().getHostAddress());
        }

        if (InternetDomainName.isValidLenient(ch.getHostName())) {
            // do a whois lookup on the domain
            try {
                String topmostAssigned = InternetDomainName.fromLenient(ch.getHostName()).topPrivateDomain().name();
                addWhoisLink(curi, topmostAssigned);
            } catch (IllegalStateException e) {
                // java.lang.IllegalStateException: Not under a public suffix: mod.uk
                logger.warning("problem resolving topmost assigned domain, will try whois lookup on the plain hostname " + ch.getHostName() + " - " + e);
                addWhoisLink(curi, ch.getHostName());
            }
        }
    }
    
    @Override
    protected void innerProcess(CrawlURI uri) throws InterruptedException {
        throw new RuntimeException("this method shouldn't be called - should use innerProcessResult()");
    }

}
