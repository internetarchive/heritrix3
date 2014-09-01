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
package org.archive.modules.recrawl.wbm;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.recrawl.FetchHistoryHelper;
import org.archive.modules.recrawl.RecrawlAttributeConstants;
import org.archive.util.ArchiveUtils;
import org.archive.util.DateUtils;

/**
 * A {@link Processor} for retrieving recrawl info from remote Wayback Machine index.
 * This is currently in the early stage of experiment. Both low-level protocol and WBM API
 * semantics will certainly undergo several revisions.
 * <p>Current interface:</p>
 * <p>http://web-beta.archive.org/cdx/search/cdx?url=archive.org&startDate=1999 will return raw
 * CDX lines for archive.org, since 1999-01-01 00:00:00.
 * </p>
 * <p>As index is updated in a separate batch processing job, there's no "Store" counterpart.</p>
 * @contributor Kenji Nagahashi.
 */
public class WbmPersistLoadProcessor extends Processor {
    private static final Log log = LogFactory.getLog(WbmPersistLoadProcessor.class);

    private HttpClient client;
    private PoolingHttpClientConnectionManager conman;

    private int historyLength = 2;

    public void setHistoryLength(int historyLength) {
        this.historyLength = historyLength;
    }
    public int getHistoryLength() {
        return historyLength;
    }

    // ~Jan 2, 2013
    //private String queryURL = "http://web-beta.archive.org/cdx/search";
    //private String queryURL = "http://web.archive.org/cdx/search/cdx";
    // ~Sep 26, 2013
    private String queryURL = "http://wwwb-dedup.us.archive.org:8083/web/timemap/cdx?url=$u&limit=-1";
    public void setQueryURL(String queryURL) {
        this.queryURL = queryURL;
        prepareQueryURL();
    }
    public String getQueryURL() {
        return queryURL;
    }
    
    public interface FormatSegment {
        void print(StringBuilder sb, String[] args);
    }
    private static class StaticSegment implements FormatSegment {
        String s;
        public StaticSegment(String s) {
            this.s = s;
        }
        public void print(StringBuilder sb, String[] args) {
            sb.append(s);
        }
    }
    private static class InterpolateSegment implements FormatSegment {
        int aidx;
        public InterpolateSegment(int aidx) {
            this.aidx = aidx;
        }
        public void print(StringBuilder sb, String[] args) {
            sb.append(args[aidx]);
        }
    }
    private FormatSegment[] preparedQueryURL;
    
    /**
     * pre-scan queryURL template so that actual queryURL can be built
     * with minimal processing.
     */
    private void prepareQueryURL() {
        List<FormatSegment> segments = new ArrayList<FormatSegment>();
        final int l = queryURL.length();
        int p = 0;
        int q;
        while (p < l && (q = queryURL.indexOf('$', p)) >= 0) {
            if (q + 2 > l) {
                // '$' at the end. keep it as if it were '$$'
                break;
            }
            if (q > p) {
                segments.add(new StaticSegment(queryURL.substring(p, q)));
            }
            char c = queryURL.charAt(q + 1);
            if (c == 'u') {
                segments.add(new InterpolateSegment(0));
            } else if (c == 's') {
                segments.add(new InterpolateSegment(1));
            } else {
                // copy '$'-sequence so that it's easy to spot errors
                segments.add(new StaticSegment(queryURL.substring(q, q + 2)));
            }
            p = q + 2;
        }
        if (p < l) {
            segments.add(new StaticSegment(queryURL.substring(p)));
        }
        preparedQueryURL = segments.toArray(new FormatSegment[segments.size()]);
    }

