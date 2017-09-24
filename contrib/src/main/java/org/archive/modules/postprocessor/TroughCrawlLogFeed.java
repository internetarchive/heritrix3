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
package org.archive.modules.postprocessor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.frontier.AbstractFrontier;
import org.archive.crawler.frontier.BdbFrontier;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.net.ServerCache;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

/**
 * Post insert statements for these two tables.
 * 
 * CREATE TABLE crawled_url(
 *         id INTEGER PRIMARY KEY AUTOINCREMENT,
 *         timestamp DATETIME,
 *         status_code INTEGER,
 *         size BIGINT,
 *         payload_size BIGINT,
 *         url VARCHAR(4000),
 *         hop_path VARCHAR(255),
 *         is_seed_redirect INTEGER(1),
 *         via VARCHAR(255),
 *         mimetype VARCHAR(255),
 *         content_digest VARCHAR(255),
 *         seed VARCHAR(4000),
 *         is_duplicate INTEGER(1),
 *         warc_filename VARCHAR(255),
 *         warc_offset VARCHAR(255),
 *         warc_content_bytes BIGINT,
 *         host VARCHAR(255));
 *
 * CREATE TABLE uncrawled_url(
 *         id INTEGER PRIMARY KEY AUTOINCREMENT,
 *         timestamp DATETIME,
 *         url VARCHAR(4000),
 *         hop_path VARCHAR(255),
 *         status_code INTEGER,
 *         via VARCHAR(255),
 *         seed VARCHAR(4000),
 *         host VARCHAR(255));
 * 
 * import doublethink
 * import requests
 * database_id = '300001'
 * rethinker = doublethink.Rethinker(db="trough_configuration", servers=settings['RETHINKDB_HOSTS'])
 * services = doublethink.ServiceRegistry(rethinker)
 * master_node = services.unique_service('trough-sync-master')
 * write_url = requests.post(master_node['url']), database_id)
 *
 * response = requests.post(write_url, 'INSERT INTO crawled_urls SET ...')
 * if not response == 'OK':
 *     logging.warn('there was an error, probably')
 * 
 * https://github.com/jkafader/trough
 * 
 */
public class TroughCrawlLogFeed extends Processor implements Lifecycle {

    protected static final Logger logger = Logger.getLogger(TroughCrawlLogFeed.class.getName());

    protected static final int BATCH_MAX_TIME_MS = 20 * 1000;
    protected static final int BATCH_MAX_SIZE = 500;

    protected List<String> crawledBatch = new ArrayList<String>();
    protected long crawledBatchLastTime = System.currentTimeMillis();
    protected List<String> uncrawledBatch = new ArrayList<String>();
    protected long uncrawledBatchLastTime = System.currentTimeMillis();

    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    /** Autowired frontier, needed to determine when a url is finished. */
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    protected String writeUrl = null;
    public void setWriteUrl(String writeUrl) {
        this.writeUrl = writeUrl;
    }
    public String getWriteUrl() {
        return writeUrl;
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        if (frontier instanceof AbstractFrontier) {
            return !((AbstractFrontier) frontier).needsReenqueuing(curi);
        } else {
            return false;
        }
    }

    @Override
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        if (!crawledBatch.isEmpty()) {
            postCrawledBatch();
        }

        if (frontier instanceof BdbFrontier) {
            Closure closure = new Closure() {
                public void execute(Object o) {
                    try {
                        innerProcess((CrawlURI) o);
                    } catch (InterruptedException e) {
                    }
                }
            };

            logger.info("dumping " + frontier.queuedUriCount() + " queued urls to trough feed");
            ((BdbFrontier) frontier).forAllPendingDo(closure);
            logger.info("dumped " + frontier.queuedUriCount() + " queued urls to trough feed");
        } else {
            logger.warning("frontier is not a BdbFrontier, cannot dump queued urls to trough feed");
        }

