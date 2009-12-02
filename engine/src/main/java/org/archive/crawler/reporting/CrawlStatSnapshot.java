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
package org.archive.crawler.reporting;

import org.archive.crawler.framework.CrawlController;
import org.archive.util.ArchiveUtils;
import org.archive.util.PaddingStringBuffer;

/**
 * Frozen snapshot of a variety of crawl statistics. Used for 
 * obtaining a consistent set of stats and a short log of stats
 * for calculating rates.
 * 
 * @contributor gojomo
 */
public class CrawlStatSnapshot {
    public long timestamp; 
    
    public long urisFetched;
    public long bytesProcessed;
    
    public long discoveredUriCount;
    public long queuedUriCount;
    public long futureUriCount; 
    public long finishedUriCount;
    public long downloadedUriCount;
    public long downloadFailures;
    public long downloadDisregards;
    
    public long elapsedMilliseconds; 
    
    public double docsPerSecond;
    public double currentDocsPerSecond;
    
    public long totalKiBPerSec;
    public long currentKiBPerSec;
    
    public int busyThreads;
    
    public float congestionRatio; 
    public long deepestUri;
    public long averageDepth;
    
    /**
     * Collect all relevant snapshot samples, from the given CrawlController
     * and StatisticsTracker (which also provides the previous snapshot 
     * for rate-calculations.
     * 
     * @param controller
     * @param stats
     */
    public void collect(CrawlController controller, StatisticsTracker stats) {
        // TODO: reconsider names of these methods, inline?    
        downloadedUriCount = controller.getFrontier().succeededFetchCount();
        bytesProcessed = stats.crawledBytes.getTotalBytes();
        timestamp = System.currentTimeMillis();
        
        elapsedMilliseconds = stats.getCrawlElapsedTime();
        discoveredUriCount = controller.getFrontier().discoveredUriCount();
        finishedUriCount = controller.getFrontier().finishedUriCount();
        queuedUriCount = controller.getFrontier().queuedUriCount();
        futureUriCount = controller.getFrontier().futureUriCount(); 
        downloadFailures = controller.getFrontier().failedFetchCount();
        downloadDisregards = controller.getFrontier().disregardedUriCount();
        
        busyThreads = controller.getActiveToeCount();
        
        congestionRatio = controller.getFrontier().congestionRatio();
        deepestUri = controller.getFrontier().deepestUri();
        averageDepth = controller.getFrontier().averageDepth();
        
        // overall rates
        docsPerSecond = (double) downloadedUriCount /
            (stats.getCrawlElapsedTime() / 1000d);
        totalKiBPerSec = (long)((bytesProcessed / 1024d) /
            ((stats.getCrawlElapsedTime()+1) / 1000d));
        
        CrawlStatSnapshot lastSnapshot = stats.snapshots.peek();

        if(lastSnapshot==null) {
            // no previous snapshot; unable to calculate current rates
            return;
        }

        // last sample period rates
        long sampleTime = timestamp - lastSnapshot.timestamp;
        currentDocsPerSecond =
            (double) (downloadedUriCount - lastSnapshot.downloadedUriCount) 
            / (sampleTime / 1000d);
        currentKiBPerSec = 
            (long) (((bytesProcessed-lastSnapshot.bytesProcessed)/1024)
            / (sampleTime / 1000d));
    }
    
    /**
     * Return one line of current progress-statistics
     * 
     * @param now
     * @return String of stats
     */
    public String getProgressStatisticsLine() {
        return new PaddingStringBuffer()
            .append(ArchiveUtils.getLog14Date(timestamp))
            .raAppend(32, discoveredUriCount)
            .raAppend(44, queuedUriCount)
            .raAppend(57, downloadedUriCount)
            .raAppend(74, ArchiveUtils.
                doubleToString(currentDocsPerSecond, 2) +
                "(" + ArchiveUtils.doubleToString(docsPerSecond, 2) + ")")
            .raAppend(85, currentKiBPerSec + "(" + totalKiBPerSec + ")")
            .raAppend(99, downloadFailures)
            .raAppend(113, busyThreads)
            .raAppend(126, (Runtime.getRuntime().totalMemory() -
                Runtime.getRuntime().freeMemory()) / 1024)
            .raAppend(140, Runtime.getRuntime().totalMemory() / 1024)
            .raAppend(153, ArchiveUtils.doubleToString(congestionRatio, 2))
            .raAppend(165, deepestUri)
            .raAppend(177, averageDepth)
            .toString();
    }
    
    public long totalCount() {
        return queuedUriCount + busyThreads +
            downloadedUriCount;
    }
    
    /**
     * This returns the number of completed URIs as a percentage of the total
     * number of URIs encountered (should be inverse to the discovery curve)
     *
     * @return The number of completed URIs as a percentage of the total
     * number of URIs encountered
     */
    public int percentOfDiscoveredUrisCompleted() {
        long total = discoveredUriCount;
        if (total == 0) {
            return 0;
        }
        return (int) (100 * finishedUriCount / total);
    }

    /**
     * Return true if this snapshot shows no tangible progress in 
     * its URI counts over the supplied snapshot. May be used to 
     * suppress unnecessary redundant reporting/checkpointing. 
     * @param lastSnapshot
     * @return true if this snapshot stats are essentially same as previous given
     */
    public boolean sameProgressAs(CrawlStatSnapshot lastSnapshot) {
        if(lastSnapshot==null) {
            return false;
        }
        return (finishedUriCount == lastSnapshot.finishedUriCount)
            && (queuedUriCount == lastSnapshot.queuedUriCount)
            && (downloadDisregards == lastSnapshot.downloadDisregards);
    }
}