    private String contentDigestScheme = "sha1:";
    /**
     * set Content-Digest scheme string to prepend to the hash string found in CDX.
     * Heritrix's Content-Digest comparison including this part.
     * {@code "sha1:"} by default.
     * @param contentDigestScheme
     */
    public void setContentDigestScheme(String contentDigestScheme) {
        this.contentDigestScheme = contentDigestScheme;
    }
    public String getContentDigestScheme() {
        return contentDigestScheme;
    }
    private int socketTimeout = 10000;
    /**
     * socket timeout (SO_TIMEOUT) for HTTP client in milliseconds.
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }
    public int getSocketTimeout() {
        return socketTimeout;
    }

    private int connectionTimeout = 10000;
    /**
     * connection timeout for HTTP client in milliseconds. 
     * @param connectionTimeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    private int maxConnections = 10;
    public int getMaxConnections() {
        return maxConnections;
    }
    public synchronized void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
        if (conman != null) {
            if (conman.getMaxTotal() < this.maxConnections)
                conman.setMaxTotal(this.maxConnections);
            conman.setDefaultMaxPerRoute(this.maxConnections);
        }
    }
    private boolean gzipAccepted = false;
    public boolean isGzipAccepted() {
        return gzipAccepted;
    }
    /**
     * if set to true, {@link WbmPersistLoadProcessor} adds a header
     * {@code Accept-Encoding: gzip} to HTTP requests. New CDX server
     * see this header to decide whether to compress the response. it is also
     * possible to override gzipAccepted=true setting with gzip=false 
     * request parameter.
     * It is off by default, as it should make little sense to compress single
     * line of CDX.
     * @param gzipAccepted true to allow gzip compression.
     */
    public void setGzipAccepted(boolean gzipAccepted) {
        this.gzipAccepted = gzipAccepted;
    }
    
    private Map<String, String> requestHeaders = new ConcurrentHashMap<String, String>(1, 0.75f, 2);
    public Map<String, String> getRequestHeaders() {
        return requestHeaders;
    }
    /**
     * all key-value pairs in this map will be added as HTTP headers.
     * typically used for providing authentication cookies. this method
     * makes a copy of {@requestHeaders}.
     * <em>note:</em> this property may be dropped in the future if
     * I come up with better interface.
     * @param requestHeaders map of &lt;header-name, header-value>.
     */
    public void setRequestHeaders(Map<String, String> requestHeaders) {
        if (requestHeaders == null) {
            this.requestHeaders.clear();
        } else {
            // TODO: mmm, ConcurrentHashMap may be overkill. simple synchronized Hashtable would work
            // just okay?
            ConcurrentHashMap<String, String> m = new ConcurrentHashMap<String, String>(1, 0.75f, 2);
            m.putAll(requestHeaders);
            this.requestHeaders = m;
        }
    }    

    // statistics
    private AtomicLong loadedCount = new AtomicLong();
    /**
     * number of times successfully loaded recrawl info.
     * @return long
     */
    public long getLoadedCount() {
        return loadedCount.get();
    }
    private AtomicLong missedCount = new AtomicLong();
    /**
     * number of times getting no recrawl info.
     * @return long
     */
    public long getMissedCount() {
        return missedCount.get();
    }
    private AtomicLong errorCount = new AtomicLong();
    /**
     * number of times cdx-server API call failed. 
     * @return long
     */
    public long getErrorCount() {
        return errorCount.get();
    }
    
    private AtomicLong cumulativeFetchTime = new AtomicLong();
    /**
     * total milliseconds spent in API call.
     * it is a sum of time waited for next available connection,
     * and actual HTTP request-response round-trip, across all threads.
     * @return
     */
    public long getCumulativeFetchTime() {
        return cumulativeFetchTime.get();
    }

    public void setHttpClient(HttpClient client) {
        this.client = client;
    }

