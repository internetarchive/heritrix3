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

import static org.archive.modules.CrawlURI.FetchType.HTTP_POST;
import static org.archive.modules.fetcher.FetchErrors.HEADER_TRUNC;
import static org.archive.modules.fetcher.FetchErrors.LENGTH_TRUNC;
import static org.archive.modules.fetcher.FetchErrors.TIMER_TRUNC;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_FAILED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_LOST;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_PREREQUISITE_FAILURE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ETAG_HEADER;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_FETCH_HISTORY;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_REFERENCE_LENGTH;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_STATUS;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.HttpVersion;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.auth.AuthChallengeParser;
import org.apache.commons.httpclient.auth.AuthPolicy;
import org.apache.commons.httpclient.auth.AuthScheme;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.auth.MalformedChallengeException;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.commons.httpclient.protocol.ProtocolSocketFactory;
import org.apache.commons.lang.StringUtils;
import org.archive.httpclient.ConfigurableX509TrustManager;
import org.archive.httpclient.ConfigurableX509TrustManager.TrustLevel;
import org.archive.httpclient.HttpRecorderGetMethod;
import org.archive.httpclient.HttpRecorderMethod;
import org.archive.httpclient.HttpRecorderPostMethod;
import org.archive.httpclient.SingleHttpConnectionManager;
import org.archive.io.RecorderLengthExceededException;
import org.archive.io.RecorderTimeoutException;
import org.archive.io.RecorderTooMuchHeaderException;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.credential.Credential;
import org.archive.modules.credential.CredentialStore;
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.archive.modules.deciderules.AcceptDecideRule;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.util.Recorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

/**
 * HTTP fetcher that uses <a
 * href="http://jakarta.apache.org/commons/httpclient/">Apache Jakarta Commons
 * HttpClient</a> library.
 * 
 * @contributor gojomo
 * @contributor Igor Ranitovic
 * @contributor stack
 * @contributor others
 * @version $Id$
 */
public class FetchHTTP extends Processor implements Lifecycle {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;
    private static Logger logger = Logger.getLogger(FetchHTTP.class.getName());

    /**
     * Proxy host IP (set only if needed).
     */
    {
        setHttpProxyHost("");
    }
    public String getHttpProxyHost() {
        return (String) kp.get("httpProxyHost");
    }
    public void setHttpProxyHost(String host) {
        kp.put("httpProxyHost",host);
    }

    /**
     * Proxy port (set only if needed).
     */
    {
        setHttpProxyPort(0);
    }
    public int getHttpProxyPort() {
        return (Integer) kp.get("httpProxyPort");
    }
    public void setHttpProxyPort(int port) {
        kp.put("httpProxyPort",port);
    }

    /**
     * Proxy user (set only if needed).
     */
    {
        setHttpProxyUser("");
    }
    public String getHttpProxyUser() {
        return (String) kp.get("httpProxyUser");
    }
    public void setHttpProxyUser(String user) {
        kp.put("httpProxyUser",user);
    }

    /**
     * Proxy password (set only if needed).
     */
    {
        setHttpProxyPassword("");
    }
    public String getHttpProxyPassword() {
        return (String) kp.get("httpProxyPassword");
    }
    public void setHttpProxyPassword(String password) {
        kp.put("httpProxyPassword",password);
    }

