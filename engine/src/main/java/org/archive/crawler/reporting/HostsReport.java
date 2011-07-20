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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import org.apache.commons.collections.Closure;
import org.archive.bdb.DisposableStoredSortedMap;
import org.archive.modules.net.CrawlHost;

/**
 * The "Hosts Report", tallies by host.
 * 
 * @contributor gojomo
 */
public class HostsReport extends Report {

    protected String fixup(String hostName) {
        if ("dns:".equals(hostName)) {
            return hostName;
        } else {
            try {
                return URLEncoder.encode(hostName, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void write(final PrintWriter writer, StatisticsTracker stats) {
        // TODO: use CrawlHosts for all stats; only perform sorting on 
        // manageable number of hosts
        DisposableStoredSortedMap<Long,String> hd = stats.calcReverseSortedHostsDistribution();
        // header
        writer.print("[#urls] [#bytes] [host] [#robots] [#remaining] [#novel-urls] [#novel-bytes] [#dup-by-hash-urls] [#dup-by-hash-bytes] [#not-modified-urls] [#not-modified-bytes]\n"); 
        for (Map.Entry<Long,String> entry : hd.entrySet()) {
            // key is -count, value is hostname
            CrawlHost host = stats.serverCache.getHostFor(entry.getValue());
            long count = Math.abs(entry.getKey()); 
            writeReportLine(writer,
                    count,
                    stats.getBytesPerHost(entry.getValue()),
                    fixup(entry.getValue()),
                    host.getSubstats().getRobotsDenials(),
                    host.getSubstats().getRemaining(), 
                    host.getSubstats().getNovelUrls(),
                    host.getSubstats().getNovelBytes(),
                    host.getSubstats().getDupByHashUrls(),
                    host.getSubstats().getDupByHashBytes(),
                    host.getSubstats().getNotModifiedUrls(),
                    host.getSubstats().getNotModifiedBytes()); 
        }
        hd.dispose();
        // StatisticsTracker doesn't know of zero-completion hosts; 
        // so supplement report with those entries from host cache
        Closure logZeros = new Closure() {
            public void execute(Object obj) {
                CrawlHost host = (CrawlHost)obj;
                if(host.getSubstats().getRecordedFinishes()==0) {
                    writeReportLine(writer,
                            host.getSubstats().getRecordedFinishes(),
                            host.getSubstats().getTotalBytes(),
                            fixup(host.getHostName()),
                            host.getSubstats().getRobotsDenials(),
                            host.getSubstats().getRemaining(),
                            host.getSubstats().getNovelUrls(),
                            host.getSubstats().getNovelBytes(),
                            host.getSubstats().getDupByHashUrls(),
                            host.getSubstats().getDupByHashBytes(),
                            host.getSubstats().getNotModifiedUrls(),
                            host.getSubstats().getNotModifiedBytes()); 
                }
            }};
        stats.serverCache.forAllHostsDo(logZeros);
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
