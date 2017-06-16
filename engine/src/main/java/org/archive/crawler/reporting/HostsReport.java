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
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.bdb.DisposableStoredSortedMap;
import org.archive.modules.net.CrawlHost;

/**
 * The "Hosts Report", tallies by host.
 * 
 * @author gojomo
 */
public class HostsReport extends Report {
    
    private final static Logger logger =
            Logger.getLogger(HostsReport.class.getName());
    
    int maxSortSize = -1;
    public int getMaxSortSize() {
    	return maxSortSize;
    }
    /**
     * The maximum number of hosts allowed in a report while still sorting it. If the number of hosts exceeds
     * this value, the generated report will not be sorted. A negative signifies no limit (always sort). 
     * A value of zero means never sort. Default -1, always sort. This matches the behavior before this 
     * parameter was introduced.
     * 
     * This value can not be overridden by a sheet. It may be safely edited at runtime.
     * 
     * @param maxSortSize
     */
    public void setMaxSortSize(int maxSortSize) {
    	this.maxSortSize = maxSortSize;
    }
    
    boolean suppressEmptyHosts = false;
    public boolean isSuppressEmptyHosts() {
		return suppressEmptyHosts;
	}
    /**
     * If true, hosts for whom no URLs have been fetched will be suppressed in this report.
     * Such hosts are recorded when the crawler encounters an URL for a host but has not yet (and may never) 
     * processed any URL for the host. This can happen for many reason's, related to scoping and queue budgeting
     * among others.
     * Default behavior is to include these non-crawled hosts.
     * 
     * This value can not be overridden by a sheet. It may be safely edited at runtime.
     *  
     * @param suppressEmptyHosts 
     */
	public void setSuppressEmptyHosts(boolean suppressEmptyHosts) {
		this.suppressEmptyHosts = suppressEmptyHosts;
	}
	
	@Override
    public void write(final PrintWriter writer, StatisticsTracker stats) {
    	Collection<String> keys = null;
    	DisposableStoredSortedMap<Long, String> hd = null;
    	if (maxSortSize<0 || maxSortSize>stats.serverCache.hostKeys().size()) {
    		hd = stats.calcReverseSortedHostsDistribution();
        	keys = hd.values();
        } else {
        	keys = stats.serverCache.hostKeys();
        }
        writer.print("[#urls] [#bytes] [host] [#robots] [#remaining] [#novel-urls] [#novel-bytes] [#dup-by-hash-urls] [#dup-by-hash-bytes] [#not-modified-urls] [#not-modified-bytes]\n"); 
        for (String key : keys) {
            // key is -count, value is hostname
            try {
                CrawlHost host = stats.serverCache.getHostFor(key);
                long fetchSuccesses = host.getSubstats().getFetchSuccesses();
                if (!suppressEmptyHosts || fetchSuccesses>0) {
	                writeReportLine(writer,
	                        fetchSuccesses,
	                        host.getSubstats().getTotalBytes(),
	                        host.fixUpName(),
	                        host.getSubstats().getRobotsDenials(),
	                        host.getSubstats().getRemaining(), 
	                        host.getSubstats().getNovelUrls(),
	                        host.getSubstats().getNovelBytes(),
	                        host.getSubstats().getDupByHashUrls(),
	                        host.getSubstats().getDupByHashBytes(),
	                        host.getSubstats().getNotModifiedUrls(),
	                        host.getSubstats().getNotModifiedBytes());
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "unable to tally host stats for " + key, e);
            }
        }
        if (hd!=null) {
        	hd.dispose();
        }
    }

    protected void writeReportLine(PrintWriter writer, Object  ... fields) {
        for(Object field : fields) {
            writer.print(field);
            writer.print(" ");
        }
        writer.print("\n");
     }

    @Override
    public String getFilename() {
        return "hosts-report.txt";
    }
}
