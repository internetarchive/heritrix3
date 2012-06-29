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

import static org.archive.modules.fetcher.FetchErrors.LENGTH_TRUNC;
import static org.archive.modules.fetcher.FetchErrors.TIMER_TRUNC;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_FAILED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_LOST;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_PREREQUISITE_FAILURE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_FETCH_HISTORY;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_REFERENCE_LENGTH;

import java.io.IOException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.ContentType;
import org.apache.http.message.BasicHeader;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.archive.io.RecorderLengthExceededException;
import org.archive.io.RecorderTimeoutException;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.Recorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

public class FetchHTTP2 extends AbstractFetchHTTP implements Lifecycle {

    private static Logger logger = Logger.getLogger(FetchHTTP2.class.getName());

    protected RecordingHttpClient httpClient; 
    
    public static final String REFERER = "Referer";
    public static final String RANGE = "Range";
    public static final String RANGE_PREFIX = "bytes=0-";
    public static final String HTTP_SCHEME = "http";
    public static final String HTTPS_SCHEME = "https";

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    /**
     * Used to do DNS lookups.
     */
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    {
        setDigestContent(true);
    }
    public boolean getDigestContent() {
        return (Boolean) kp.get("digestContent");
    }
    /**
     * Whether or not to perform an on-the-fly digest hash of retrieved
     * content-bodies.
     */
    public void setDigestContent(boolean digest) {
        kp.put("digestContent",digest);
    }
 
