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
package org.archive.modules.fetcher;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.archive.crawler.util.CrawledBytesHistotable;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.ReportUtils;
import org.archive.util.Reporter;

/**
 * Collector of statistics for a 'subset' of a crawl,
 * such as a server (host:port), host, or frontier group
 * (eg queue).
 *
 * @author gojomo
 */
public class FetchStats extends CrawledBytesHistotable implements Serializable, FetchStatusCodes, Reporter {
    private static final long serialVersionUID = 2l;

    public enum Stage {SCHEDULED, RELOCATED, RETRIED, SUCCEEDED, DISREGARDED, FAILED};

    public static final String TOTAL_SCHEDULED = "totalScheduled";  // anything initially scheduled
                                                                    // (totalScheduled - (fetchSuccesses + fetchFailures)
    public static final String FETCH_SUCCESSES = "fetchSuccesses";  // anything disposed-success
                                                                    // (HTTP 2XX response codes, other non-errors)
    public static final String FETCH_FAILURES = "fetchFailures";    // anything disposed-failure
    public static final String FETCH_DISREGARDS = "fetchDisregards";// anything disposed-disregard
    public static final String FETCH_RESPONSES = "fetchResponses";  // all positive responses (incl. 3XX, 4XX, 5XX)
    public static final String ROBOTS_DENIALS = "robotsDenials";    // all robots-precluded failures
    public static final String SUCCESS_BYTES = "successBytes";      // total size of all success responses
    public static final String TOTAL_BYTES = "totalBytes";          // total size of all responses
    public static final String FETCH_NONRESPONSES = "fetchNonResponses"; // processing attempts resulting in no response
                                                                    // (both failures and temp deferrals)

    public interface HasFetchStats {
        public FetchStats getSubstats();
    }
    public interface CollectsFetchStats {
        public void tally(CrawlURI curi, Stage stage);
    }

    protected long lastSuccessTime;

    public synchronized void tally(CrawlURI curi, Stage stage) {
        switch(stage) {
            case SCHEDULED:
                tally(TOTAL_SCHEDULED, 1);
                break;
            case RETRIED:
                if(curi.getFetchStatus()<=0) {
                    tally(FETCH_NONRESPONSES, 1);
                }
                break;
            case SUCCEEDED:
                tally(FETCH_SUCCESSES, 1);
                tally(FETCH_RESPONSES, 1);
                tally(TOTAL_BYTES, curi.getContentSize());
                tally(SUCCESS_BYTES, curi.getContentSize());

                lastSuccessTime = curi.getFetchCompletedTime();
                break;
            case DISREGARDED:
                tally(FETCH_DISREGARDS, 1);
                if(curi.getFetchStatus()==S_ROBOTS_PRECLUDED) {
                    tally(ROBOTS_DENIALS, 1);
                }
                break;
            case FAILED:
                if(curi.getFetchStatus()<=0) {
                    tally(FETCH_NONRESPONSES, 1);
                } else {
                    tally(FETCH_RESPONSES, 1);
                    tally(TOTAL_BYTES, curi.getContentSize());
                }
                tally(FETCH_FAILURES, 1);
                break;
            default:
                break;
        }

        if (curi.getFetchStatus() > 0) {
            this.accumulate(curi);
        }
    }

    public long getFetchSuccesses() {
        return get(FETCH_SUCCESSES);
    }
    public long getFetchResponses() {
        return get(FETCH_RESPONSES);
    }
    public long getSuccessBytes() {
        return get(SUCCESS_BYTES);
    }
    public long getTotalBytes() {
        return get(TOTAL_BYTES);
    }
    public long getFetchNonResponses() {
        return get(FETCH_NONRESPONSES);
    }
    public long getTotalScheduled() {
        return get(TOTAL_SCHEDULED);
    }
    public long getFetchDisregards() {
        return get(FETCH_DISREGARDS);
    }
    public long getRobotsDenials() {
        return get(ROBOTS_DENIALS);
    }

    public long getRemaining() {
        return get(TOTAL_SCHEDULED) - (get(FETCH_SUCCESSES) + get(FETCH_FAILURES)+ get(FETCH_DISREGARDS));
    }
    public long getRecordedFinishes() {
        return get(FETCH_SUCCESSES) + get(FETCH_FAILURES);
    }

    public long getNovelBytes() {
        return get(NOVEL);
    }

    public long getNovelUrls() {
        return get(NOVELCOUNT);
    }

    public long getNotModifiedBytes() {
        return get(NOTMODIFIED);
    }

    public long getNotModifiedUrls() {
        return get(NOTMODIFIEDCOUNT);
    }

    public long getDupByHashBytes() {
        return get(DUPLICATE);
    }

    public long getDupByHashUrls() {
        return get(DUPLICATECOUNT);
    }

    public long getOtherDupBytes() {
        return get(OTHERDUPLICATE);
    }

    public long getOtherDupUrls() {
        return get(OTHERDUPLICATECOUNT);
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.io.PrintWriter)
     */
    @Override // Reporter
    public void reportTo(PrintWriter writer) {
        writer.println(shortReportLegend());
        shortReportLineTo(writer);
    }

    @Override
    public String shortReportLegend() {
        return "totalScheduled fetchSuccesses fetchFailures fetchDisregards " +
                "fetchResponses robotsDenials successBytes totalBytes " +
                "fetchNonResponses lastSuccessTime";
    }

    public String shortReportLine() {
        return ReportUtils.shortReportLine(this);
    }

    @Override
    public void shortReportLineTo(PrintWriter writer) {
        writer.print(get(TOTAL_SCHEDULED));
        writer.print(" ");
        writer.print(get(FETCH_SUCCESSES));
        writer.print(" ");
        writer.print(get(FETCH_FAILURES));
        writer.print(" ");
        writer.print(get(FETCH_DISREGARDS));
        writer.print(" ");
        writer.print(get(FETCH_RESPONSES));
        writer.print(" ");
        writer.print(get(ROBOTS_DENIALS));
        writer.print(" ");
        writer.print(get(SUCCESS_BYTES));
        writer.print(" ");
        writer.print(get(TOTAL_BYTES));
        writer.print(" ");
        writer.print(get(FETCH_NONRESPONSES));
        writer.print(" ");
        writer.print(ArchiveUtils.getLog17Date(lastSuccessTime));
    }

    @Override
    public Map<String, Object> shortReportMap() {
        Map<String,Object> map = new LinkedHashMap<String, Object>(this);
        map.put("lastSuccessTime",lastSuccessTime);
        return map;
    }

    public long getLastSuccessTime() {
        return lastSuccessTime;
    }
}
