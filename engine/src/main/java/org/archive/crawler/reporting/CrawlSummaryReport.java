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

import java.io.PrintWriter;

import org.archive.crawler.util.CrawledBytesHistotable;
import org.archive.util.ArchiveUtils;

/**
 * The "Crawl Report", with summaries of overall crawl size.
 * 
 * @contributor gojomo
 */
public class CrawlSummaryReport extends Report {

    @Override
    public void write(PrintWriter writer, StatisticsTracker stats) {
        CrawlStatSnapshot snapshot = stats.getLastSnapshot(); 
        writer.println("crawl name: " + stats.getCrawlController().getMetadata().getJobName());
        writer.println("crawl status: " + stats.getCrawlController().getCrawlExitStatus().desc);
        writer.println("duration: " +
                ArchiveUtils.formatMillisecondsToConventional(stats.getCrawlElapsedTime()));
        writer.println();
        
        // seeds summary
        stats.tallySeeds();
        writer.println("seeds crawled: " + stats.seedsCrawled);
        writer.println("seeds uncrawled: " + (stats.seedsTotal - stats.seedsCrawled));
        writer.println();

        // hostsDistribution contains all hosts crawled plus an entry for dns.
        writer.println("hosts visited: " + (stats.serverCache.hostKeys().size()-1));
        writer.println();

        // URI totals
        writer.println("URIs processed: " + snapshot.finishedUriCount);
        writer.println("URI successes: " + snapshot.downloadedUriCount);
        writer.println("URI failures: " + snapshot.downloadFailures);
        writer.println("URI disregards: " + snapshot.downloadDisregards);
        writer.println();
        
        // novel/duplicate/not-modified URI counts
        writer.println("novel URIs: " + stats.crawledBytes.get(
                CrawledBytesHistotable.NOVELCOUNT));
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.
                DUPLICATECOUNT)) {
            writer.println("duplicate-by-hash URIs: " + 
                    stats.crawledBytes.get(CrawledBytesHistotable.
                            DUPLICATECOUNT));
        }
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.
                NOTMODIFIEDCOUNT)) {
            writer.println("not-modified URIs: " +
                    stats.crawledBytes.get(CrawledBytesHistotable.
                            NOTMODIFIEDCOUNT)); 
        }
        writer.println();
        
        // total bytes 'crawled' (which includes the size of 
        // refetched-but-unwritten-duplicates and reconsidered-but-not-modified
        writer.println("total crawled bytes: " + snapshot.bytesProcessed +
                " (" + ArchiveUtils.formatBytesForDisplay(snapshot.bytesProcessed) +
                ") ");
        // novel/duplicate/not-modified byte counts
        writer.println("novel crawled bytes: " 
                + stats.crawledBytes.get(CrawledBytesHistotable.NOVEL)
                + " (" + ArchiveUtils.formatBytesForDisplay(
                        stats.crawledBytes.get(CrawledBytesHistotable.NOVEL))
                +  ")");
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.DUPLICATE)) {
            writer.println("duplicate-by-hash crawled bytes: " 
                    + stats.crawledBytes.get(CrawledBytesHistotable.DUPLICATE)
                    + " (" + ArchiveUtils.formatBytesForDisplay(
                            stats.crawledBytes.get(CrawledBytesHistotable.DUPLICATE))
                    +  ") ");
        }
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.NOTMODIFIED)) {
            writer.println("not-modified crawled bytes: " 
                    + stats.crawledBytes.get(CrawledBytesHistotable.NOTMODIFIED)
                    + " (" + ArchiveUtils.formatBytesForDisplay(
                            stats.crawledBytes.get(CrawledBytesHistotable.NOTMODIFIED))
                    +  ") ");
        }
        writer.println();
        
        // rates
        writer.println("URIs/sec: " +
                ArchiveUtils.doubleToString(snapshot.docsPerSecond,2));
        writer.println("KB/sec: " + snapshot.totalKiBPerSec);
    }

    @Override
    public String getFilename() {
        return "crawl-report.txt";
    }

}
