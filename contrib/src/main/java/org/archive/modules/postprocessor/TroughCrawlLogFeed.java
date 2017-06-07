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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
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
 *         host VARCHAR(255));
 *         
 * CREATE TABLE queued_url(
 *         id INTEGER PRIMARY KEY AUTOINCREMENT,
 *         timestamp DATETIME,
 *         url VARCHAR(4000),
 *         hop_path VARCHAR(255),
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

    protected List<String> batch = new ArrayList<String>();
    protected long batchLastTime = System.currentTimeMillis();

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
        if (!batch.isEmpty()) {
            postBatch();
        }

        if (frontier instanceof BdbFrontier) {
            Closure closure = new Closure() {
                public void execute(Object o) {
                    CrawlURI curi = (CrawlURI) o;
                    batch.add("("
                            + sqlValue(new Date()) + ", "
                            + sqlValue(curi) + ", "
                            + sqlValue(curi.getPathFromSeed()) + ", "
                            + sqlValue(curi.getVia()) + ", "
                            + sqlValue(curi.getSourceTag()) + ", "
                            + sqlValue(serverCache.getHostFor(curi.getUURI()).getHostName()) + ")");

                    if (batch.size() >= BATCH_MAX_SIZE) {
                        String sql = "insert into queued_url (timestamp, url, hop_path, via, seed, host) values "
                                + String.join(", ", batch) + ";";
                        post(sql);
                        batch.clear();
                    }
                }
            };

            logger.info("dumping " + frontier.queuedUriCount() + " queued urls to trough feed");
            ((BdbFrontier) frontier).forAllPendingDo(closure);
            if (!batch.isEmpty()) {
                String sql = "insert into queued_url (timestamp, url, hop_path, via, seed, host) values "
                        + String.join(", ", batch) + ";";
                post(sql);
                batch.clear();
            }
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
            return "'" + o + "'";
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
        logger.info("posting to " + writeUrl + " - " + statement);
        HttpPost httpPost = new HttpPost(writeUrl);
        try {
            httpPost.setEntity(new StringEntity(statement));
            HttpResponse response = httpClient().execute(httpPost);
            EntityUtils.consume(response.getEntity());
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new IOException("expected 200 OK but got " + response.getStatusLine());
            }
        } catch (Exception e) {
            logger.warning("problem posting " + statement + " to " + writeUrl + " - " + e);
        } finally {
            httpPost.releaseConnection();
        }

    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        batch.add("(" + sqlValue(new Date(curi.getFetchBeginTime())) + ", "
                + sqlValue(curi.getFetchStatus()) + ", "
                + sqlValue(curi.getContentSize()) + ", "
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
                + sqlValue(serverCache.getHostFor(curi.getUURI()).getHostName()) + ")");

        if (batch.size() >= BATCH_MAX_SIZE || System.currentTimeMillis() - batchLastTime > BATCH_MAX_TIME_MS) {
            postBatch();
        }
    }

    protected void postBatch() {
        String sql = "insert into crawled_url ("
                + "timestamp, status_code, size, url, hop_path, is_seed_redirect, "
                + "via, mimetype, content_digest, seed, is_duplicate, warc_filename, "
                + "warc_offset, host)  values "
                + String.join(", ", batch)
                + ";";
        post(sql);
        batchLastTime = System.currentTimeMillis();
        batch.clear();
    }

}