    /**
     * If the fetch is not completed in this number of seconds, give up (and
     * retry later).
     */
    {
        setTimeoutSeconds(20*60); // 20 minutes
    }
    public int getTimeoutSeconds() {
        return (Integer) kp.get("timeoutSeconds");
    }
    public void setTimeoutSeconds(int timeout) {
        kp.put("timeoutSeconds",timeout);
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

    /**
     * Maximum length in bytes to fetch. Fetch is truncated at this length. A
     * value of 0 means no limit.
     */
    {
        setMaxLengthBytes(0L); // no limit
    }
    public long getMaxLengthBytes() {
        return (Long) kp.get("maxLengthBytes");
    }
    public void setMaxLengthBytes(long timeout) {
        kp.put("maxLengthBytes",timeout);
    }

    /**
     * Accept Headers to include in each request. Each must be the complete
     * header, e.g., 'Accept-Language: en'. (Thus, this can also be used to
     * other headers not beginning 'Accept-' as well.) By default heritrix sends
     * an Accept header similar to what a typical browser would send (the value
     * comes from Firefox 4.0).
     */
    {
        setAcceptHeaders(Arrays.asList("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
    }
    @SuppressWarnings("unchecked")
    public List<String> getAcceptHeaders() {
        return (List<String>) kp.get("acceptHeaders");
    }
    public void setAcceptHeaders(List<String> headers) {
        kp.put("acceptHeaders",headers);
    }
    
    /**
     * The character encoding to use for files that do not have one specified in
     * the HTTP response headers. Default: ISO-8859-1.
     */
    {
        setDefaultEncoding("ISO-8859-1");
    }
    public String getDefaultEncoding() {
        return getDefaultCharset().name();
    }
    public void setDefaultEncoding(String encoding) {
        kp.put("defaultEncoding",Charset.forName(encoding));
    }
    public Charset getDefaultCharset() {
        return (Charset)kp.get("defaultEncoding");
    }

    /**
     * Whether or not to perform an on-the-fly digest hash of retrieved
     * content-bodies.
     */
    {
        setDigestContent(true);
    }
    public boolean getDigestContent() {
        return (Boolean) kp.get("digestContent");
    }
    public void setDigestContent(boolean digest) {
        kp.put("digestContent",digest);
    }
 
    /**
     * Which algorithm (for example MD5 or SHA-1) to use to perform an
     * on-the-fly digest hash of retrieved content-bodies.
     */
    protected String digestAlgorithm = "sha1"; 
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }
    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    /**
     * The maximum KB/sec to use when fetching data from a server. The default
     * of 0 means no maximum.
     */
    {
        setMaxFetchKBSec(0); // no limit
    }
    public int getMaxFetchKBSec() {
        return (Integer) kp.get("maxFetchKBSec");
    }
    public void setMaxFetchKBSec(int rate) {
        kp.put("maxFetchKBSec",rate);
    }

    public UserAgentProvider getUserAgentProvider() {
        return (UserAgentProvider) kp.get("userAgentProvider");
    }
    @Autowired
    public void setUserAgentProvider(UserAgentProvider provider) {
        kp.put("userAgentProvider",provider);
    }

    /**
     * SSL certificate trust level. Range is from the default 'open' (trust all
     * certs including expired, selfsigned, and those for which we do not have a
     * CA) through 'loose' (trust all valid certificates including selfsigned),
     * 'normal' (all valid certificates not including selfsigned) to 'strict'
     * (Cert is valid and DN must match servername).
     */
    {
        setSslTrustLevel(TrustLevel.OPEN);
    }
    public TrustLevel getSslTrustLevel() {
        return (TrustLevel) kp.get("sslTrustLevel");
    }
    public void setSslTrustLevel(TrustLevel trustLevel) {
        kp.put("sslTrustLevel",trustLevel);
    }

    private transient HttpClient http = null;

    /**
     * How many 'instant retries' of HttpRecoverableExceptions have occurred
     * 
     * Would like it to be 'long', but longs aren't atomic
     */
    private int recoveryRetries = 0;


    /**
     * DecideRules applied after receipt of HTTP response headers but before we
     * start to download the body. If any filter returns FALSE, the fetch is
     * aborted. Prerequisites such as robots.txt by-pass filtering (i.e. they
     * cannot be midfetch aborted.
     */
    {
        setShouldFetchBodyRule(new AcceptDecideRule());
    }
    public DecideRule getShouldFetchBodyRule() {
        return (DecideRule) kp.get("shouldFetchBodyRule");
    }
    public void setShouldFetchBodyRule(DecideRule rule) {
        kp.put("shouldFetchBodyRule", rule);
    }

    // see [ 1379040 ] regex for midfetch filter not being stored in crawl order
    // http://sourceforge.net/support/tracker.php?aid=1379040
    // this.midfetchfilters.setExpertSetting(true);

    /**
     * What to log if midfetch abort.
     */
    private static final String MIDFETCH_ABORT_LOG = "midFetchAbort";

    /**
     * Use HTTP/1.1. Note: even when offering an HTTP/1.1 request, 
     * Heritrix may not properly handle persistent/keep-alive connections, 
     * so the sendConnectionClose parameter should remain 'true'. 
     */
    {
        setUseHTTP11(false);
    }
    public boolean getUseHTTP11() {
        return (Boolean) kp.get("useHTTP11");
    }
    public void setUseHTTP11(boolean useHTTP11) {
        kp.put("useHTTP11",useHTTP11);
    }
    
    /**
     * Set headers to accept compressed responses. 
     */
    {
        setAcceptCompression(false);
    }
    public boolean getAcceptCompression() {
        return (Boolean) kp.get("acceptCompression");
    }
    public void setAcceptCompression(boolean acceptCompression) {
        kp.put("acceptCompression",acceptCompression);
    }
    
    /**
     * Send 'Connection: close' header with every request.
     */
    {
        setSendConnectionClose(true);
    }
    public boolean getSendConnectionClose() {
        return (Boolean) kp.get("sendConnectionClose");
    }
    public void setSendConnectionClose(boolean sendClose) {
        kp.put("sendConnectionClose",sendClose);
    }

    private static final Header HEADER_SEND_CONNECTION_CLOSE = new Header(
            "Connection", "close");

    /**
     * Send 'Referer' header with every request.
     * <p>
     * The 'Referer' header contans the location the crawler came from, the page
     * the current URI was discovered in. The 'Referer' usually is logged on the
     * remote server and can be of assistance to webmasters trying to figure how
     * a crawler got to a particular area on a site.
     */
    {
        setSendReferer(true);
    }
    public boolean getSendReferer() {
        return (Boolean) kp.get("sendReferer");
    }
    public void setSendReferer(boolean sendClose) {
        kp.put("sendReferer",sendClose);
    }

    /**
     * Send 'Range' header when a limit ({@link #MAX_LENGTH_BYTES}) on
     * document size.
     * <p>
     * Be polite to the HTTP servers and send the 'Range' header, stating that
     * you are only interested in the first n bytes. Only pertinent if
     * {@link #MAX_LENGTH_BYTES} &gt; 0. Sending the 'Range' header results in a
     * '206 Partial Content' status response, which is better than just cutting
     * the response mid-download. On rare occasion, sending 'Range' will
     * generate '416 Request Range Not Satisfiable' response.
     */
    {
        setSendRange(false);
    }
    public boolean getSendRange() {
        return (Boolean) kp.get("sendRange");
    }
    public void setSendRange(boolean sendRange) {
        kp.put("sendRange",sendRange);
    }
    
    /**
     * Send 'If-Modified-Since' header, if previous 'Last-Modified' fetch
     * history information is available in URI history.
     */
    {
        setSendIfModifiedSince(true);
    }
    public boolean getSendIfModifiedSince() {
        return (Boolean) kp.get("sendIfModifiedSince");
    }
    public void setSendIfModifiedSince(boolean sendIfModifiedSince) {
        kp.put("sendIfModifiedSince",sendIfModifiedSince);
    }

    /**
     * Send 'If-None-Match' header, if previous 'Etag' fetch history information
     * is available in URI history.
     */
    {
        setSendIfNoneMatch(true);
    }
    public boolean getSendIfNoneMatch() {
        return (Boolean) kp.get("sendIfNoneMatch");
    }
    public void setSendIfNoneMatch(boolean sendIfNoneMatch) {
        kp.put("sendIfNoneMatch",sendIfNoneMatch);
    }
    
    public static final String REFERER = "Referer";

    public static final String RANGE = "Range";

    public static final String RANGE_PREFIX = "bytes=0-";

    public static final String HTTP_SCHEME = "http";

    public static final String HTTPS_SCHEME = "https";

    
    protected CookieStorage cookieStorage = new BdbCookieStorage();
    @Autowired(required=false)
    public void setCookieStorage(CookieStorage storage) {
        this.cookieStorage = storage; 
    }
    public CookieStorage getCookieStorage() {
        return this.cookieStorage;
    }

    /**
     * Disable cookie handling.
     */
    {
        setIgnoreCookies(false);
    }
    public boolean getIgnoreCookies() {
        return (Boolean) kp.get("ignoreCookies");
    }
    public void setIgnoreCookies(boolean ignoreCookies) {
        kp.put("ignoreCookies",ignoreCookies);
    }

    /**
     * Local IP address or hostname to use when making connections (binding
     * sockets). When not specified, uses default local address(es).
     */
    public String getHttpBindAddress(){
        return (String) kp.get(HTTP_BIND_ADDRESS);
    }
    public void setHttpBindAddress(String address) {
        kp.put(HTTP_BIND_ADDRESS, address);
    }
    public static final String HTTP_BIND_ADDRESS = "httpBindAddress";

    /**
     * Used to store credentials.
     */
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
    
    /**
     * Used to do DNS lookups.
     */
    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    static {
        Protocol.registerProtocol("http", new Protocol("http",
                new HeritrixProtocolSocketFactory(), 80));
        try {
            ProtocolSocketFactory psf = new HeritrixSSLProtocolSocketFactory();
            Protocol p = new Protocol("https", psf, 443); 
            Protocol.registerProtocol("https", p);
        } catch (KeyManagementException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
     

    // static final String SERVER_CACHE_KEY = "heritrix.server.cache";
    static final String SSL_FACTORY_KEY = "heritrix.ssl.factory";

    /***************************************************************************
     * Socket factory that has the configurable trust manager installed.
     */
    private transient SSLSocketFactory sslfactory = null;

    /**
     * Constructor.
     */
    public FetchHTTP() {
    }

    protected void innerProcess(final CrawlURI curi)
            throws InterruptedException {
        // Note begin time
        curi.setFetchBeginTime(System.currentTimeMillis());

        // Get a reference to the HttpRecorder that is set into this ToeThread.
        Recorder rec = curi.getRecorder();

        // Shall we get a digest on the content downloaded?
        boolean digestContent = getDigestContent();
        String algorithm = null;
        if (digestContent) {
            algorithm = getDigestAlgorithm();
            rec.getRecordedInput().setDigest(algorithm);
        } else {
            // clear
            rec.getRecordedInput().setDigest((MessageDigest)null);
        }

        // Below we do two inner classes that add check of midfetch
        // filters just as we're about to receive the response body.
        String curiString = curi.getUURI().toString();
        HttpMethodBase method = null;
        if (curi.getFetchType() == HTTP_POST) {
            method = new HttpRecorderPostMethod(curiString, rec) {
                protected void readResponseBody(HttpState state,
                        HttpConnection conn) throws IOException, HttpException {
                    addResponseContent(this, curi);
                    if (checkMidfetchAbort(curi, this.httpRecorderMethod, conn)) {
                        doAbort(curi, this, MIDFETCH_ABORT_LOG);
                    } else {
                        super.readResponseBody(state, conn);
                    }
                }
            };
        } else {
            method = new HttpRecorderGetMethod(curiString, rec) {
                protected void readResponseBody(HttpState state,
                        HttpConnection conn) throws IOException, HttpException {
                    addResponseContent(this, curi);
                    if (checkMidfetchAbort(curi, this.httpRecorderMethod, conn)) {
                        doAbort(curi, this, MIDFETCH_ABORT_LOG);
                    } else {
                        super.readResponseBody(state, conn);
                    }
                }
            };
        }
        
        // Save method into curi too. Midfetch filters may want to leverage
        // info in here.
        curi.setHttpMethod(method);

        HostConfiguration customConfigOrNull = configureMethod(curi, method);

        // Populate credentials. Set config so auth. is not automatic.
        boolean addedCredentials = populateCredentials(curi, method);
        if (http.getState().getProxyCredentials(new AuthScope(getProxyHost(), getProxyPort())) != null) {
            addedCredentials = true;
        }
        
        // prep POST submit-data, if present
        if (curi.getFetchType()==FetchType.HTTP_POST 
                && curi.getData().containsKey(CoreAttributeConstants.A_SUBMIT_DATA)) {
            if (method instanceof PostMethod) {
                ((PostMethod)method).setRequestBody(
                    (NameValuePair[]) curi.getData().get(CoreAttributeConstants.A_SUBMIT_DATA));
            } else {
                logger.severe("method type mismatch");
            }
        }

        // set hardMax on bytes (if set by operator)
        long hardMax = getMaxLengthBytes();
        // set overall timeout (if set by operator)
        long timeoutMs = 1000 * getTimeoutSeconds();
        // Get max fetch rate (bytes/ms). It comes in in KB/sec
        long maxRateKBps = getMaxFetchKBSec();
        rec.getRecordedInput().setLimits(hardMax, timeoutMs, maxRateKBps);

        try {
            this.http.executeMethod(customConfigOrNull, method);
        } catch (RecorderTooMuchHeaderException ex) {
            // when too much header material, abort like other truncations
            doAbort(curi, method, HEADER_TRUNC);
        } catch (IOException e) {
            failedExecuteCleanup(method, curi, e);
            return;
        } catch (ArrayIndexOutOfBoundsException e) {
            // For weird windows-only ArrayIndex exceptions in native
            // code... see
            // http://forum.java.sun.com/thread.jsp?forum=11&thread=378356
            // treating as if it were an IOException
            failedExecuteCleanup(method, curi, e);
            return;
        }

        // set softMax on bytes to get (if implied by content-length)
        long softMax = method.getResponseContentLength();

        try {
            if (!method.isAborted()) {
                // Force read-to-end, so that any socket hangs occur here,
                // not in later modules.
                rec.getRecordedInput().readFullyOrUntil(softMax);
            }
        } catch (RecorderTimeoutException ex) {
            doAbort(curi, method, TIMER_TRUNC);
        } catch (RecorderLengthExceededException ex) {
            doAbort(curi, method, LENGTH_TRUNC);
        } catch (IOException e) {
            cleanup(curi, e, "readFully", S_CONNECT_LOST);
            return;
        } catch (ArrayIndexOutOfBoundsException e) {
            // For weird windows-only ArrayIndex exceptions from native code
            // see http://forum.java.sun.com/thread.jsp?forum=11&thread=378356
            // treating as if it were an IOException
            cleanup(curi, e, "readFully", S_CONNECT_LOST);
            return;
        } finally {
            // ensure recording has stopped
            rec.closeRecorders();
            if (!method.isAborted()) {
                method.releaseConnection();
            }
            // Note completion time
            curi.setFetchCompletedTime(System.currentTimeMillis());
            // Set the response charset into the HttpRecord if available.
            setCharacterEncoding(curi, rec, method);
            setSizes(curi, rec);
            setOtherCodings(curi, rec, method); 
        }

        if (digestContent) {
            curi.setContentDigest(algorithm, 
                rec.getRecordedInput().getDigestValue());
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(((curi.getFetchType() == HTTP_POST) ? "POST" : "GET")
                    + " " + curi.getUURI().toString() + " "
                    + method.getStatusCode() + " "
                    + rec.getRecordedInput().getSize() + " "
                    + curi.getContentType());
        }

        if (isSuccess(curi) && addedCredentials) {
            // Promote the credentials from the CrawlURI to the CrawlServer
            // so they are available for all subsequent CrawlURIs on this
            // server.
            promoteCredentials(curi);
            if (logger.isLoggable(Level.FINE)) {
                // Print out the cookie. Might help with the debugging.
                Header setCookie = method.getResponseHeader("set-cookie");
                if (setCookie != null) {
                    logger.fine(setCookie.toString().trim());
                }
            }
        } else if (method.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
            // 401 is not 'success'.
            handle401(method, curi);
        }

        if (rec.getRecordedInput().isOpen()) {
            logger.severe(curi.toString() + " RIS still open. Should have"
                    + " been closed by method release: "
                    + Thread.currentThread().getName());
            try {
                rec.getRecordedInput().close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "second-chance RIS close failed", e);
            }
        }
    }

    /**
     * Update CrawlURI internal sizes based on current transaction (and
     * in the case of 304s, history) 
     * 
     * @param curi CrawlURI
     * @param rec HttpRecorder
     */
    protected void setSizes(CrawlURI curi, Recorder rec) {
        // set reporting size
        curi.setContentSize(rec.getRecordedInput().getSize());
        // special handling for 304-not modified
        if (curi.getFetchStatus() == HttpStatus.SC_NOT_MODIFIED
                && curi.containsDataKey(A_FETCH_HISTORY)) {
            @SuppressWarnings("unchecked")
            Map<String, ?> history[] = (Map[])curi.getData().get(A_FETCH_HISTORY);
            if (history[0] != null
                    && history[0]
                            .containsKey(A_REFERENCE_LENGTH)) {
                long referenceLength = (Long) history[0].get(A_REFERENCE_LENGTH);
                // carry-forward previous 'reference-length' for future
                curi.getData().put(A_REFERENCE_LENGTH, referenceLength);
                // increase content-size to virtual-size for reporting
                curi.setContentSize(rec.getRecordedInput().getSize()
                        + referenceLength);
            }
        }
    }
    
    protected void doAbort(CrawlURI curi, HttpMethod method,
            String annotation) {
        curi.getAnnotations().add(annotation);
        curi.getRecorder().close();
        method.abort();
    }

    protected boolean checkMidfetchAbort(CrawlURI curi,
            HttpRecorderMethod method, HttpConnection conn) {
        if (curi.isPrerequisite()) {
            return false;
        }
        DecideResult r = getShouldFetchBodyRule().decisionFor(curi);
        if (r != DecideResult.REJECT) {
            return false;
        }
        method.markContentBegin(conn);
        return true;
    }

    /**
     * This method populates <code>curi</code> with response status and
     * content type.
     * 
     * @param curi
     *            CrawlURI to populate.
     * @param method
     *            Method to get response status and headers from.
     */
    protected void addResponseContent(HttpMethod method, CrawlURI curi) {
        curi.setFetchStatus(method.getStatusCode());
        Header ct = method.getResponseHeader("content-type");
        curi.setContentType((ct == null) ? null : ct.getValue());
    }

    /**
     * Set the character encoding based on the result headers or default.
     * 
     * The HttpClient returns its own default encoding ("ISO-8859-1") if one
     * isn't specified in the Content-Type response header. We give the user the
     * option of overriding this, so we need to detect the case where the
     * default is returned.
     * 
     * Now, it may well be the case that the default returned by HttpClient and
     * the default defined by the user are the same.
     * 
     * TODO:FIXME?: This method does not do the "detect the case where the
     * [HttpClient] default is returned" mentioned above! Why not?
     * 
     * @param rec
     *            Recorder for this request.
     * @param method
     *            Method used for the request.
     */
    private void setCharacterEncoding(CrawlURI curi, final Recorder rec,
            final HttpMethod method) {
        String encoding = ((HttpMethodBase) method).getResponseCharSet();
        try {
            rec.setCharset(Charset.forName(encoding));
        } catch (IllegalArgumentException e) {
            curi.getAnnotations().add("unsatisfiableCharsetInHeader:"+StringUtils.stripToEmpty(encoding));
            rec.setCharset(getDefaultCharset());
        }
    }
    
    /**
     * Set the transfer, content encodings based on headers (if necessary). 
     * 
     * @param rec
     *            Recorder for this request.
     * @param method
     *            Method used for the request.
     */
    private void setOtherCodings(CrawlURI uri, final Recorder rec,
            final HttpMethod method) {
        Header transferCodingHeader = ((HttpMethodBase) method).getResponseHeader("Transfer-Encoding"); 
        if (transferCodingHeader !=null) {
            String te = transferCodingHeader.getValue().trim(); 
            if(te.equalsIgnoreCase("chunked")) {
                rec.setInputIsChunked(true); 
            } else {
                logger.log(Level.WARNING,"Unknown transfer-encoding '"+te+"' for "+uri.getURI());
            }
        }
        Header contentEncodingHeader = ((HttpMethodBase) method).getResponseHeader("Content-Encoding"); 
        if (contentEncodingHeader!=null) {
            String ce = contentEncodingHeader.getValue().trim(); 
            try {
                rec.setContentEncoding(ce); 
            } catch (IllegalArgumentException e) {
                uri.getAnnotations().add("unsatisfiableContentEncoding:"+StringUtils.stripToEmpty(ce));
            }
        }
    }

    /**
     * Cleanup after a failed method execute.
     * 
     * @param curi
     *            CrawlURI we failed on.
     * @param method
     *            Method we failed on.
     * @param exception
     *            Exception we failed with.
     */
    private void failedExecuteCleanup(final HttpMethod method,
            final CrawlURI curi, final Exception exception) {
        cleanup(curi, exception, "executeMethod", (method.isRequestSent() ? S_CONNECT_LOST : S_CONNECT_FAILED));
        method.releaseConnection();
    }

    /**
     * Cleanup after a failed method execute.
     * 
     * @param curi
     *            CrawlURI we failed on.
     * @param exception
     *            Exception we failed with.
     * @param message
     *            Message to log with failure. FIXME: Seems ignored
     * @param status
     *            Status to set on the fetch.
     */
    private void cleanup(final CrawlURI curi, final Exception exception,
            final String message, final int status) {
        // message ignored!
        curi.getNonFatalFailures().add(exception);
        curi.setFetchStatus(status);
        curi.getRecorder().close();
    }

    @Override
    public ProcessResult process(CrawlURI uri) throws InterruptedException {
        if (uri.getFetchStatus() < 0) {
            // already marked as errored, this pass through
            // skip to end
            return ProcessResult.FINISH;
        } else {
            return super.process(uri);
        }
    }

    /**
     * Can this processor fetch the given CrawlURI. May set a fetch status
     * if this processor would usually handle the CrawlURI, but cannot in
     * this instance.
     * 
     * @param curi
     * @return True if processor can fetch.
     */
    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        String scheme = curi.getUURI().getScheme();
        if (!(scheme.equals("http") || scheme.equals("https"))) {
            // handles only plain http and https
            return false;
        }

        CrawlHost host = serverCache.getHostFor(curi.getUURI());
        if (host.getIP() == null && host.hasBeenLookedUp()) {
            curi.setFetchStatus(S_DOMAIN_PREREQUISITE_FAILURE);
            return false;
        }

        return true;
    }

    /**
     * Configure the HttpMethod setting options and headers.
     * 
     * @param curi
     *            CrawlURI from which we pull configuration.
     * @param method
     *            The Method to configure.
     */
    protected HostConfiguration configureMethod(CrawlURI curi,
            HttpMethod method) {
        // Don't auto-follow redirects
        method.setFollowRedirects(false);

        // // set soTimeout
        // method.getParams().setSoTimeout(
        // ((Integer) getUncheckedAttribute(curi, ATTR_SOTIMEOUT_MS))
        // .intValue());

        // Set cookie policy.
        boolean ignoreCookies = getIgnoreCookies();
        method.getParams().setCookiePolicy(
                ignoreCookies ? CookiePolicy.IGNORE_COOKIES
                        : CookiePolicy.BROWSER_COMPATIBILITY);

        method.getParams().setVersion(getUseHTTP11() 
                                        ? HttpVersion.HTTP_1_1 
                                        : HttpVersion.HTTP_1_0);

        UserAgentProvider uap = getUserAgentProvider();
        String from = uap.getFrom();
        String userAgent = curi.getUserAgent();
        if (userAgent == null) {
            userAgent = uap.getUserAgent();
        }
        
        method.setRequestHeader("User-Agent", userAgent);
        if(StringUtils.isNotBlank(from)) {
            method.setRequestHeader("From", from);
        }

        // Set retry handler.
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                new HeritrixHttpMethodRetryHandler());

        final long maxLength = getMaxLengthBytes();
        if (maxLength > 0 && getSendRange()) {
            method.addRequestHeader(RANGE, RANGE_PREFIX.concat(Long
                    .toString(maxLength - 1)));
        }

        if (getSendConnectionClose()) {
            method.addRequestHeader(HEADER_SEND_CONNECTION_CLOSE);
        }

        if (getSendReferer() && !LinkContext.PREREQ_MISC.equals(curi.getViaContext())) {
            // RFC2616 says no referer header if referer is https and the url
            // is not
            String via = flattenVia(curi);
            if (via != null
                    && via.length() > 0
                    && !(via.startsWith(HTTPS_SCHEME) && curi.getUURI()
                            .getScheme().equals(HTTP_SCHEME))) {
                method.setRequestHeader(REFERER, via);
            }
        }

        if (!curi.isPrerequisite()) {
            setConditionalGetHeader(curi, method, getSendIfModifiedSince(), 
                    A_LAST_MODIFIED_HEADER, "If-Modified-Since");
            setConditionalGetHeader(curi, method, getSendIfNoneMatch(), 
                    A_ETAG_HEADER, "If-None-Match");
        }
        
        // TODO: What happens if below method adds a header already
        // added above: e.g. Connection, Range, or Referer?
        setAcceptHeaders(curi, method);

        HostConfiguration config = 
            new HostConfiguration(http.getHostConfiguration());
        configureProxy(curi, config);
        configureBindAddress(curi, config);
        return config;
    }

    /**
     * Set the given conditional-GET header, if the setting is enabled and
     * a suitable value is available in the URI history. 
     * @param curi source CrawlURI
     * @param method HTTP operation pending
     * @param setting true/false enablement setting name to consult
     * @param sourceHeader header to consult in URI history
     * @param targetHeader header to set if possible
     */
    protected void setConditionalGetHeader(CrawlURI curi, HttpMethod method, 
            boolean conditional, String sourceHeader, String targetHeader) {
        if (conditional) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, ?>[] history = (Map[])curi.getData().get(A_FETCH_HISTORY);
                int previousStatus = (Integer) history[0].get(A_STATUS);
                if(previousStatus<=0) {
                    // do not reuse headers from any broken fetch
                    return; 
                }
                String previousValue = (String) history[0].get(sourceHeader);
                if(previousValue!=null) {
                    method.setRequestHeader(targetHeader, previousValue);
                }
            } catch (RuntimeException e) {
                // for absent key, bad index, etc. just do nothing
            }
        }
    }
    
    /**
     * Setup proxy, based on attributes in CrawlURI and settings, 
     * in given HostConfiguration
     */
    private void configureProxy(CrawlURI curi, HostConfiguration config) {
        String proxy = (String) getAttributeEither(curi, "httpProxyHost");
        int port = (Integer) getAttributeEither(curi, "httpProxyPort");            
        String user = (String) getAttributeEither(curi, "httpProxyUser");
        String password = (String) getAttributeEither(curi, "httpProxyPassword");
        configureProxy(proxy, port, user, password, config);
    }
    
    private void configureProxy(String proxy, int port, String user, String password,
                                   HostConfiguration config) {
        if(StringUtils.isNotEmpty(proxy)) {
            config.setProxy(proxy, port);
            if (StringUtils.isNotEmpty(user)) {
                Credentials credentials = new NTCredentials(user, password, "", "");
                AuthScope authScope = new AuthScope(proxy, port);
                this.http.getState().setProxyCredentials(authScope, credentials);
            }
        }
    }
    
    /**
     * Setup local bind address, based on attributes in CrawlURI and 
     * settings, in given HostConfiguration
     */
    private void configureBindAddress(CrawlURI curi, HostConfiguration config) {
        String addressString = (String) getAttributeEither(curi, HTTP_BIND_ADDRESS);
        configureBindAddress(addressString,config);
    }

    private void configureBindAddress(String address, HostConfiguration config) {
        if (StringUtils.isNotEmpty(address)) {
            try {
                InetAddress localAddress = InetAddress.getByName(address);
                config.setLocalAddress(localAddress);
            } catch (UnknownHostException e) {
                // Convert all to RuntimeException so get an exception out
                // if initialization fails.
                throw new RuntimeException("Unknown host " + address
                        + " in local-address");
            }
        }
    }

    /**
     * Get a value either from inside the CrawlURI instance, or from
     * settings (module attributes).
     * 
     * @param curi
     *            CrawlURI to consult
     * @param key
     *            key to lookup
     * @return value from either CrawlURI (preferred) or settings
     */
    protected Object getAttributeEither(CrawlURI curi, String key) {
        
        Object r = curi.getData().get(key);
        if (r != null) {
            return r;
        }
        return kp.get(key);
    }

    /**
     * Add credentials if any to passed <code>method</code>.
     * 
     * Do credential handling. Credentials are in two places. 1. Credentials
     * that succeeded are added to the CrawlServer (Or rather, avatars for
     * credentials are whats added because its not safe to keep around
     * references to credentials). 2. Credentials to be tried are in the curi.
     * Returns true if found credentials to be tried.
     * 
     * @param curi
     *            Current CrawlURI.
     * @param method
     *            The method to add to.
     * @return True if prepopulated <code>method</code> with credentials AND
     *         the credentials came from the <code>curi</code>, not from the
     *         CrawlServer. The former is special in that if the
     *         <code>curi</curi> credentials
     * succeed, then the caller needs to promote them from the CrawlURI to the
     * CrawlServer so they are available for all subsequent CrawlURIs on this
     * server.
     */
    private boolean populateCredentials(CrawlURI curi, HttpMethod method) {
        // First look at the server avatars. Add any that are to be volunteered
        // on every request (e.g. RFC2617 credentials). Every time creds will
        // return true when we call 'isEveryTime().
        String serverKey;
        try {
            serverKey = CrawlServer.getServerKey(curi.getUURI());
        } catch (URIException e) {
            return false;
        }
        CrawlServer server = serverCache.getServerFor(serverKey);
        if (server.hasCredentials()) {
            for (Credential cred : server.getCredentials()) {
                if (cred.isEveryTime()) {
                    cred.populate(curi, this.http, method, server.getHttpAuthChallenges());
                }
            }
        }

        boolean result = false;

        // Now look in the curi. The Curi will have credentials loaded either
        // by the handle401 method if its a rfc2617 or it'll have been set into
        // the curi by the preconditionenforcer as this login uri came through.
        for (Credential c: curi.getCredentials()) {
            if (c.populate(curi, this.http, method, curi.getHttpAuthChallenges())) {
                result = true;
            }
        }

        return result;
    }

    /**
     * Promote successful credential to the server.
     * 
     * @param curi
     *            CrawlURI whose credentials we are to promote.
     */
    private void promoteCredentials(final CrawlURI curi) {
        Set<Credential> credentials = curi.getCredentials();
        for (Iterator<Credential> i = credentials.iterator(); i.hasNext();) {
            Credential c = i.next();
            i.remove();
            // The server to attach too may not be the server that hosts
            // this passed curi. It might be of another subdomain.
            // The avatar needs to be added to the server that is dependent
            // on this precondition. Find it by name. Get the name from
            // the credential this avatar represents.
            String cd = c.getDomain();
            if (cd != null) {
                CrawlServer cs = serverCache.getServerFor(cd);
                if (cs != null) {
                    cs.addCredential(c);
                    cs.setHttpAuthChallenges(curi.getHttpAuthChallenges());
                }
            }
        }
    }

    /**
     * Server is looking for basic/digest auth credentials (RFC2617). If we have
     * any, put them into the CrawlURI and have it come around again.
     * Presence of the credential serves as flag to frontier to requeue
     * promptly. If we already tried this domain and still got a 401, then our
     * credentials are bad. Remove them and let this curi die.
     * 
     * @param method
     *            Method that got a 401.
     * @param curi
     *            CrawlURI that got a 401.
     */
    protected void handle401(final HttpMethod method, final CrawlURI curi) {
        AuthScheme authscheme = getAuthScheme(method, curi);
        if (authscheme == null) {
            return;
        }
        String realm = authscheme.getRealm();

        // Look to see if this curi had rfc2617 avatars loaded. If so, are
        // any of them for this realm? If so, then the credential failed
        // if we got a 401 and it should be let die a natural 401 death.
        Set<Credential> curiRfc2617Credentials = getCredentials(curi,
                HttpAuthenticationCredential.class);
        HttpAuthenticationCredential extant = HttpAuthenticationCredential.getByRealm(
                curiRfc2617Credentials, realm, curi);
        if (extant != null) {
            // Then, already tried this credential. Remove ANY rfc2617
            // credential since presence of a rfc2617 credential serves
            // as flag to frontier to requeue this curi and let the curi
            // die a natural death.
            extant.detachAll(curi);
            logger.warning("Auth failed (401) though supplied realm " + realm
                    + " to " + curi.toString());
        } else {
            // Look see if we have a credential that corresponds to this
            // realm in credential store. Filter by type and credential
            // domain. If not, let this curi die. Else, add it to the
            // curi and let it come around again. Add in the AuthScheme
            // we got too. Its needed when we go to run the Auth on
            // second time around.
            String serverKey = getServerKey(curi);
            CrawlServer server = serverCache.getServerFor(serverKey);
            Set<Credential> storeRfc2617Credentials = getCredentialStore().subset(curi,
                    HttpAuthenticationCredential.class, server.getName());
            if (storeRfc2617Credentials == null
                    || storeRfc2617Credentials.size() <= 0) {
                logger.fine("No rfc2617 credentials for " + curi);
            } else {
                HttpAuthenticationCredential found = HttpAuthenticationCredential.getByRealm(
                        storeRfc2617Credentials, realm, curi);
                if (found == null) {
                    logger.fine("No rfc2617 credentials for realm " + realm
                            + " in " + curi);
                } else {
                    found.attach(curi);
                    logger.fine("Found credential for realm " + realm
                            + " in store for " + curi.toString());
                }
            }
        }
    }

    /**
     * @param method
     *            Method that got a 401.
     * @param curi
     *            CrawlURI that got a 401.
     * @return Returns first wholesome authscheme found else null.
     */
    protected AuthScheme getAuthScheme(final HttpMethod method,
            final CrawlURI curi) {
        Header[] headers = method.getResponseHeaders("WWW-Authenticate");
        if (headers == null || headers.length <= 0) {
            logger.fine("We got a 401 but no WWW-Authenticate challenge: "
                    + curi.toString());
            return null;
        }

        Map<String, String> authChallenges = null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> parsedChallenges = AuthChallengeParser.parseChallenges(headers);
            authChallenges = parsedChallenges;
            
            // remember WWW-Authenticate headers for later use 
            curi.setHttpAuthChallenges(authChallenges);
        } catch (MalformedChallengeException e) {
            logger.fine("Failed challenge parse: " + e.getMessage());
        }
        if (authChallenges == null || authChallenges.size() <= 0) {
            logger.fine("We got a 401 and WWW-Authenticate challenge"
                    + " but failed parse of the header " + curi.toString());
            return null;
        }

        // XXX there's a lot of overlap below with AuthChallengeProcessor.processChallenge()
        
        AuthScheme result = null;
        // Use the first auth found.
        for (Iterator<String> i = authChallenges.keySet().iterator(); result == null
                && i.hasNext();) {
            String key = (String) i.next();
            String challenge = (String) authChallenges.get(key);
            if (key == null || key.length() <= 0 || challenge == null
                    || challenge.length() <= 0) {
                logger.warning("Empty scheme: " + curi.toString() + ": "
                        + Arrays.toString(headers));
                continue;
            }
            AuthScheme authscheme;
            try {
                authscheme = AuthPolicy.getAuthScheme(key);
            } catch (IllegalStateException e) {
                logger.info("Unsupported auth scheme '" + key + "' at " + curi + " - " + e);
                continue;
            }

            try {
                authscheme.processChallenge(challenge);
            } catch (MalformedChallengeException e) {
                logger.fine(e.getMessage() + " " + curi + " " + Arrays.toString(headers));
                continue;
            }
            if (authscheme.isConnectionBased()) {
                logger.fine("Connection based " + authscheme);
                continue;
            }

            if (authscheme.getRealm() == null
                    || authscheme.getRealm().length() <= 0) {
                logger.fine("Empty realm " + authscheme + " for " + curi);
                continue;
            }
            result = authscheme;
        }

        return result;
    }

    /**
     * @param curi
     *            CrawlURI that got a 401.
     * @param type
     *            Class of credential to get from curi.
     * @return Set of credentials attached to this curi.
     */
    private Set<Credential> getCredentials(CrawlURI curi, Class<?> type) {
        Set<Credential> result = null;

        if (curi.hasCredentials()) {
            for (Credential c : curi.getCredentials()) {
                if (type.isInstance(c)) {
                    if (result == null) {
                        result = new HashSet<Credential>();
                    }
                    result.add(c);
                }
            }
        }
        return result;
    }

    public void start() {
        if(isRunning()) {
            return; 
        }
        super.start();
        
        setSSLFactory();
        configureHttp();

        if (cookieStorage != null) {     
            cookieStorage.start(); 
            http.getState().setCookiesMap(cookieStorage.getCookiesMap());
        }
    }
    
    public boolean isRunning() {
        return this.http != null; 
    }
    
    public void stop() {
        if(!isRunning()) {
            return; 
        }
        super.stop();
        // At the end save cookies to the file specified in the order file.
        if (cookieStorage != null) {
            Map<String, Cookie> map = http.getState().getCookiesMap();
            cookieStorage.saveCookiesMap(map);
            cookieStorage.stop();
        }
        cleanupHttp(); // XXX happens at finish; move to teardown?
    }

    /**
     * Perform any final cleanup related to the HttpClient instance.
     */
    protected void cleanupHttp() {
        this.http = null; 
    }
    
    private void setSSLFactory() {
        // I tried to get the default KeyManagers but doesn't work unless you
        // point at a physical keystore. Passing null seems to do the right
        // thing so we'll go w/ that.
        try {
            SSLContext context = SSLContext.getInstance("SSL");
            context.init(null,
                    new TrustManager[] { new ConfigurableX509TrustManager(
                            getSslTrustLevel()) }, null);
            this.sslfactory = context.getSocketFactory();
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed configure of ssl context "
                    + e.getMessage(), e);
        }
        
    }

    protected void configureHttp() {
        int soTimeout = getSoTimeoutMs();
        String addressStr = getHttpBindAddress();
        String proxy = getHttpProxyHost();
        int port = -1;
        String user = "";
        String password = "";
        if (proxy.length() == 0) {
            proxy = null;
        } else {
            port = getHttpProxyPort();
            user = getHttpProxyUser();
            password = getHttpProxyPassword();
        }
        configureHttp(soTimeout, addressStr, proxy, port, user, password);
    }
    
    protected void configureHttp(int soTimeout, String addressStr,
                                 String proxy, int port, String user, String password) {
        // Get timeout. Use it for socket and for connection timeout.
        int timeout = (soTimeout > 0) ? soTimeout : 0;

        // HttpConnectionManager cm = new ThreadLocalHttpConnectionManager();
        HttpConnectionManager cm = new SingleHttpConnectionManager();

        // TODO: The following settings should be made in the corresponding
        // HttpConnectionManager, not here.
        HttpConnectionManagerParams hcmp = cm.getParams();
        hcmp.setConnectionTimeout(timeout);
        hcmp.setStaleCheckingEnabled(true);
        // Minimizes bandwidth usage. Setting to true disables Nagle's
        // algorithm. IBM JVMs < 142 give an NPE setting this boolean
        // on ssl sockets.
        hcmp.setTcpNoDelay(false);

        this.http = new HttpClient(cm);
        HttpClientParams hcp = this.http.getParams();
        // Set default socket timeout.
        hcp.setSoTimeout(timeout);
        // Set client to be version 1.0.
        hcp.setVersion(HttpVersion.HTTP_1_0);

        // configureHttpCookies(defaults);

        // Configure how we want the method to act.
        this.http.getParams().setParameter(
                HttpMethodParams.SINGLE_COOKIE_HEADER, new Boolean(true));
        this.http.getParams().setParameter(
                HttpMethodParams.UNAMBIGUOUS_STATUS_LINE, new Boolean(false));
        this.http.getParams().setParameter(
                HttpMethodParams.STRICT_TRANSFER_ENCODING, new Boolean(false));
        this.http.getParams().setIntParameter(
                HttpMethodParams.STATUS_LINE_GARBAGE_LIMIT, 10);

        if ((proxy != null) && (proxy.length() == 0)) {
            proxy = null;
        }
        HostConfiguration config = http.getHostConfiguration();
        configureProxy(proxy, port, user, password, config);
        configureBindAddress(addressStr,config);

        hcmp.setParameter(SSL_FACTORY_KEY, this.sslfactory);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.archive.crawler.framework.Processor#report()
     */
    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append(super.report());
        ret.append("  Function:          Fetch HTTP URIs\n");
        ret.append("  CrawlURIs handled: " + this.getURICount() + "\n");
        ret.append("  Recovery retries:   " + this.recoveryRetries + "\n");

        return ret.toString();
    }


    private void setAcceptHeaders(CrawlURI curi, HttpMethod get) {
        if(getAcceptCompression()) {
            // we match the Firefox header exactly (ordering and whitespace)
            // as a favor to caches
            get.setRequestHeader("Accept-Encoding","gzip,deflate"); 
        }
        List<String> acceptHeaders = getAcceptHeaders();
        if (acceptHeaders.isEmpty()) {
            return;
        }
        for (String hdr : acceptHeaders) {
            String[] nvp = hdr.split(": +");
            if (nvp.length == 2) {
                get.setRequestHeader(nvp[0], nvp[1]);
            } else {
                logger.warning("Invalid accept header: " + hdr);
            }
        }
    }

    // custom serialization

    private String getLocalAddress() {
        HostConfiguration hc = http.getHostConfiguration();
        if (hc == null) {
            return "";
        }
        
        InetAddress addr = hc.getLocalAddress();
        if (addr == null) {
            return "";
        }
        
        String r = addr.getCanonicalHostName();
        if (r == null) {
            return "";
        }
        
        return r;
    }
    
    
    private String getProxyHost() {
        HostConfiguration hc = http.getHostConfiguration();
        if (hc == null) {
            return "";
        }
        
        String r = hc.getProxyHost();
        if (r == null) {
            return "";
        }
        
        return r;
    }
    
    
    private int getProxyPort() {
        HostConfiguration hc = http.getHostConfiguration();
        if (hc == null) {
            return -1;
        }
        
        return hc.getProxyPort();
    }


    private String getProxyUser() {
        NTCredentials credentials = (NTCredentials)http.getState().getProxyCredentials(new AuthScope(getProxyHost(), getProxyPort()));
        if (credentials == null) {
            return "";
        }

        String r = credentials.getUserName();
        if (r == null) {
            return "";
        }

        return r;
    }

    private String getProxyPassword() {
        NTCredentials credentials = (NTCredentials)http.getState().getProxyCredentials(new AuthScope(getProxyHost(), getProxyPort()));
        if (credentials == null) {
            return "";
        }

        String r = credentials.getPassword();
        if (r == null) {
            return "";
        }

        return r;
    }


    private void writeObject(ObjectOutputStream stream) throws IOException {
         stream.defaultWriteObject();

         // Special handling for http since it isn't Serializable itself
         stream.writeInt(http.getParams().getSoTimeout());
         stream.writeUTF(getLocalAddress());
         stream.writeUTF(getProxyHost());
         stream.writeInt(getProxyPort());
        stream.writeUTF(getProxyUser());
        stream.writeUTF(getProxyPassword());
    }


    private void readObject(ObjectInputStream stream) 
     throws IOException, ClassNotFoundException {
         stream.defaultReadObject();

        int soTimeout = stream.readInt();
        String localAddress = stream.readUTF();
        String proxy = stream.readUTF();
        int port = stream.readInt();
        String user = stream.readUTF();
        String password = stream.readUTF();

        configureHttp(soTimeout, localAddress, proxy, port, user, password);
        setSSLFactory();
    }


    /**
     * @return Returns the http instance.
     */
    protected HttpClient getHttp() {
        return this.http;
    }

    private static String getServerKey(CrawlURI uri) {
        try {
            return CrawlServer.getServerKey(uri.getUURI());
        } catch (URIException e) {
            logger.severe(e.getMessage() + ": " + uri);
            e.printStackTrace();
            return null;
        }
    }
}