    // XXX HttpHeadquarterAdapter has the same code. move to common library.
    private static boolean contains(HeaderElement[] elements, String value) {
        for (int i = 0; i < elements.length; i++) {
            if (elements[i].getName().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
    public synchronized HttpClient getHttpClient() {
        if (client == null) {
            if (conman == null) {
                conman = new PoolingHttpClientConnectionManager();
                conman.setDefaultMaxPerRoute(maxConnections);
                conman.setMaxTotal(Math.max(conman.getMaxTotal(), maxConnections));
            }
            HttpClientBuilder builder = HttpClientBuilder.create()
                    .disableCookieManagement()
                    .setConnectionManager(conman);
            builder.useSystemProperties();
            // TODO: use setDefaultHeaders for adding requestHeaders? It's a bit painful
            // because we need to convert it to a Collection of Header objects.

            // config code for older version of httpclient.
//            builder.setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(socketTimeout).build());
//            HttpParams params = client.getParams();
//            params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, socketTimeout);
//            params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeout);
            // setup request/response intercepter for handling gzip-compressed response.
            // Disabled because httpclient 4.3.3 sends "Accept-Encoding: gzip,deflate" by
            // default. Response parsing will fail If gzip-decompression ResponseInterceptor
            // is installed.
            builder.addInterceptorLast(new HttpRequestInterceptor() {
                @Override
                public void process(final HttpRequest request, final HttpContext context) {
//                    System.err.println("RequestInterceptor");
//                    if (request.containsHeader("Accept-Encoding")) {
//                        System.err.println("already has Accept-Encoding: " + request.getHeaders("Accept-Encoding")[0]);
//                    }
//                    if (gzipAccepted) {
//                        if (!request.containsHeader("Accept-Encoding")) {
//                            request.addHeader("Accept-Encoding", "gzip");
//                        }
//                    }
                    // add extra headers configured.
                    if (requestHeaders != null) {
                        for (Entry<String, String> ent : requestHeaders.entrySet()) {
                            request.addHeader(ent.getKey(), ent.getValue());
                        }
                    }
                }
            });
//            builder.addInterceptorFirst(new HttpResponseInterceptor() {
//                @Override
//                public void process(final HttpResponse response, final HttpContext context)
//                        throws HttpException, IOException {
//                    HttpEntity entity = response.getEntity();
//                    Header ceheader = entity.getContentEncoding();
//                    if (ceheader != null && contains(ceheader.getElements(), "gzip")) {
//                        response.setEntity(new GzipInflatingHttpEntityWrapper(response.getEntity()));
//                    }
//                }
//            });
            this.client = builder.build();
        }
        return client;
    }

    private long queryRangeSecs = 6L*30*24*3600;
    /**
     * 
     * @param queryRangeSecs
     */
    public void setQueryRangeSecs(long queryRangeSecs) {
        this.queryRangeSecs = queryRangeSecs;
    }
    public long getQueryRangeSecs() {
        return queryRangeSecs;
    }

    private String buildStartDate() {
        final long range = queryRangeSecs;
        if (range <= 0)
            return ArchiveUtils.get14DigitDate(new Date(0));
        Date now = new Date();
        Date startDate = new Date(now.getTime() - range*1000);
        return ArchiveUtils.get14DigitDate(startDate);
    }
    
    protected String buildURL(String url) {
        // we don't need to pass scheme part, but no problem passing it.
        StringBuilder sb = new StringBuilder();
        final FormatSegment[] segments = preparedQueryURL;
        String encodedURL;
        try {
            encodedURL = URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            encodedURL = url;
        }
        final String[] args = new String[] {
          encodedURL,
          buildStartDate()
        };
        for (FormatSegment fs : segments) {
            fs.print(sb, args);
        }
        return sb.toString();
    }

    public WbmPersistLoadProcessor() {
        prepareQueryURL();
    }
    
    protected InputStream getCDX(String qurl) throws InterruptedException, IOException {
        final String url = buildURL(qurl);
        HttpGet m = new HttpGet(url);
        m.setConfig(RequestConfig.custom().setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout).build());
        HttpEntity entity = null;
        int attempts = 0;
        do {
            if (Thread.interrupted())
                throw new InterruptedException("interrupted while GET " + url);
            if (attempts > 0) {
                Thread.sleep(5000);
            }
            try {
                long t0 = System.currentTimeMillis();
                HttpResponse resp = getHttpClient().execute(m);
                cumulativeFetchTime.addAndGet(System.currentTimeMillis() - t0);
                StatusLine sl = resp.getStatusLine();
                if (sl.getStatusCode() != 200) {
                    log.error("GET " + url + " failed with status=" + sl.getStatusCode() + " " + sl.getReasonPhrase());
                    entity = resp.getEntity();
                    entity.getContent().close();
                    entity = null;
                    continue;
                }
                entity = resp.getEntity();
            } catch (IOException ex) {
                log.error("GEt " + url + " failed with error " + ex.getMessage());
            } catch (Exception ex) {
                log.error("GET " + url + " failed with error ", ex);
            }
        } while (entity == null && ++attempts < 3);
        if (entity == null) {
            throw new IOException("giving up on GET " + url + " after " + attempts + " attempts");
        }
        return entity.getContent();
    }
    
    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) throws InterruptedException {
        InputStream is;
        try {
            is = getCDX(curi.toString());
        } catch (IOException ex) {
            log.error(ex.getMessage());
            errorCount.incrementAndGet();
            return ProcessResult.PROCEED;
        }
        Map<String, Object> info = null;
        try {
            info = getLastCrawl(is);
        } catch (IOException ex) {
            log.error("error parsing response", ex);
        } finally {
            if (is != null)
                ArchiveUtils.closeQuietly(is);
        }
        if (info != null) {
            Map<String, Object> history = FetchHistoryHelper.getFetchHistory(curi,
                    (Long)info.get(FetchHistoryHelper.A_TIMESTAMP), historyLength);
            if (history != null)
                history.putAll(info);
            loadedCount.incrementAndGet();
        } else {
            missedCount.incrementAndGet();
        }
        return ProcessResult.PROCEED;
    }

    protected HashMap<String, Object> getLastCrawl(InputStream is) throws IOException {
        // read CDX lines, save most recent (at the end) hash.
        ByteBuffer buffer = ByteBuffer.allocate(32);
        ByteBuffer tsbuffer = ByteBuffer.allocate(14);
        int field = 0;
        int c;
        do {
            c = is.read();
            if (field == 1) {
                // 14-digits timestamp
                tsbuffer.clear();
                while (Character.isDigit(c) && tsbuffer.remaining() > 0) {
                    tsbuffer.put((byte)c);
                    c = is.read();
                }
                if (c != ' ' || tsbuffer.position() != 14) {
                    tsbuffer.clear();
                }
                // fall through to skip the rest
            } else if (field == 5) {
                buffer.clear();
                while ((c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') && buffer.remaining() > 0) {
                    buffer.put((byte)c);
                    c = is.read();
                }
                if (c != ' ' || buffer.position() != 32) {
                    buffer.clear();
                }
                // fall through to skip the rest
            }
            while (true) {
                if (c == -1) {
                    break;
                } else if (c == '\n') {
                    field = 0;
                    break;
                } else if (c == ' ') {
                    field++;
                    break;
                }
                c = is.read();
            }
        } while (c != -1);

        HashMap<String, Object> info = new HashMap<String, Object>();
        if (buffer.remaining() == 0) {
            info.put(RecrawlAttributeConstants.A_CONTENT_DIGEST, contentDigestScheme + new String(buffer.array()));
        }
        if (tsbuffer.remaining() == 0) {
            try {
                long ts = DateUtils.parse14DigitDate(new String(tsbuffer.array())).getTime();
                // A_TIMESTAMP has been used for sorting history long before A_FETCH_BEGAN_TIME
                // field was introduced. Now FetchHistoryProcessor fails if A_FETCH_BEGAN_TIME is
                // not set. We could stop storing A_TIMESTAMP and sort by A_FETCH_BEGAN_TIME.
                info.put(FetchHistoryHelper.A_TIMESTAMP, ts);
                info.put(CoreAttributeConstants.A_FETCH_BEGAN_TIME, ts);
            } catch (ParseException ex) {
            }
        }
        return info.isEmpty() ? null : info;
    }

    /**
     * unused.
     */
    @Override
    protected void innerProcess(CrawlURI uri) throws InterruptedException {
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        // TODO: we want deduplicate robots.txt, too.
        //if (uri.isPrerequisite()) return false;
        String scheme = uri.getUURI().getScheme();
        if (!(scheme.equals("http") || scheme.equals("https"))) return false;
        return true;
    }
    
    /**
     * main entry point for quick test.
     * @param args
     */
    public static void main(String[] args) throws Exception {
        String url = args[0];
        String cookie = args.length > 1 ? args[1] : null;
        WbmPersistLoadProcessor wp = new WbmPersistLoadProcessor();
        if (cookie != null) {
            wp.setRequestHeaders(Collections.singletonMap("Cookie", cookie));
        }
        InputStream is = wp.getCDX(url);
        byte[] b = new byte[1024];
        int n;
        while ((n = is.read(b)) > 0) {
            System.out.write(b, 0, n);
        }
        is.close();
    }
}
