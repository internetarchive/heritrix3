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


import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.collections.Closure;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.frontier.AbstractFrontier;
import org.archive.crawler.frontier.BdbFrontier;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.net.ServerCache;
import org.archive.spring.KeyedProperties;
import org.archive.trough.TroughClient;
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
    protected static final int BATCH_MAX_SIZE = 400;
    protected static final String CRAWLED_BATCH = "crawled";
    protected static final String UNCRAWLED_BATCH = "uncrawled";
    protected AtomicInteger crawledBatchSize = new AtomicInteger(0);
    protected AtomicInteger uncrawledBatchSize = new AtomicInteger(0);

    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }

    public void setSegmentId(String segmentId) {
        kp.put("segmentId", segmentId);
    }
    public String getSegmentId() {
        return (String) kp.get("segmentId");
    }

    /**
     * @param rethinkUrl url with schema rethinkdb:// pointing to
     *          trough configuration database
     */
    public void setRethinkUrl(String rethinkUrl) {
        kp.put("rethinkUrl", rethinkUrl);
    }
    public String getRethinkUrl() {
        return (String) kp.get("rethinkUrl");
    }

    protected TroughClient troughClient = null;

    protected TroughClient troughClient() throws MalformedURLException {
        if (troughClient == null) {
            troughClient = new TroughClient(getRethinkUrl(), null);
            troughClient.start();
        }
        return troughClient;
    }

    protected ConcurrentLinkedQueue<Object[]> crawledBatch = new ConcurrentLinkedQueue<Object[]>();
    protected long crawledBatchLastTime = System.currentTimeMillis();
    protected ConcurrentLinkedQueue<Object[]> uncrawledBatch = new ConcurrentLinkedQueue<Object[]>();
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

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        if (frontier instanceof AbstractFrontier) {
            return !((AbstractFrontier) frontier).needsReenqueuing(curi);
        } else {
            return false;
        }
    }
    
    protected boolean dumpPendingAtClose = true;
    public boolean getDumpPendingAtClose() {
	return dumpPendingAtClose;
    }
    public void setDumpPendingAtClose(boolean dumpPendingAtClose) {
	this.dumpPendingAtClose = dumpPendingAtClose;
    }

    protected boolean forceBatchPosting = false;
    protected boolean getForceBatchPosting() {
	return forceBatchPosting;
    }
    protected void setForceBatchPosting(boolean forceBatchPosting) {
	this.forceBatchPosting = forceBatchPosting;
    }

    @Override
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        if (!crawledBatch.isEmpty()) {
            setForceBatchPosting(true);
            postBatch(crawledBatch, crawledBatchSize, CRAWLED_BATCH);
            setForceBatchPosting(false);
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

            if(getDumpPendingAtClose()) {
                logger.info("dumping " + frontier.queuedUriCount() + " queued urls to trough feed");
                setForceBatchPosting(true);
                ((BdbFrontier) frontier).forAllPendingDo(closure);
                postBatch(uncrawledBatch, uncrawledBatchSize, UNCRAWLED_BATCH);
                setForceBatchPosting(false);
                    logger.info("dumped " + frontier.queuedUriCount() + " queued urls to trough feed");
            }
        } else {
            logger.warning("frontier is not a BdbFrontier, cannot dump queued urls to trough feed");
        }

        // String rateStr = String.format("%1.1f", 0.01 * stats.errors / stats.total);
        // logger.info("final error count: " + stats.errors + "/" + stats.total + " (" + rateStr + "%)");
        super.stop();
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

            Object[] values = new Object[] {
                    new Date(curi.getFetchBeginTime()),
                    curi.getFetchStatus(),
                    curi.getContentSize(),
                    curi.getContentLength(),
                    curi,
                    curi.getPathFromSeed(),
                    (curi.isSeed() && !"".equals(curi.getPathFromSeed())) ? 1 : 0,
                    curi.getVia(),
                    MimetypeUtils.truncate(curi.getContentType()),
                    curi.getContentDigestSchemeString(),
                    curi.getSourceTag(),
                    curi.isRevisit() ? 1 : 0,
                    curi.getExtraInfo().opt("warcFilename"),
                    curi.getExtraInfo().opt("warcOffset"),
                    warcContentBytes,
                    serverCache.getHostFor(curi.getUURI()).getHostName(),
            };

            crawledBatch.add(values);

            if (crawledBatchSize.incrementAndGet() >= BATCH_MAX_SIZE || System.currentTimeMillis() - crawledBatchLastTime > BATCH_MAX_TIME_MS) {
                postBatch(crawledBatch, crawledBatchSize, CRAWLED_BATCH);
            }
        } else {
            Object[] values = new Object[] {
                    new Date(),
                    curi,
                    curi.getPathFromSeed(),
                    curi.getFetchStatus(),
                    curi.getVia(),
                    curi.getSourceTag(),
                    serverCache.getHostFor(curi.getUURI()).getHostName(),
            };

            uncrawledBatch.add(values);
            if (uncrawledBatchSize.incrementAndGet() >= BATCH_MAX_SIZE || System.currentTimeMillis() - uncrawledBatchLastTime > BATCH_MAX_TIME_MS) {
                postBatch(uncrawledBatch, uncrawledBatchSize, UNCRAWLED_BATCH);
            }
        }
    }

    protected void postBatch(ConcurrentLinkedQueue<Object[]> batch, AtomicInteger batchSize, String batchType) {
        ArrayList<Object[]> crawlLogLines = new ArrayList<Object[]>();
        synchronized (batch) {
            //read and remove log lines from batch into local variable so we can exit the synchronized block asap
            if (batchSize.get() >= BATCH_MAX_SIZE || getForceBatchPosting()) {
                while(!batch.isEmpty()) {
                    Object[] crawlLine = batch.poll();
                    if(crawlLine != null)
                        crawlLogLines.add(crawlLine);
                    else
                        break;
                }
                batchSize.getAndSet(batch.size()); //size() is O(n). Use sparingly. Should be near zero right now.
                logger.info("posting batch of " + crawlLogLines.size() + " " + batchType + " urls to trough segment " + getSegmentId());
            }
        }
        if( crawlLogLines != null && crawlLogLines.size() > 0){
            StringBuffer sqlTmpl = new StringBuffer();
            int numCols=0;
            switch(batchType){
                case CRAWLED_BATCH:
                    sqlTmpl.append("insert into crawled_url ("
                            + "timestamp, status_code, size, payload_size, url, hop_path, is_seed_redirect, "
                            + "via, mimetype, content_digest, seed, is_duplicate, warc_filename, "
                            + "warc_offset, warc_content_bytes, host)  values "
                            + "(%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)");
                    for (int i = 1; i < crawlLogLines.size(); i++) {
                        sqlTmpl.append(", (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)");
                    }
                    numCols=16;
                    break;
                case UNCRAWLED_BATCH:
                    sqlTmpl.append(
                            "insert into uncrawled_url (timestamp, url, hop_path, status_code, via, seed, host)"
                                    + " values (%s, %s, %s, %s, %s, %s, %s)");

                    for (int i = 1; i < crawlLogLines.size(); i++) {
                        sqlTmpl.append(", (%s, %s, %s, %s, %s, %s, %s)");
                    }
                    numCols=7;
                    break;
            }

            /*
            Trough takes a single object array for the values we insert, so an array of size (N columns * X rows).
            We read each row in column by column, then repeat for each row.
             */
            Object[] flattenedValues = new Object[numCols * crawlLogLines.size()];
            for (int i = 0; i < crawlLogLines.size(); i++) {
                System.arraycopy(crawlLogLines.get(i),0,flattenedValues, numCols * i,numCols);
            }

            try {
                troughClient().write(getSegmentId(), sqlTmpl.toString(), flattenedValues);
            } catch (Exception e) {
                logger.log(Level.WARNING, "problem posting batch of " + crawlLogLines.size() + " " + batchType + " urls to trough segment " + getSegmentId(), e);
            }

            switch(batchType) {
                case CRAWLED_BATCH:
                    crawledBatchLastTime = System.currentTimeMillis();
                    break;
                case UNCRAWLED_BATCH:
                    uncrawledBatchLastTime = System.currentTimeMillis();
                    break;
            }
        }
    }
}
