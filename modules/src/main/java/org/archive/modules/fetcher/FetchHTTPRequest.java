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

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ETAG_HEADER;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_STATUS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolVersion;
import org.apache.http.auth.AuthScheme;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.AbstractExecutionAwareRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.MessageConstraints;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.ManagedHttpClientConnection;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.DefaultBHttpClientConnection;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.DefaultHttpResponseParserFactory;
import org.apache.http.impl.conn.ManagedHttpClientConnectionFactory;
import org.apache.http.impl.io.DefaultHttpRequestWriterFactory;
import org.apache.http.io.HttpMessageParserFactory;
import org.apache.http.io.HttpMessageWriterFactory;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.Processor;
import org.archive.modules.credential.Credential;
import org.archive.modules.credential.HtmlFormCredential;
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.forms.HTMLForm.NameValue;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.util.Recorder;

/**
 * @contributor nlevitt
 */
public class FetchHTTPRequest {
    
    private boolean disableSNI = false;
    
    public boolean isDisableSNI() {
        return disableSNI;
    }

    public void setDisableSNI(boolean disableSNI) {
        this.disableSNI = disableSNI;
    }

    /**
     * Implementation of {@link DnsResolver} that uses the server cache which is
     * normally expected to have been populated by FetchDNS.
     */
    protected static class ServerCacheResolver implements DnsResolver {
        private static Logger logger = Logger.getLogger(DnsResolver.class.getName());
        protected ServerCache serverCache;

        public ServerCacheResolver(ServerCache serverCache) {
            this.serverCache = serverCache;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            CrawlHost crawlHost = this.serverCache.getHostFor(host);
            if (crawlHost != null) {
                InetAddress ip = crawlHost.getIP();
                if (ip != null) {
                    return new InetAddress[] {ip};
                }
            }

            logger.info("host \"" + host + "\" is not in serverCache, allowing java to resolve it");
            return new InetAddress[] {InetAddress.getByName(host)};
        }
    }

    private static final Logger logger = Logger.getLogger(FetchHTTPRequest.class.getName());

    protected FetchHTTP fetcher;
    protected CrawlURI curi;
    protected HttpClientBuilder httpClientBuilder;
    protected RequestConfig.Builder requestConfigBuilder;
    protected HttpClientContext httpClientContext;
    protected AbstractExecutionAwareRequest request;
    protected HttpHost targetHost;
    protected boolean addedCredentials;
    protected HttpHost proxyHost;
    // make this a member variable so it doesn't get gc'd prematurely
    protected HttpClientConnectionManager connMan;

    public FetchHTTPRequest(FetchHTTP fetcher, CrawlURI curi) throws URIException {
        this.fetcher = fetcher;
        this.curi = curi;
        
        this.targetHost = new HttpHost(curi.getUURI().getHost(), 
                curi.getUURI().getPort(), curi.getUURI().getScheme());
        
        this.httpClientContext = new HttpClientContext();
        this.requestConfigBuilder = RequestConfig.custom();

        ProtocolVersion httpVersion = fetcher.getConfiguredHttpVersion();
        String proxyHostname = (String) fetcher.getAttributeEither(curi, "httpProxyHost");
        Integer proxyPort = (Integer) fetcher.getAttributeEither(curi, "httpProxyPort");
                
        String requestLineUri;
        if (StringUtils.isNotEmpty(proxyHostname) && proxyPort != null) {
            this.proxyHost = new HttpHost(proxyHostname, proxyPort);
            this.requestConfigBuilder.setProxy(this.proxyHost);
            requestLineUri = curi.getUURI().toString();
        } else {
            requestLineUri = curi.getUURI().getEscapedPathQuery();
        }

        if (curi.getFetchType() == FetchType.HTTP_POST) {
            BasicExecutionAwareEntityEnclosingRequest postRequest = new BasicExecutionAwareEntityEnclosingRequest(
                    "POST", requestLineUri, httpVersion);
            this.request = postRequest;
            if (curi.containsDataKey(CoreAttributeConstants.A_SUBMIT_DATA)) {
                HttpEntity entity = buildPostRequestEntity(curi);
                postRequest.setEntity(entity);
            }
        } else {
            this.request = new BasicExecutionAwareRequest("GET", 
                    requestLineUri, httpVersion);
            curi.setFetchType(FetchType.HTTP_GET);
        }

        if (proxyHost != null) {
            request.addHeader("Proxy-Connection", "close");
        }
        
        initHttpClientBuilder();
        configureHttpClientBuilder();
        
        configureRequestHeaders();
        configureRequest();
        
        this.addedCredentials = populateTargetCredential();
        populateHttpProxyCredential();
    }

