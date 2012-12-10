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
import java.util.Date;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.hq.GzipInflatingHttpEntityWrapper;
import org.archive.modules.hq.recrawl.RecrawlDataSchemaBase;
import org.archive.modules.recrawl.RecrawlAttributeConstants;
import org.archive.util.ArchiveUtils;

/**
 * A {@link Processor} for retrieving recrawl info from remote Wayback Machine index.
 * This is currently in the early stage of experiment. Both low-level protocol and WBM API
 * semantics will certainly undergo several revisions.
 * <p>Current interface:</p>
 * <p>http://web-beta.archive.org/cdx/search?url=archive.org&startDate=1999 will return raw
 * CDX lines for archive.org, since 1999-01-01 00:00:00.
 * </p>
 * <p>As index is updated in a separate batch processing job, there's no "Store" counterpart.</p>
 * @contributor Kenji Nagahashi.
 */
public class WbmPersistLoadProcessor extends Processor {
  private static final Log log = LogFactory.getLog(WbmPersistLoadProcessor.class);

    private HttpClient client;
    
    private String queryURL = "http://web-beta.archive.org/cdx/search";
    public void setQueryURL(String queryURL) {
      this.queryURL = queryURL;
    }
    public String getQueryURL() {
      return queryURL;
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
     * socket timeout (SO_TIMEOUT) for HTTP cient in milliseconds.
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
    public void setMaxConnections(int maxConnections) {
      this.maxConnections = maxConnections;
      if (client != null) {
	((ThreadSafeClientConnManager)client.getConnectionManager())
	.setDefaultMaxPerRoute(this.maxConnections);
      }
    }
    
    // statistics
    private AtomicLong loadedCount = new AtomicLong();
    public long getLoadedCount() {
      return loadedCount.get();
    }
    private AtomicLong failedCount = new AtomicLong();
    public long getFailedCount() {
      return failedCount.get();
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
	ThreadSafeClientConnManager conman = new ThreadSafeClientConnManager();
	conman.setDefaultMaxPerRoute(maxConnections);
	final DefaultHttpClient client = new DefaultHttpClient(conman);
	HttpParams params = client.getParams();
	params.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, socketTimeout);
	params.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeout);
	// setup request/response intercepter for handling gzip-compressed response.
	client.addRequestInterceptor(new HttpRequestInterceptor() {
          @Override
          public void process(final HttpRequest request, final HttpContext context)
                  throws HttpException, IOException {
              if (!request.containsHeader("Accept-Encoding")) {
                  request.addHeader("Accept-Encoding", "gzip");
              }
          }
	});
	client.addResponseInterceptor(new HttpResponseInterceptor() {
	  @Override
	  public void process(final HttpResponse response, final HttpContext context)
	      throws HttpException, IOException {
	    HttpEntity entity = response.getEntity();
	    Header ceheader = entity.getContentEncoding();
	    if (ceheader != null && contains(ceheader.getElements(), "gzip")) {
	      response.setEntity(new GzipInflatingHttpEntityWrapper(response.getEntity()));
	    }
	  }
	});
	this.client = client;
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
    
    protected String buildURL(CrawlURI curi) {
      // we don't need to pass scheme part, but no problem passing it.
      StringBuilder sb = new StringBuilder(queryURL);
      sb.append("?url=");
      try {
	sb.append(URLEncoder.encode(curi.toString(), "UTF-8"));
      } catch (UnsupportedEncodingException ex) {
	// expecting it's never thrown
      }
      long range = queryRangeSecs;
      if (range > 0) {
	Date now = new Date();
	Date startDate = new Date(now.getTime() - range*1000);
	sb.append("&startDate=");
	sb.append(ArchiveUtils.get14DigitDate(startDate));
      }
      sb.append("&limit=1&last=true");
      return sb.toString();
    }
    
    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) throws InterruptedException {
      final String url = buildURL(curi);
      HttpGet m = new HttpGet(url);
      HttpEntity entity = null;
      int attempts = 0;
      do {
	if (Thread.interrupted())
	  throw new InterruptedException("interrupted while GET " + url);
	if (attempts > 0) {
	  Thread.sleep(5000);
	}
	try {
	  HttpResponse resp = getHttpClient().execute(m);
	  StatusLine sl = resp.getStatusLine();
	  if (sl.getStatusCode() != 200) {
	    log.error("GET " + url + " failed with status=" + sl.getStatusCode() + " " + sl.getReasonPhrase());
	    entity = resp.getEntity();
	    entity.getContent().close();
	    entity = null;
	    continue;
	  }
	  entity = resp.getEntity();
	} catch (Exception ex) {
	  log.error("GET " + url + " failed with error " + ex.getMessage());
	}
      } while (entity == null && ++attempts < 3);
      if (entity == null) {
	log.error("giving up on GET " + url + " after " + attempts + " attempts");
	failedCount.incrementAndGet();
	return ProcessResult.PROCEED;
      }
      InputStream is = null;
      String hash = null;
      try {
	hash = getLastHash(is = entity.getContent());
      } catch (IOException ex) {
      } finally {
	if (is != null)
	  ArchiveUtils.closeQuietly(is);
      }
      if (hash != null) {
	Map<String, Object> history = RecrawlDataSchemaBase.getFetchHistory(curi);
	history.put(RecrawlAttributeConstants.A_CONTENT_DIGEST, contentDigestScheme + hash);
	loadedCount.incrementAndGet();
      } else {
	failedCount.incrementAndGet();
      }
      return ProcessResult.PROCEED;
    }
    
    protected String getLastHash(InputStream is) throws IOException {
      // read CDX lines, save most recent (at the end) hash.
      ByteBuffer buffer = ByteBuffer.allocate(32);
      int field = 0;
      int c;
      do {
	if (field == 5) {
	  buffer.clear();
	  while (buffer.remaining() > 0) {
	    c = is.read();
	    if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
		buffer.put((byte)c);
	    } else {
	      break;
	    }
	  }
	  if (buffer.remaining() == 0) {
	    c = is.read();
	    if (c == ' ') {
	      field++;
	      continue;
	    }
	  } else {
	    buffer.clear();
	  }
	}
	while  (true) {
	  c = is.read();
	  if (c == -1) {
	    break;
	  } else if (c == '\n') {
	    field = 0;
	    break;
	  } else if (c == ' ') {
	    field++;
	    break;
	  }
	}
      } while (c != -1);
      if (buffer.remaining() == 0) {
	return new String(buffer.array());
      } else {
	return null;
      }
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
}
