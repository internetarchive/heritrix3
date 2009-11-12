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

import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.MultiReporter;
import org.apache.commons.httpclient.HttpStatus; 
import org.archive.modules.deciderules.recrawl.IdenticalDigestDecideRule;

/**
 * Collector of statistics for a 'subset' of a crawl,
 * such as a server (host:port), host, or frontier group 
 * (eg queue). 
 * 
 * @author gojomo
 */
public class FetchStats implements Serializable, FetchStatusCodes, MultiReporter {
    private static final long serialVersionUID = 8624425657056569036L;

    public enum Stage {SCHEDULED, RELOCATED, RETRIED, 
        SUCCEEDED, DISREGARDED, FAILED};
    
    public interface HasFetchStats {
        public FetchStats getSubstats();
    }
    public interface CollectsFetchStats {
        public void tally(CrawlURI curi, Stage stage); 
    }
    
    long totalScheduled;   // anything initially scheduled
                           // (totalScheduled - (fetchSuccesses + fetchFailures)
    long fetchSuccesses;   // anything disposed-success 
                           // (HTTP 2XX response codes, other non-errors)
    long fetchFailures;    // anything disposed-failure
    long fetchDisregards;  // anything disposed-disregard
    long fetchResponses;   // all positive responses (incl. 3XX, 4XX, 5XX)
    long robotsDenials;    // all robots-precluded failures
    long successBytes;     // total size of all success responses
    long totalBytes;       // total size of all responses
    long fetchNonResponses; // processing attempts resulting in no response
                           // (both failures and temp deferrals)
    
    long novelBytes; 
    long novelUrls;
    long notModifiedBytes;
    long notModifiedUrls;
    long dupByHashBytes;
    long dupByHashUrls;  
    
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
        writer.println(singleLineLegend());
        singleLineReportTo(writer);
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.io.PrintWriter)
     */
    public void reportTo(PrintWriter writer) {
        reportTo(null,writer);
    }

    public String singleLineLegend() {
        return "totalScheduled fetchSuccesses fetchFailures fetchDisregards " +
                "fetchResponses robotsDenials successBytes totalBytes " +
                "fetchNonResponses";
    }

    public String singleLineReport() {
        return ArchiveUtils.singleLineReport(this);
    }

    public void singleLineReportTo(PrintWriter writer) {
        writer.print(totalScheduled);
        writer.print(" ");
        writer.print(fetchSuccesses);
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
    }
}