    /**
     * Returns a copy of the string with non-ascii characters replaced by their
     * html numeric character reference in decimal (e.g. &amp;#12345;).
     * 
     * <p>
     * The purpose of this is to produce a multipart/formdata submission that
     * any server should be able to handle, based on experiments using a modern
     * browser (chromium 47.0.2526.106 for mac). What chromium posts depends on
     * what it considers the character encoding of the page containing the form,
     * and maybe other factors. It would be too complicated to try to simulate
     * that behavior in heritrix.
     * 
     * <p>
     * Instead what we do is approximately what the browser does when the form
     * page is plain ascii. It html-escapes characters outside of the
     * latin1/cp1252 range. Characters in the U+0080-U+00FF range are encoded in
     * latin1/cp1252. That is the one way that we differ from chromium. We
     * html-escape those characters (U+0080-U+00FF) as well. That way the http
     * post is plain ascii, and should work regardless of which encoding the
     * server expects.
     * 
     * <p>
     * N.b. chromium doesn't indicate the encoding of the request in any way (no
     * charset in the content-type or anything like that). Also of note is that
     * when it considers the form page to be utf-8, it submits in utf-8. That's
     * part of the complicated behavior we don't want to try to simulate.
     */
    public static String escapeForMultipart(String str) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < str.length(); ) {
            int codepoint = str.codePointAt(i);
            if (codepoint <= 0x7f) {
                buf.appendCodePoint(codepoint);
            } else {
                buf.append("&#" + codepoint + ";");
            }
            i += Character.charCount(codepoint);
        }
        return buf.toString();
    }

    protected HttpEntity buildPostRequestEntity(CrawlURI curi) {
        String enctype = (String) curi.getData().get(CoreAttributeConstants.A_SUBMIT_ENCTYPE);
        if (enctype == null) {
            enctype = ContentType.APPLICATION_FORM_URLENCODED.getMimeType();
        }

        @SuppressWarnings("unchecked")
        List<NameValue> submitData = (List<NameValue>) curi.getData().get(CoreAttributeConstants.A_SUBMIT_DATA);

        if (enctype.equals(ContentType.APPLICATION_FORM_URLENCODED.getMimeType())) {
            LinkedList<NameValuePair> nvps = new LinkedList<NameValuePair>();
            for (NameValue nv: submitData) {
                nvps.add(new BasicNameValuePair(nv.name, nv.value));
            }
            try {
                return new UrlEncodedFormEntity(nvps, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            }
        } else if (enctype.equals(ContentType.MULTIPART_FORM_DATA.getMimeType())) {
            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
            entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
            for (NameValue nv: submitData) {
                entityBuilder.addTextBody(escapeForMultipart(nv.name),
                        escapeForMultipart(nv.value));
            }
            return entityBuilder.build();
        } else {
            throw new IllegalStateException("unsupported form submission enctype='" + enctype + "'");
        }
    }

    protected void configureRequestHeaders() {
        if (fetcher.getAcceptCompression()) {
            request.addHeader("Accept-Encoding", "gzip,deflate");
        }
        
        String from = fetcher.getUserAgentProvider().getFrom();
        if (StringUtils.isNotBlank(from)) {
            request.setHeader(HttpHeaders.FROM, from);
        }
        
        if (fetcher.getMaxLengthBytes() > 0 && fetcher.getSendRange()) {
            String rangeEnd = Long.toString(fetcher.getMaxLengthBytes() - 1);
            request.setHeader(HttpHeaders.RANGE, "bytes=0-" + rangeEnd);
        }

        if (fetcher.getSendConnectionClose()) {
            request.setHeader(HTTP.CONN_DIRECTIVE, HTTP.CONN_CLOSE);
        }
        
        // referer
        if (fetcher.getSendReferer() && !LinkContext.PREREQ_MISC.equals(curi.getViaContext())) {
            // RFC2616 says no referer header if referer is https and the url is not
            String via = Processor.flattenVia(curi);
            if (!StringUtils.isEmpty(via)
                    && !(curi.getVia().getScheme().equals(FetchHTTP.HTTPS_SCHEME) 
                            && curi.getUURI().getScheme().equals(FetchHTTP.HTTP_SCHEME))) {
                request.setHeader(HttpHeaders.REFERER, via);
            }
        }

        if (!curi.isPrerequisite()) {
            maybeAddConditionalGetHeader(fetcher.getSendIfModifiedSince(),
                    A_LAST_MODIFIED_HEADER, "If-Modified-Since");
            maybeAddConditionalGetHeader(fetcher.getSendIfNoneMatch(),
                    A_ETAG_HEADER, "If-None-Match");
        }

        // TODO: What happens if below method adds a header already added above,
        // e.g. Connection, Range, or Referer?
        for (String headerString: fetcher.getAcceptHeaders()) {
            String[] nameValue = headerString.split(": +");
            if (nameValue.length == 2) {
                request.addHeader(nameValue[0], nameValue[1]);
            } else {
                logger.warning("Invalid accept header: " + headerString);
            }
        }

        if (curi.getViaContext() != null
                && "a[data-remote='true']/@href".equals(curi.getViaContext().toString())) {
            request.addHeader("X-Requested-With", "XMLHttpRequest");
        }


        /*
         * set custom request headers in last interceptor, so they override
         * anything else (this could just as well belong in
         * configureHttpClientBuilder())
         */
        httpClientBuilder.addInterceptorLast(new HttpRequestInterceptor() {
            @Override
            public void process(HttpRequest request, HttpContext context) throws HttpException, IOException {
                @SuppressWarnings("unchecked")
                Map<String, String> uriCustomHeaders = (Map<String, String>) curi.getData().get("customHttpRequestHeaders");
                if (uriCustomHeaders != null) {
                    for (Entry<String, String> h: uriCustomHeaders.entrySet()) {
                        request.setHeader(h.getKey(), h.getValue());
                    }
                }
            }
        });

    }

    /**
     * Add the given conditional-GET header, if the setting is enabled and
     * a suitable value is available in the URI history. 
     * @param setting true/false enablement setting name to consult
     * @param sourceHeader header to consult in URI history
     * @param targetHeader header to set if possible
     */
    protected void maybeAddConditionalGetHeader(boolean conditional,
            String sourceHeader, String targetHeader) {
        if (conditional) {
            try {
                HashMap<String, Object>[] history = curi.getFetchHistory();
                int previousStatus = (Integer) history[0].get(A_STATUS);
                if (previousStatus <= 0) {
                    // do not reuse headers from any broken fetch
                    return;
                }
                String previousValue = (String) history[0].get(sourceHeader);
                if (previousValue != null) {
                    request.setHeader(targetHeader, previousValue);
                }
            } catch (RuntimeException e) {
                // for absent key, bad index, etc. just do nothing
            }
        }
    }

    protected void configureRequest() {
        if (fetcher.getIgnoreCookies()) {
            requestConfigBuilder.setCookieSpec(CookieSpecs.IGNORE_COOKIES);
        } else {
            requestConfigBuilder.setCookieSpec(CookieSpecs.BROWSER_COMPATIBILITY);
        }

        requestConfigBuilder.setConnectionRequestTimeout(fetcher.getSoTimeoutMs());
        requestConfigBuilder.setConnectTimeout(fetcher.getSoTimeoutMs());

        /*
         * XXX This socket timeout seems to be ignored. The one on the
         * socketConfig on the PoolingHttpClientConnectionManager in the
         * HttpClientBuilder is respected.
         */
        requestConfigBuilder.setSocketTimeout(fetcher.getSoTimeoutMs());        

        // local bind address
        String addressString = (String) fetcher.getAttributeEither(curi, FetchHTTP.HTTP_BIND_ADDRESS);
        if (StringUtils.isNotEmpty(addressString)) {
            try {
                InetAddress localAddress = InetAddress.getByName(addressString);
                requestConfigBuilder.setLocalAddress(localAddress);
            } catch (UnknownHostException e) {
                // Convert all to RuntimeException so get an exception out
                // if initialization fails.
                throw new RuntimeException("failed to resolve configured http bind address " + addressString, e);
            }
        }
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
     * @param request 
     * @param targetHost 
     * @param context
     *            The context to add credentials to.
     * @return True if prepopulated <code>method</code> with credentials AND
     *         the credentials came from the <code>curi</code>, not from the
     *         CrawlServer. The former is special in that if the
     *         <code>curi</curi> credentials
     * succeed, then the caller needs to promote them from the CrawlURI to the
     * CrawlServer so they are available for all subsequent CrawlURIs on this
     * server.
     */
    protected boolean populateTargetCredential() {
        // First look at the server avatars. Add any that are to be volunteered
        // on every request (e.g. RFC2617 credentials). Every time creds will
        // return true when we call 'isEveryTime().
        String serverKey;
        try {
            serverKey = CrawlServer.getServerKey(curi.getUURI());
        } catch (URIException e) {
            return false;
        }
        CrawlServer server = fetcher.getServerCache().getServerFor(serverKey);
        if (server.hasCredentials()) {
            for (Credential c: server.getCredentials()) {
                if (c.isEveryTime()) {
                    if (c instanceof HttpAuthenticationCredential) {
                        HttpAuthenticationCredential cred = (HttpAuthenticationCredential) c;
                        AuthScheme authScheme = fetcher.chooseAuthScheme(server.getHttpAuthChallenges(), HttpHeaders.WWW_AUTHENTICATE);
                        populateHttpCredential(targetHost, authScheme, cred.getLogin(), cred.getPassword());
                    } else {
                        populateHtmlFormCredential((HtmlFormCredential) c);
                    }
                }
            }
        }

        boolean result = false;

        // Now look in the curi. The Curi will have credentials loaded either
        // by the handle401 method if its a rfc2617 or it'll have been set into
        // the curi by the preconditionenforcer as this login uri came through.
        for (Credential c: curi.getCredentials()) {
            if (c instanceof HttpAuthenticationCredential) {
                HttpAuthenticationCredential cred = (HttpAuthenticationCredential) c;
                AuthScheme authScheme = fetcher.chooseAuthScheme(curi.getHttpAuthChallenges(), HttpHeaders.WWW_AUTHENTICATE);
                populateHttpCredential(targetHost, authScheme, cred.getLogin(), cred.getPassword());
                result = true;
            } else {
                result = populateHtmlFormCredential((HtmlFormCredential) c);
            }
        }

        return result;
    }
    
    protected void populateHttpProxyCredential() {
        String user = (String) fetcher.getAttributeEither(curi, "httpProxyUser");
        String password = (String) fetcher.getAttributeEither(curi, "httpProxyPassword");
        
        @SuppressWarnings("unchecked")
        Map<String,String> challenges = (Map<String, String>) fetcher.getKeyedProperties().get("proxyAuthChallenges");
        
        if (proxyHost != null && challenges != null && StringUtils.isNotEmpty(user)) {
            AuthScheme authScheme = fetcher.chooseAuthScheme(challenges, HttpHeaders.PROXY_AUTHENTICATE);
            populateHttpCredential(proxyHost, authScheme, user, password);
        }
    }
    
    protected boolean populateHtmlFormCredential(HtmlFormCredential cred) {
        if (cred.getFormItems() == null || cred.getFormItems().size() <= 0) {
            logger.severe("No form items for " + curi);
            return false;
        }
        
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        for (Entry<String, String> n: cred.getFormItems().entrySet()) {
            formParams.add(new BasicNameValuePair(n.getKey(), n.getValue()));
        }

        // XXX should it get charset from somewhere?
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formParams, HTTP.DEF_CONTENT_CHARSET);
        HttpEntityEnclosingRequest entityEnclosingRequest = (HttpEntityEnclosingRequest) request;
        entityEnclosingRequest.setEntity(entity);

        return true;
    }
    
    // http auth credential, either for proxy or target host
    protected void populateHttpCredential(HttpHost host, AuthScheme authScheme, String user, String password) {
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(user, password);
        
        AuthCache authCache = httpClientContext.getAuthCache();
        if (authCache == null) {
            authCache = new BasicAuthCache();
            httpClientContext.setAuthCache(authCache);
        }
        authCache.put(host, authScheme);

        if (httpClientContext.getCredentialsProvider() == null) {
            httpClientContext.setCredentialsProvider(new BasicCredentialsProvider());
        }
        httpClientContext.getCredentialsProvider().setCredentials(new AuthScope(host), credentials);
    }
    
    protected void configureHttpClientBuilder() throws URIException {
        String userAgent = curi.getUserAgent();
        if (userAgent == null) {
            userAgent = fetcher.getUserAgentProvider().getUserAgent();
        }
        httpClientBuilder.setUserAgent(userAgent);

        CookieStore cookieStore = fetcher.getCookieStore().cookieStoreFor(curi);
        httpClientBuilder.setDefaultCookieStore(cookieStore);
        
        connMan = buildConnectionManager();
        httpClientBuilder.setConnectionManager(connMan);
    }

    protected HttpClientConnectionManager buildConnectionManager() {
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.INSTANCE)
                .register(
                        "https",
                        new SSLConnectionSocketFactory(fetcher.sslContext(),
                                new AllowAllHostnameVerifier()) {

                            @Override
                            public Socket createLayeredSocket(
                                    final Socket socket, final String target,
                                    final int port, final HttpContext context)
                                    throws IOException {

                                return super.createLayeredSocket(socket,
                                        isDisableSNI() ? "" : target, port,
                                        context);
                            }
                        })
                .build();

        DnsResolver dnsResolver = new ServerCacheResolver(fetcher.getServerCache());

        ManagedHttpClientConnectionFactory connFactory = new ManagedHttpClientConnectionFactory(){
            private static final int DEFAULT_BUFSIZE = 8 * 1024;

            @Override
            public ManagedHttpClientConnection create(HttpRoute route,
                    ConnectionConfig config) {
                final ConnectionConfig cconfig = config != null ? config : ConnectionConfig.DEFAULT;
                CharsetDecoder chardecoder = null;
                CharsetEncoder charencoder = null;
                final Charset charset = cconfig.getCharset();
                final CodingErrorAction malformedInputAction = cconfig.getMalformedInputAction() != null ?
                        cconfig.getMalformedInputAction() : CodingErrorAction.REPORT;
                final CodingErrorAction unmappableInputAction = cconfig.getUnmappableInputAction() != null ?
                        cconfig.getUnmappableInputAction() : CodingErrorAction.REPORT;
                if (charset != null) {
                    chardecoder = charset.newDecoder();
                    chardecoder.onMalformedInput(malformedInputAction);
                    chardecoder.onUnmappableCharacter(unmappableInputAction);
                    charencoder = charset.newEncoder();
                    charencoder.onMalformedInput(malformedInputAction);
                    charencoder.onUnmappableCharacter(unmappableInputAction);
                }
                return new RecordingHttpClientConnection(DEFAULT_BUFSIZE,
                        DEFAULT_BUFSIZE, chardecoder, charencoder,
                        cconfig.getMessageConstraints(), null, null,
                        DefaultHttpRequestWriterFactory.INSTANCE,
                        DefaultHttpResponseParserFactory.INSTANCE);
            }
        };
        BasicHttpClientConnectionManager connMan = new BasicHttpClientConnectionManager(
                socketFactoryRegistry, connFactory, null, dnsResolver);
        
        SocketConfig.Builder socketConfigBuilder = SocketConfig.custom();
        socketConfigBuilder.setSoTimeout(fetcher.getSoTimeoutMs());
        connMan.setSocketConfig(socketConfigBuilder.build());
        
        return connMan;
    }
    
    protected static class RecordingHttpClientConnection extends DefaultBHttpClientConnection
    implements ManagedHttpClientConnection {

        private static final AtomicLong COUNTER = new AtomicLong();
        private String id;

        public RecordingHttpClientConnection(
                final int buffersize,
                final int fragmentSizeHint,
                final CharsetDecoder chardecoder,
                final CharsetEncoder charencoder,
                final MessageConstraints constraints,
                final ContentLengthStrategy incomingContentStrategy,
                final ContentLengthStrategy outgoingContentStrategy,
                final HttpMessageWriterFactory<HttpRequest> requestWriterFactory,
                final HttpMessageParserFactory<HttpResponse> responseParserFactory) {
            super(buffersize, fragmentSizeHint, chardecoder, charencoder,
                    constraints, incomingContentStrategy, outgoingContentStrategy,
                    requestWriterFactory, responseParserFactory);
            id = "recording-http-connection-" + Long.toString(COUNTER.getAndIncrement());
        }

        @Override
        protected InputStream getSocketInputStream(final Socket socket) throws IOException {
            Recorder recorder = Recorder.getHttpRecorder();
            if (recorder != null) {   // XXX || (isSecure() && isProxied())) {
                return recorder.inputWrap(super.getSocketInputStream(socket));
            } else {
                return super.getSocketInputStream(socket);
            }
        }

        @Override
        protected OutputStream getSocketOutputStream(final Socket socket) throws IOException {
            Recorder recorder = Recorder.getHttpRecorder();
            if (recorder != null) {   // XXX || (isSecure() && isProxied())) {
                return recorder.outputWrap(super.getSocketOutputStream(socket));
            } else {
                return super.getSocketOutputStream(socket);
            }
        }
        
        @Override
        public void close() throws IOException {
        	super.close();
        	
            /*
             * Need to do this to avoid "java.io.IOException: RIS already open"
             * on urls that are retried within httpcomponents. Exercised by
             * FetchHTTPTests.testNoResponse()
             */
            Recorder recorder = Recorder.getHttpRecorder();
            if (recorder != null) {
                recorder.close();
                recorder.closeRecorders();
            }
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public SSLSession getSSLSession() {
            final Socket socket = super.getSocket();
            if (socket instanceof SSLSocket) {
                return ((SSLSocket) socket).getSession();
            } else {
                return null;
            }
        }
        
        @Override
        public Socket getSocket() {
            return super.getSocket();
        }
    }
    
    protected static final HttpRoutePlanner ROUTE_PLANNER = new HttpRoutePlanner() {
        @Override
        public HttpRoute determineRoute(HttpHost host, HttpRequest request,
                HttpContext context) throws HttpException {
            Args.notNull(host, "Target host");
            Args.notNull(request, "Request");
            final HttpClientContext clientContext = HttpClientContext.adapt(context);
            final RequestConfig config = clientContext.getRequestConfig();
            final InetAddress local = config.getLocalAddress();
            HttpHost proxy = config.getProxy();

            final HttpHost target;
            if (host.getPort() > 0
                    && (host.getSchemeName().equalsIgnoreCase("http") && host.getPort() == 80
                    || host.getSchemeName().equalsIgnoreCase("https") && host.getPort() == 443)) {
                target = new HttpHost(host.getHostName(), -1, host.getSchemeName());
            } else {
                target = host;
            }
            final boolean secure = target.getSchemeName().equalsIgnoreCase("https");
            if (proxy == null) {
                return new HttpRoute(target, local, secure);
            } else {
                return new HttpRoute(target, local, proxy, secure);
            }

        }
        
    };
    
    protected void initHttpClientBuilder() {
        httpClientBuilder = HttpClientBuilder.create();
        
        httpClientBuilder.setDefaultAuthSchemeRegistry(FetchHTTP.AUTH_SCHEME_REGISTRY);
        
        // we handle content compression manually
        httpClientBuilder.disableContentCompression();
        
        // we handle redirects manually
        httpClientBuilder.disableRedirectHandling();
        
        httpClientBuilder.setRoutePlanner(ROUTE_PLANNER);
    }
    
    public HttpResponse execute() throws ClientProtocolException, IOException {
        HttpClient httpClient = httpClientBuilder.build();
        
        RequestConfig requestConfig = requestConfigBuilder.build();
        httpClientContext.setRequestConfig(requestConfig);
        
        return httpClient.execute(targetHost, request, httpClientContext);
    }
}
