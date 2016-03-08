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

import java.io.UnsupportedEncodingException;
import java.util.Map;

import org.apache.commons.collections.Closure;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.frontier.AbstractFrontier;
import org.archive.crawler.frontier.BdbFrontier;
import org.archive.crawler.io.UriProcessingFormatter;
import org.archive.modules.AMQPProducerProcessor;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.ServerCache;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;

/**
 * @see UriProcessingFormatter
 * @contributor nlevitt
 */
public class AMQPCrawlLogFeed extends AMQPProducerProcessor implements Lifecycle {

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

    protected Map<String,String> extraFields;
    public Map<String, String> getExtraFields() {
        return extraFields;
    }
    public void setExtraFields(Map<String, String> extraFields) {
        this.extraFields = extraFields;
    }

    protected boolean dumpPendingAtClose = false;
    public boolean getDumpPendingAtClose() {
        return dumpPendingAtClose;
    }
    /**
     * If true, publish all pending urls (i.e. queued urls still in the
     * frontier) when crawl job is stopping. They are recognizable by the status
     * field which has the value 0.
     *
     * @see BdbFrontier#setDumpPendingAtClose(boolean)
     */
    public void setDumpPendingAtClose(boolean dumpPendingAtClose) {
        this.dumpPendingAtClose = dumpPendingAtClose;
    }

    public AMQPCrawlLogFeed() {
        // set default values
        setExchange("heritrix.realTimeFeed");
        setRoutingKey("crawlLog");
    }

    @Override
    protected byte[] buildMessage(CrawlURI curi) {
        JSONObject jo = CrawlLogJsonBuilder.buildJson(curi, getExtraFields(), getServerCache());
        try {
            return jo.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        if (frontier instanceof AbstractFrontier) {
            return !((AbstractFrontier) frontier).needsReenqueuing(curi);
        } else {
            return false;
        }
    }

    private transient long pendingDumpedCount = 0l;
    @Override
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }

        if (dumpPendingAtClose) {
            if (frontier instanceof BdbFrontier) {

                Closure closure = new Closure() {
                    public void execute(Object curi) {
                        try {
                            innerProcessResult((CrawlURI) curi);
                            pendingDumpedCount++;
                        } catch (InterruptedException e) {
                        }
                    }
                };

                logger.info("dumping " + frontier.queuedUriCount() + " queued urls to amqp feed");
                ((BdbFrontier) frontier).forAllPendingDo(closure);
                logger.info("dumped " + pendingDumpedCount + " queued urls to amqp feed");
            } else {
                logger.warning("frontier is not a BdbFrontier, cannot dumpPendingAtClose");
            }
        }

        // closes amqp connection
        super.stop();
    }

    protected BasicProperties props = new AMQP.BasicProperties.Builder().
            contentType("application/json").build();

    @Override
    protected BasicProperties amqpMessageProperties() {
        return props;
    }
}
