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

package org.archive.modules;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.event.AMQPUrlPublishedEvent;
import org.archive.crawler.event.AMQPUrlReceivedEvent;
import org.archive.crawler.event.StatSnapshotEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.CrawlStatus;
import org.archive.crawler.framework.Frontier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * Bean to enforce a wait for Umbra's amqp queue
 *
 * @contributor galgeek
 */
public class AMQPUrlWaiter implements ApplicationListener<ApplicationEvent> {

    public AMQPUrlWaiter() {}

    static protected final Logger logger = Logger.getLogger(AMQPUrlWaiter.class.getName());

    protected int urlsPublished = 0;
    protected int urlsReceived = 0;

    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }

    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    /** Autowired frontier, needed to determine when a url is finished. */
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof AMQPUrlPublishedEvent) {
            urlsPublished += 1;
        } else if (event instanceof AMQPUrlReceivedEvent) {
            urlsReceived += 1;
        } else if (event instanceof StatSnapshotEvent) {
            checkAMQPUrlWait();
        }
    }

    protected void checkAMQPUrlWait() {
        if (frontier.isEmpty() && (urlsPublished == 0 || urlsReceived > 0)) {
            logger.info("frontier is empty and we have received " + urlsReceived +
                        " urls from AMQP, and published " + urlsPublished +
                        ", stopping crawl with status " + CrawlStatus.FINISHED);
            controller.requestCrawlStop(CrawlStatus.FINISHED);
        }
    }
}
