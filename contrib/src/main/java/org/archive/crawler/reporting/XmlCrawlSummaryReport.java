package org.archive.crawler.reporting;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.archive.crawler.restlet.XmlMarshaller;
import org.archive.modules.writer.WARCWriterProcessor;
import org.archive.util.ArchiveUtils;

public class XmlCrawlSummaryReport extends Report {

    private String scheduledDate;

    public void setScheduledDate(String scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public String getScheduledDate() {
        return this.scheduledDate;
    }

    @Override
    public void write(PrintWriter writer, StatisticsTracker stats) {
        Map<String,Object> info = new LinkedHashMap<String,Object>();

        CrawlStatSnapshot snapshot = stats.getLastSnapshot();

        info.put("crawlName", 
                ((WARCWriterProcessor) stats.appCtx.getBean("warcWriter")).getPrefix());
        info.put("crawlJobShortName", 
                stats.getCrawlController().getMetadata().getJobName());
        info.put("scheduledDate", this.scheduledDate);
        info.put("crawlStatus",
                stats.getCrawlController().getCrawlExitStatus().desc);
        info.put("duration", 
                ArchiveUtils.formatMillisecondsToConventional(stats.getCrawlElapsedTime()));

        stats.tallySeeds();
        info.put("seedsCrawled", stats.seedsCrawled);
        info.put("seedsUncrawled",stats.seedsTotal - stats.seedsCrawled);

        info.put("hostsVisited",stats.serverCache.hostKeys().size() - 1);

        info.put("urisProcessed", snapshot.finishedUriCount);
        info.put("uriSuccesses", snapshot.downloadedUriCount);
        info.put("uriFailures", snapshot.downloadFailures);
        info.put("uriDisregards", snapshot.downloadDisregards);

        info.put("novelUris", stats.crawledBytes.get("novelCount"));

        long duplicateCount = stats.crawledBytes.containsKey("dupByHashCount") ? stats.crawledBytes
                .get("dupByHashCount").longValue() : 0L;

        info.put("duplicateByHashUris", duplicateCount);
        long notModifiedCount = stats.crawledBytes
                .containsKey("notModifiedCount") ? stats.crawledBytes.get(
                "notModifiedCount").longValue() : 0L;

        info.put("notModifiedUris", notModifiedCount);

        info.put("totalCrawledBytes", snapshot.bytesProcessed);

        info.put("novelCrawledBytes", stats.crawledBytes.get("novel"));

        long duplicateByHashCrawledBytes = stats.crawledBytes
                .containsKey("dupByHash") ? stats.crawledBytes.get("dupByHash")
                .longValue() : 0L;

        info.put("duplicateByHashCrawledBytes",duplicateByHashCrawledBytes);
        long notModifiedCrawledBytes = stats.crawledBytes
                .containsKey("notModified") ? stats.crawledBytes.get(
                "notModified").longValue() : 0L;

        info.put("notModifiedCrawledBytes",notModifiedCrawledBytes);

        info.put("urisPerSec",
                ArchiveUtils.doubleToString(snapshot.docsPerSecond, 2));
        info.put("kbPerSec", snapshot.totalKiBPerSec);
        
        try {
            XmlMarshaller.marshalDocument(writer,
                    XmlCrawlSummaryReport.class.getCanonicalName(), info);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public String getFilename() {
        return "crawl-report.xml";
    }

}