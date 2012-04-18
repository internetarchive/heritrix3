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

import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.MultiReporter;
import org.apache.commons.httpclient.HttpStatus;
import org.archive.modules.deciderules.recrawl.IdenticalDigestDecideRule;
import org.apache.commons.httpclient.HttpMethod;

/**
 * Collector of statistics for a 'subset' of a crawl,
 * such as a server (host:port), host, or frontier group
 * (eg queue).
 *
 * @author gojomo
 */
public class FetchStats implements Serializable, FetchStatusCodes, MultiReporter {

    /** lower bound on HTTP status codes that we'll interpret as successes */
    private static final int HTTP_STATUS_CODE_SUCCESS_LOWER = 200;

    /** upper bound on HTTP status codes that we'll interpret as successes */
    private static final int HTTP_STATUS_CODE_SUCCESS_UPPER = 299;

    private static final long serialVersionUID = 8624425657056569036L;

    public enum Stage {SCHEDULED, RELOCATED, RETRIED,
        SUCCEEDED, DISREGARDED, FAILED};

    public interface HasFetchStats {
        public FetchStats getSubstats();
    }

    public interface CollectsFetchStats {
        public void tally(CrawlURI curi, Stage stage);
    }

    /**
     * anyting initially scheduled, (totalScheduled - (fetchSuccesses
     * + fetchFailures)
     */
    long totalScheduled;

    /**
     * anything disposed-success, any non-error HTTP status coed
     */
    long fetchSuccesses;

    /**
     * anything with an HTTP status code between 200 and 299
     */
    long fetchHttpSuccesses;

    /**
     * anything disposed-failure
     */
    long fetchFailures;

    /**
     * anything disposed-disregard
     */
    long fetchDisregards;

    /**
     * all positive responses (incl. 3XX, 4XX, 5XX)
     */
    long fetchResponses;

    /**
     * all robots-precluded failures
     */
    long robotsDenials;

    /**
     * total size of all success responses
     */
    long successBytes;

    /**
     * total size of all responses
     */
    long totalBytes;

    /**
     * processing attempts resulting in no response (both failures and
     * temp deferrals)
     */
    long fetchNonResponses;

    long novelBytes;
    long novelUrls;
    long notModifiedBytes;
    long notModifiedUrls;
    long dupByHashBytes;
    long dupByHashUrls;

    long lastSuccessTime;

    public synchronized void tally(CrawlURI curi, Stage stage) {
        switch(stage) {
            case SCHEDULED:
                totalScheduled++;
                break;
            case RETRIED:
                if(curi.getFetchStatus()<=0) {
                    fetchNonResponses++;
                }
                break;
            case SUCCEEDED:
                fetchSuccesses++;
                fetchResponses++;
                totalBytes += curi.getContentSize();
                successBytes += curi.getContentSize();

                if (curi.getFetchStatus() == HttpStatus.SC_NOT_MODIFIED) {
                    notModifiedBytes += curi.getContentSize();
                    notModifiedUrls++;
                } else if (IdenticalDigestDecideRule.hasIdenticalDigest(curi)){
                    dupByHashBytes += curi.getContentSize();
                    dupByHashUrls++;
                } else {
                    novelBytes += curi.getContentSize();
                    novelUrls++;
                }

                lastSuccessTime = curi.getFetchCompletedTime();

                HttpMethod httpMethod = curi.getHttpMethod();

                if(httpMethod != null) {

                    int statusCode = httpMethod.getStatusCode();

                    if(statusCode >= HTTP_STATUS_CODE_SUCCESS_LOWER &&
                       statusCode <= HTTP_STATUS_CODE_SUCCESS_UPPER) {

                        fetchHttpSuccesses++;
                    }
                }

                break;
            case DISREGARDED:
                fetchDisregards++;
                if(curi.getFetchStatus()==S_ROBOTS_PRECLUDED) {
                    robotsDenials++;
                }
                break;
            case FAILED:
                if(curi.getFetchStatus()<=0) {
                    fetchNonResponses++;
                } else {
                    fetchResponses++;
                    totalBytes += curi.getContentSize();

                    if (curi.getFetchStatus() == HttpStatus.SC_NOT_MODIFIED) {
                        notModifiedBytes += curi.getContentSize();
                        notModifiedUrls++;
                    } else if (IdenticalDigestDecideRule.
                            hasIdenticalDigest(curi)) {
                        dupByHashBytes += curi.getContentSize();
                        dupByHashUrls++;
                    } else {
                        novelBytes += curi.getContentSize();
                        novelUrls++;
                    }

                }
                fetchFailures++;
                break;
        }
    }

