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
    public void write(PrintWriter writer) {
        CrawlStatSnapshot snapshot = stats.getLastSnapshot(); 
        writer.print("Crawl Name: " + stats.getCrawlController().getMetadata().getJobName());
        writer.print("\nCrawl Status: " + stats.getCrawlController().getCrawlExitStatus().desc);
        writer.print("\nDuration Time: " +
                ArchiveUtils.formatMillisecondsToConventional(stats.getCrawlElapsedTime()));
        stats.tallySeeds();
        writer.print("\nTotal Seeds Crawled: " + stats.seedsCrawled);
        writer.print("\nTotal Seeds not Crawled: " + (stats.seedsTotal - stats.seedsCrawled));
        // hostsDistribution contains all hosts crawled plus an entry for dns.
        writer.print("\nTotal Hosts Crawled: " + (stats.hostsDistribution.size()-1));
        writer.print("\nTotal URIs Processed: " + snapshot.finishedUriCount);
        writer.print("\nURIs Crawled successfully: " + snapshot.downloadedUriCount);
        writer.print("\nURIs Failed to Crawl: " + snapshot.downloadFailures);
        writer.print("\nURIs Disregarded: " + snapshot.downloadDisregards);
        writer.print("\nProcessed docs/sec: " +
                ArchiveUtils.doubleToString(snapshot.docsPerSecond,2));
        writer.print("\nBandwidth in Kbytes/sec: " + snapshot.totalKiBPerSec);
        writer.print("\nTotal Raw Data Size in Bytes: " + snapshot.bytesProcessed +
                " (" + ArchiveUtils.formatBytesForDisplay(snapshot.bytesProcessed) +
                ") \n");
        writer.print("Novel Bytes: " 
                + stats.crawledBytes.get(CrawledBytesHistotable.NOVEL)
                + " (" + ArchiveUtils.formatBytesForDisplay(
                        stats.crawledBytes.get(CrawledBytesHistotable.NOVEL))
                +  ") \n");
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.DUPLICATE)) {
            writer.print("Duplicate-by-hash Bytes: " 
                    + stats.crawledBytes.get(CrawledBytesHistotable.DUPLICATE)
                    + " (" + ArchiveUtils.formatBytesForDisplay(
                            stats.crawledBytes.get(CrawledBytesHistotable.DUPLICATE))
                    +  ") \n");
        }
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.NOTMODIFIED)) {
            writer.print("Not-modified Bytes: " 
                    + stats.crawledBytes.get(CrawledBytesHistotable.NOTMODIFIED)
                    + " (" + ArchiveUtils.formatBytesForDisplay(
                            stats.crawledBytes.get(CrawledBytesHistotable.NOTMODIFIED))
                    +  ") \n");
        }     
    }

    @Override
    public String getFilename() {
        return "crawl-report.txt";
    }

}