        // String rateStr = String.format("%1.1f", 0.01 * stats.errors / stats.total);
        // logger.info("final error count: " + stats.errors + "/" + stats.total + " (" + rateStr + "%)");
        super.stop();
    }

    protected String sqlValue(Object o) {
        if (o == null) {
            return "null";
        } else if (o instanceof Date) {
            return "datetime('" + ArchiveUtils.getLog14Date((Date) o) + "')";
        } else if (o instanceof Number) {
            return o.toString();
        } else {
            return "'" + StringEscapeUtils.escapeSql(o.toString()) + "'";
        }
    }

    transient protected CloseableHttpClient _httpClient;
    protected CloseableHttpClient httpClient() {
        if (_httpClient == null) {
            _httpClient = HttpClientBuilder.create().build();
        }
        return _httpClient;
    }

    protected void post(String statement) {
        logger.info("posting to " + writeUrl + " - " + statement.substring(0, 200) + "...");
        HttpPost httpPost = new HttpPost(writeUrl);
        try {
            httpPost.setEntity(new StringEntity(statement));
            HttpResponse response = httpClient().execute(httpPost);
            String payload = IOUtils.toString(response.getEntity().getContent(), Charset.forName("UTF-8"));
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("expected 200 OK but got " + response.getStatusLine() + " - " + payload);
            }
        } catch (Exception e) {
            logger.warning("problem posting " + statement + " to " + writeUrl + " - " + e);
        } finally {
            httpPost.releaseConnection();
        }

    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        if (curi.getFetchStatus() > 0) {
            // compute warcContentBytes
            long warcContentBytes;
            if (curi.getExtraInfo().opt("warcFilename") != null) {
                if (curi.isRevisit()) {
                    warcContentBytes = curi.getContentSize() - curi.getContentLength();
                } else {
                    warcContentBytes = curi.getContentSize();
                }
            } else {
                warcContentBytes = 0;
            }
            synchronized (crawledBatch) {
                crawledBatch.add("(" + sqlValue(new Date(curi.getFetchBeginTime())) + ", "
                        + sqlValue(curi.getFetchStatus()) + ", "
                        + sqlValue(curi.getContentSize()) + ", "
                        + sqlValue(curi.getContentLength()) + ", "
                        + sqlValue(curi) + ", "
                        + sqlValue(curi.getPathFromSeed()) + ", "
                        + sqlValue(curi.isSeed() && !"".equals(curi.getPathFromSeed()) ? 1 : 0) + ", "
                        + sqlValue(curi.getVia()) + ", "
                        + sqlValue(MimetypeUtils.truncate(curi.getContentType())) + ", "
                        + sqlValue(curi.getContentDigestSchemeString()) + ", "
                        + sqlValue(curi.getSourceTag()) + ", "
                        + sqlValue(curi.isRevisit() ? 1 : 0) + ", "
                        + sqlValue(curi.getExtraInfo().opt("warcFilename")) + ", "
                        + sqlValue(curi.getExtraInfo().opt("warcOffset")) + ", "
                        + sqlValue(warcContentBytes) + ", "
                        + sqlValue(serverCache.getHostFor(curi.getUURI()).getHostName()) + ")");
                if (crawledBatch.size() >= BATCH_MAX_SIZE || System.currentTimeMillis() - crawledBatchLastTime > BATCH_MAX_TIME_MS) {
                    postCrawledBatch();
                }
            }
        } else {
            synchronized (uncrawledBatch) {
                uncrawledBatch.add("("
                        + sqlValue(new Date()) + ", "
                        + sqlValue(curi) + ", "
                        + sqlValue(curi.getPathFromSeed()) + ", "
                        + sqlValue(curi.getFetchStatus()) + ", "
                        + sqlValue(curi.getVia()) + ", "
                        + sqlValue(curi.getSourceTag()) + ", "
                        + sqlValue(serverCache.getHostFor(curi.getUURI()).getHostName()) + ")");
                if (uncrawledBatch.size() >= BATCH_MAX_SIZE || System.currentTimeMillis() - uncrawledBatchLastTime > BATCH_MAX_TIME_MS) {
                    postUncrawledBatch();
                }
            }
        }
    }

    protected void postCrawledBatch() {
        synchronized (crawledBatch) {
            String sql = "insert into crawled_url ("
                    + "timestamp, status_code, size, payload_size, url, hop_path, is_seed_redirect, "
                    + "via, mimetype, content_digest, seed, is_duplicate, warc_filename, "
                    + "warc_offset, warc_content_bytes, host)  values "
                    + StringUtils.join(crawledBatch, ", ")
                    + ";";
            post(sql);
            crawledBatchLastTime = System.currentTimeMillis();
            crawledBatch.clear();
        }
    }

    protected void postUncrawledBatch() {
        synchronized (uncrawledBatch) {
            String sql = "insert into uncrawled_url ("
                    + "timestamp, url, hop_path, status_code, via, seed, host) values "
                    + StringUtils.join(uncrawledBatch, ", ")
                    + ";";
            post(sql);
            uncrawledBatchLastTime = System.currentTimeMillis();
            uncrawledBatch.clear();
        }
    }
}
