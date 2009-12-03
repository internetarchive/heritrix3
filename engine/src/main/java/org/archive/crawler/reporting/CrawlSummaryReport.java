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
        writer.print("crawl name: " + stats.getCrawlController().getMetadata().getJobName());
        writer.print("\ncrawl status: " + stats.getCrawlController().getCrawlExitStatus().desc);
        writer.print("\nduration: " +
                ArchiveUtils.formatMillisecondsToConventional(stats.getCrawlElapsedTime()));
        writer.println();
        
        // seeds summary
        stats.tallySeeds();
        writer.print("\nseeds crawled: " + stats.seedsCrawled);
        writer.print("\nseeds uncrawled: " + (stats.seedsTotal - stats.seedsCrawled));
        writer.println();

        // hostsDistribution contains all hosts crawled plus an entry for dns.
        writer.print("\nhosts visited: " + (stats.hostsDistribution.size()-1));
        writer.println();

        // URI totals
        writer.print("\nURIs processed: " + snapshot.finishedUriCount);
        writer.print("\nURI successes: " + snapshot.downloadedUriCount);
        writer.print("\nURI failures: " + snapshot.downloadFailures);
        writer.print("\nURI disregards: " + snapshot.downloadDisregards);
        writer.println();
        
        // novel/duplicate/not-modified URI counts
        writer.print("\nnovel URIs: " + stats.crawledBytes.get(
                CrawledBytesHistotable.NOVELCOUNT));
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.
                DUPLICATECOUNT)) {
            writer.print("\nduplicate-by-hash URIs: " + 
                    stats.crawledBytes.get(CrawledBytesHistotable.
                            DUPLICATECOUNT));
        }
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.
                NOTMODIFIEDCOUNT)) {
            writer.print("\nnot-modified URIs: " +
                    stats.crawledBytes.get(CrawledBytesHistotable.
                            NOTMODIFIEDCOUNT)); 
        }
        writer.println();
        
        // total bytes 'crawled' (which includes the size of 
        // refetched-but-unwritten-duplicates and reconsidered-but-not-modified
        writer.print("\ntotal crawled bytes: " + snapshot.bytesProcessed +
                " (" + ArchiveUtils.formatBytesForDisplay(snapshot.bytesProcessed) +
                ") \n");
        // novel/duplicate/not-modified byte counts
        writer.print("\nnovel crawled bytes: " 
                + stats.crawledBytes.get(CrawledBytesHistotable.NOVEL)
                + " (" + ArchiveUtils.formatBytesForDisplay(
                        stats.crawledBytes.get(CrawledBytesHistotable.NOVEL))
                +  ")");
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.DUPLICATE)) {
            writer.print("\nduplicate-by-hash crawled bytes: " 
                    + stats.crawledBytes.get(CrawledBytesHistotable.DUPLICATE)
                    + " (" + ArchiveUtils.formatBytesForDisplay(
                            stats.crawledBytes.get(CrawledBytesHistotable.DUPLICATE))
                    +  ") \n");
        }
        if(stats.crawledBytes.containsKey(CrawledBytesHistotable.NOTMODIFIED)) {
            writer.print("\nnot-modified crawled bytes: " 
                    + stats.crawledBytes.get(CrawledBytesHistotable.NOTMODIFIED)
                    + " (" + ArchiveUtils.formatBytesForDisplay(
                            stats.crawledBytes.get(CrawledBytesHistotable.NOTMODIFIED))
                    +  ") \n");
        }
        writer.println();
        
        // rates
        writer.print("\nURIs/sec: " +
                ArchiveUtils.doubleToString(snapshot.docsPerSecond,2));
        writer.print("\nKB/sec: " + snapshot.totalKiBPerSec);
    }

    @Override
    public String getFilename() {
        return "crawl-report.txt";
    }

}