    protected String digestAlgorithm = "sha1";
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }
    /**
     * Which algorithm (for example MD5 or SHA-1) to use to perform an
     * on-the-fly digest hash of retrieved content-bodies.
     */
    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    public UserAgentProvider getUserAgentProvider() {
        return (UserAgentProvider) kp.get("userAgentProvider");
    }
    @Autowired
    public void setUserAgentProvider(UserAgentProvider provider) {
        kp.put("userAgentProvider",provider);
    }

    {
        setSendConnectionClose(true);
    }
    public boolean getSendConnectionClose() {
        return (Boolean) kp.get("sendConnectionClose");
    }
    /**
     * Send 'Connection: close' header with every request.
     */
    public void setSendConnectionClose(boolean sendClose) {
        kp.put("sendConnectionClose",sendClose);
    }
    
    {
        setDefaultEncoding("ISO-8859-1");
    }
    public String getDefaultEncoding() {
        return getDefaultCharset().name();
    }
    /**
     * The character encoding to use for files that do not have one specified in
     * the HTTP response headers. Default: ISO-8859-1.
     */
    public void setDefaultEncoding(String encoding) {
        kp.put("defaultEncoding",Charset.forName(encoding));
    }
    public Charset getDefaultCharset() {
        return (Charset)kp.get("defaultEncoding");
    }

    {
        setUseHTTP11(false);
    }
    public boolean getUseHTTP11() {
        return (Boolean) kp.get("useHTTP11");
    }
    /**
     * Use HTTP/1.1. Note: even when offering an HTTP/1.1 request, 
     * Heritrix may not properly handle persistent/keep-alive connections, 
     * so the sendConnectionClose parameter should remain 'true'. 
     */
    public void setUseHTTP11(boolean useHTTP11) {
        kp.put("useHTTP11",useHTTP11);
    }

    {
        setIgnoreCookies(false);
    }
    public boolean getIgnoreCookies() {
        return (Boolean) kp.get("ignoreCookies");
    }
    /**
     * Disable cookie handling.
     */
    public void setIgnoreCookies(boolean ignoreCookies) {
        kp.put("ignoreCookies",ignoreCookies);
    }

    {
        setSendReferer(true);
    }
    public boolean getSendReferer() {
        return (Boolean) kp.get("sendReferer");
    }
    /**
     * Send 'Referer' header with every request.
     * <p>
     * The 'Referer' header contans the location the crawler came from, the page
     * the current URI was discovered in. The 'Referer' usually is logged on the
     * remote server and can be of assistance to webmasters trying to figure how
     * a crawler got to a particular area on a site.
     */
    public void setSendReferer(boolean sendClose) {
        kp.put("sendReferer",sendClose);
    }

    {
        setAcceptCompression(false);
    }
    public boolean getAcceptCompression() {
        return (Boolean) kp.get("acceptCompression");
    }
    /**
     * Set headers to accept compressed responses. 
     */
    public void setAcceptCompression(boolean acceptCompression) {
        kp.put("acceptCompression", acceptCompression);
    }
    
    {
        setAcceptHeaders(Arrays.asList("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"));
    }
    @SuppressWarnings("unchecked")
    public List<String> getAcceptHeaders() {
        return (List<String>) kp.get("acceptHeaders");
    }
    /**
     * Accept Headers to include in each request. Each must be the complete
     * header, e.g., 'Accept-Language: en'. (Thus, this can also be used to
     * other headers not beginning 'Accept-' as well.) By default heritrix sends
     * an Accept header similar to what a typical browser would send (the value
     * comes from Firefox 4.0).
     */
    public void setAcceptHeaders(List<String> headers) {
        kp.put("acceptHeaders",headers);
    }
    
    protected AbstractCookieStore cookieStore;
    @Autowired(required=false)
    public void setCookieStore(AbstractCookieStore store) {
        this.cookieStore = store; 
    }
    public AbstractCookieStore getCookieStore() {
        return cookieStore;
    }

    protected static final Header HEADER_SEND_CONNECTION_CLOSE = new BasicHeader(
            HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
    
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
        if (!(scheme.equals(HTTP_SCHEME) || scheme.equals(HTTPS_SCHEME))) {
            // handles only plain http and https
            return false;
        }

        CrawlHost host = getServerCache().getHostFor(curi.getUURI());
        if (host.getIP() == null && host.hasBeenLookedUp()) {
            curi.setFetchStatus(S_DOMAIN_PREREQUISITE_FAILURE);
            return false;
        }

        return true;
    }
    
    /**
     * Set the transfer, content encodings based on headers (if necessary). 
     * 
     * @param rec
     *            Recorder for this request.
     * @param response
     *            Method used for the request.
     */
    protected void setOtherCodings(CrawlURI uri, final Recorder rec,
            final HttpResponse response) {
        rec.setInputIsChunked(response.getEntity().isChunked()); 
        Header contentEncodingHeader = response.getEntity().getContentEncoding(); 
        if (contentEncodingHeader != null) {
            String ce = contentEncodingHeader.getValue().trim();
            try {
                rec.setContentEncoding(ce);
            } catch (IllegalArgumentException e) {
                uri.getAnnotations().add("unsatisfiableContentEncoding:" + StringUtils.stripToEmpty(ce));
            }
        }
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
     * @param response
     *            Method used for the request.
     */
    protected void setCharacterEncoding(CrawlURI curi, final Recorder rec,
            final HttpResponse response) {
        Charset charset = ContentType.getOrDefault(response.getEntity()).getCharset();
        if (charset != null) {
            rec.setCharset(charset);
        } else {
            // curi.getAnnotations().add("unsatisfiableCharsetInHeader:"+StringUtils.stripToEmpty(encoding));
            rec.setCharset(getDefaultCharset());
        }
    }

    @Override
    protected void innerProcess(final CrawlURI curi) throws InterruptedException {
        // Note begin time
        curi.setFetchBeginTime(System.currentTimeMillis());

        // Get a reference to the HttpRecorder that is set into this ToeThread.
        final Recorder rec = curi.getRecorder();

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

        String curiString = curi.getUURI().toString();
        HttpRequestBase request = null;
        if (curi.getFetchType() == FetchType.HTTP_POST) {
            request = new HttpPost(curiString);
            curi.setFetchType(FetchType.HTTP_POST);
        } else {
            request = new HttpGet(curiString);
            curi.setFetchType(FetchType.HTTP_GET);
        }
        
        configureRequest(curi, request);
        
        HttpResponse response = null;
        try {
            response = getHttpClient().execute(request);
            addResponseContent(response, curi);
        } catch (ClientProtocolException e) {
            failedExecuteCleanup(request, curi, e);
            return;
        } catch (IOException e) {
            failedExecuteCleanup(request, curi, e);
            return;
        }
        
        // set softMax on bytes to get (if implied by content-length)
        long softMax = -1l;
        Header h = response.getLastHeader("content-length");
        if (h != null) {
            Long.parseLong(h.getValue());
        }
        try {
            if (!request.isAborted()) {
                // Force read-to-end, so that any socket hangs occur here,
                // not in later modules.
                
                // XXX does it matter that we're circumventing the library here? response.getEntity().getContent()
                rec.getRecordedInput().readFullyOrUntil(softMax); 
            }
        } catch (RecorderTimeoutException ex) {
            doAbort(curi, request, TIMER_TRUNC);
        } catch (RecorderLengthExceededException ex) {
            doAbort(curi, request, LENGTH_TRUNC);
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
            rec.close();
            // ensure recording has stopped
            rec.closeRecorders();
            if (!request.isAborted()) {
                request.reset();
            }
            // Note completion time
            curi.setFetchCompletedTime(System.currentTimeMillis());
            // Set the response charset into the HttpRecord if available.
            setCharacterEncoding(curi, rec, response);
            setSizes(curi, rec);
            setOtherCodings(curi, rec, response); 
        }

        if (digestContent) {
            curi.setContentDigest(algorithm, 
                rec.getRecordedInput().getDigestValue());
        }
    }

    protected void configureRequest(CrawlURI curi, HttpRequestBase request) {
        // ignore cookies?
        if (getIgnoreCookies()) {
            HttpClientParams.setCookiePolicy(request.getParams(), CookiePolicy.IGNORE_COOKIES);
        } else {
            HttpClientParams.setCookiePolicy(request.getParams(), CookiePolicy.BROWSER_COMPATIBILITY);
        }

        // http 1.1
        if (getUseHTTP11()) {
            HttpProtocolParams.setVersion(request.getParams(), HttpVersion.HTTP_1_1);
        } else {
            HttpProtocolParams.setVersion(request.getParams(), HttpVersion.HTTP_1_0);
        }
        
        // user-agent header
        String userAgent = curi.getUserAgent();
        if (userAgent == null) {
            userAgent = getUserAgentProvider().getUserAgent();
        }
        // request.setHeader(HTTP.USER_AGENT, userAgent);
        HttpProtocolParams.setUserAgent(request.getParams(), userAgent);

        // from header
        String from = getUserAgentProvider().getFrom();
        if (StringUtils.isNotBlank(from)) {
            request.setHeader("From", from);
        }
        
        if (getSendConnectionClose()) {
            request.setHeader(HEADER_SEND_CONNECTION_CLOSE);
        }
        
        // referer
        if (getSendReferer() && !LinkContext.PREREQ_MISC.equals(curi.getViaContext())) {
            // RFC2616 says no referer header if referer is https and the url is not
            String via = flattenVia(curi);
            if (!StringUtils.isEmpty(via)
                    && !(curi.getVia().getScheme().equals(HTTPS_SCHEME) 
                            && curi.getUURI().getScheme().equals(HTTP_SCHEME))) {
                request.setHeader(REFERER, via);
            }
        }

        // TODO: What happens if below method adds a header already
        // added above: e.g. Connection, Range, or Referer?
        configureAcceptHeaders(request);

//        HostConfiguration config = 
//            new HostConfiguration(http.getHostConfiguration());
//        configureProxy(curi, config);
//        configureBindAddress(curi, config);
//        return config;
    }
    
    protected void configureAcceptHeaders(HttpRequestBase request) {
        if (getAcceptCompression()) {
            // we match the Firefox header exactly (ordering and whitespace)
            // as a favor to caches
            request.setHeader("Accept-Encoding","gzip,deflate"); 
        }
        List<String> acceptHeaders = getAcceptHeaders();
        if (acceptHeaders.isEmpty()) {
            return;
        }
        for (String hdr: acceptHeaders) {
            String[] nvp = hdr.split(": +");
            if (nvp.length == 2) {
                request.addHeader(nvp[0], nvp[1]);
            } else {
                logger.warning("Invalid accept header: " + hdr);
            }
        }
}
    
    protected RecordingHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new RecordingHttpClient(getServerCache());
        }
        
        return httpClient;
    }

    protected void doAbort(CrawlURI curi, HttpRequestBase request,
            String annotation) {
        curi.getAnnotations().add(annotation);
        curi.getRecorder().close();
        request.abort();
    }

    /**
     * Update CrawlURI internal sizes based on current transaction (and
     * in the case of 304s, history) 
     * 
     * @param curi CrawlURI
     * @param rec HttpRecorder
     */
    @SuppressWarnings("unchecked")
    protected void setSizes(CrawlURI curi, Recorder rec) {
        // set reporting size
        curi.setContentSize(rec.getRecordedInput().getSize());
        // special handling for 304-not modified
        if (curi.getFetchStatus() == HttpStatus.SC_NOT_MODIFIED
                && curi.containsDataKey(A_FETCH_HISTORY)) {
            Map history[] = (Map[])curi.getData().get(A_FETCH_HISTORY);
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

    /**
     * This method populates <code>curi</code> with response status and
     * content type.
     * 
     * @param curi
     *            CrawlURI to populate.
     * @param response
     *            Method to get response status and headers from.
     */
    protected void addResponseContent(HttpResponse response, CrawlURI curi) {
        curi.setFetchStatus(response.getStatusLine().getStatusCode());
        // Header ct = response.getResponseHeader("content-type");
        Header ct = response.getLastHeader("content-type");
        curi.setContentType(ct == null ? null : ct.getValue());
        
        for (Header h: response.getAllHeaders()) {
            curi.putHttpHeader(h.getName(), h.getValue());
        }
    }

    /**
     * Cleanup after a failed method execute.
     * 
     * @param curi
     *            CrawlURI we failed on.
     * @param request
     *            Method we failed on.
     * @param exception
     *            Exception we failed with.
     */
    protected void failedExecuteCleanup(final HttpRequestBase request,
            final CrawlURI curi, final Exception exception) {
        cleanup(curi, exception, "executeMethod", S_CONNECT_FAILED);
        request.reset();
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
    protected void cleanup(final CrawlURI curi, final Exception exception,
            final String message, final int status) {
        // message ignored!
        curi.getNonFatalFailures().add(exception);
        curi.setFetchStatus(status);
        curi.getRecorder().close();
    }
    
    public void start() {
        if(isRunning()) {
            return; 
        }
        super.start();
        
        // configureHttp();

        if (getCookieStore() != null) {     
            getCookieStore().start();
            getHttpClient().setCookieStore(getCookieStore());
        }

        // setSSLFactory();
    }
    
    public boolean isRunning() {
        return this.httpClient != null; 
    }
    
    public void stop() {
        if (!isRunning()) {
            return;
        }
        super.stop();
        // At the end save cookies to the file specified in the order file.
        if (cookieStore != null) {
            cookieStore.saveCookies();
            cookieStore.stop();
        }
        // cleanupHttp(); // XXX happens at finish; move to teardown?
    }

}
