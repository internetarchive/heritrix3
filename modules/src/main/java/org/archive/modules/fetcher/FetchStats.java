/* CrawlSubstats
*
* $Id$
*
* Created on Nov 4, 2005
*
* Copyright (C) 2005 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/ 
package org.archive.modules.fetcher;

import java.io.PrintWriter;
import java.io.Serializable;

import org.archive.modules.ProcessorURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.MultiReporter;

/**
 * Collector of statististics for a 'subset' of a crawl,
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
        public void tally(ProcessorURI curi, Stage stage); 
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
    
    public synchronized void tally(ProcessorURI curi, Stage stage) {
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
