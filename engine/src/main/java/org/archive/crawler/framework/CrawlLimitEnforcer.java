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
 
package org.archive.crawler.framework;

import org.archive.crawler.event.StatSnapshotEvent;
import org.archive.crawler.reporting.CrawlStatSnapshot;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * Bean to enforce limits on the size of a crawl in URI count,
 * byte count, or elapsed time. Fires off the StatSnapshotEvent,
 * so only checks at the interval (configured in StatisticsTracker)
 * of those events. 
 * 
 * @contributor gojomo
 */
public class CrawlLimitEnforcer implements ApplicationListener {

    /**
     * Maximum number of bytes to download. Once this number is exceeded 
     * the crawler will stop. A value of zero means no upper limit.
     */
    long maxBytesDownload = 0L;
    public long getMaxBytesDownload() {
        return maxBytesDownload;
    }
    public void setMaxBytesDownload(long maxBytesDownload) {
        this.maxBytesDownload = maxBytesDownload;
    }

    /**
     * Maximum number of documents to download. Once this number is exceeded the 
     * crawler will stop. A value of zero means no upper limit.
     */
    long maxDocumentsDownload = 0L; 
    public long getMaxDocumentsDownload() {
        return maxDocumentsDownload;
    }
    public void setMaxDocumentsDownload(long maxDocumentsDownload) {
        this.maxDocumentsDownload = maxDocumentsDownload;
    }

    /**
     * Maximum amount of time to crawl (in seconds). Once this much time has 
     * elapsed the crawler will stop. A value of zero means no upper limit.
     */
    long maxTimeSeconds = 0L;
    public long getMaxTimeSeconds() {
        return maxTimeSeconds;
    }
    public void setMaxTimeSeconds(long maxTimeSeconds) {
        this.maxTimeSeconds = maxTimeSeconds;
    }

    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }
    
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof StatSnapshotEvent) {
            CrawlStatSnapshot snapshot = ((StatSnapshotEvent)event).getSnapshot();
            checkForLimitsExceeded(snapshot);
        }
    }
    
    protected void checkForLimitsExceeded(CrawlStatSnapshot snapshot) {
        if (maxBytesDownload > 0 && snapshot.bytesProcessed >= maxBytesDownload) {
            controller.requestCrawlStop(CrawlStatus.FINISHED_DATA_LIMIT);
        } else if (maxDocumentsDownload > 0
                && snapshot.downloadedUriCount >= maxDocumentsDownload) {
            controller.requestCrawlStop(CrawlStatus.FINISHED_DOCUMENT_LIMIT);
        } else if (maxTimeSeconds > 0 
                && snapshot.elapsedMilliseconds >= maxTimeSeconds * 1000) {
            controller.requestCrawlStop(CrawlStatus.FINISHED_TIME_LIMIT);
        }
    }

}