    public long getFetchSuccesses() {
        return fetchSuccesses;
    }
    public long getFetchHttpSuccesses() {
        return fetchHttpSuccesses;
    }
    public long getFetchResponses() {
        return fetchResponses;
    }
    public long getSuccessBytes() {
        return successBytes;
    }
    public long getTotalBytes() {
        return totalBytes;
    }
    public long getFetchNonResponses() {
        return fetchNonResponses;
    }
    public long getTotalScheduled() {
        return totalScheduled;
    }
    public long getFetchDisregards() {
        return fetchDisregards;
    }
    public long getRobotsDenials() {
        return robotsDenials;
    }

    public long getRemaining() {
        return totalScheduled - (fetchSuccesses + fetchFailures + fetchDisregards);
    }
    public long getRecordedFinishes() {
        return fetchSuccesses + fetchFailures;
    }

    public long getNovelBytes() {
        return novelBytes;
    }

    public long getNovelUrls() {
        return novelUrls;
    }

    public long getNotModifiedBytes() {
        return notModifiedBytes;
    }

    public long getNotModifiedUrls() {
        return notModifiedUrls;
    }

    public long getDupByHashBytes() {
        return dupByHashBytes;
    }

    public long getDupByHashUrls() {
        return dupByHashUrls;
    }

    public String[] getReports() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.lang.String, java.io.PrintWriter)
     */
    public void reportTo(String name, PrintWriter writer) {
        // name ignored, only one report
        writer.println(shortReportLegend());
        shortReportLineTo(writer);
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.io.PrintWriter)
     */
    public void reportTo(PrintWriter writer) {
        reportTo(null,writer);
    }

    public String shortReportLegend() {
        return "totalScheduled fetchSuccesses fetchHttpSuccesses " +
            "fetchFailures fetchDisregards fetchResponses " +
            "robotsDenials successBytes totalBytes fetchNonResponses " +
            "lastSuccessTime";
    }

    public String shortReportLine() {
        return ArchiveUtils.shortReportLine(this);
    }

    public void shortReportLineTo(PrintWriter writer) {
        writer.print(totalScheduled);
        writer.print(" ");
        writer.print(fetchSuccesses);
        writer.print(" ");
        writer.print(fetchHttpSuccesses);
        writer.print(" ");
        writer.print(fetchFailures);
        writer.print(" ");
        writer.print(fetchDisregards);
        writer.print(" ");
        writer.print(fetchResponses);
        writer.print(" ");
        writer.print(robotsDenials);
        writer.print(" ");
        writer.print(successBytes);
        writer.print(" ");
        writer.print(totalBytes);
        writer.print(" ");
        writer.print(fetchNonResponses);
        writer.print(" ");
        writer.print(ArchiveUtils.getLog17Date(lastSuccessTime));
    }

    public Map<String, Object> shortReportMap() {
        Map<String,Object> map = new LinkedHashMap<String, Object>();
        map.put("totalScheduled", totalScheduled);
        map.put("fetchSuccesses", fetchSuccesses);
        map.put("fetchHttpSuccesses", fetchHttpSuccesses);
        map.put("fetchFailures", fetchFailures);
        map.put("fetchDisregards", fetchDisregards);
        map.put("fetchResponses", fetchResponses);
        map.put("robotsDenials", robotsDenials);
        map.put("successBytes", successBytes);
        map.put("totalBytes", totalBytes);
        map.put("fetchNonResponses", fetchNonResponses);
        map.put("lastSuccessTime",lastSuccessTime);
        return map;
    }

    public long getLastSuccessTime() {
        return lastSuccessTime;
    }
}
