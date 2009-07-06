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
import java.util.Iterator;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.collections.Closure;
import org.archive.modules.net.CrawlHost;

/**
 * The "Hosts Report", tallies by host.
 * 
 * @contributor gojomo
 */
public class HostsReport extends Report {

    @Override
    public void write(final PrintWriter writer) {
        // TODO: use CrawlHosts for all stats; only perform sorting on 
        // manageable number of hosts
        SortedMap<String,AtomicLong> hd = stats.getReverseSortedHostsDistribution();
        // header
        writer.print("[#urls] [#bytes] [host] [#robots] [#remaining]\n");
        for (Iterator<String> i = hd.keySet().iterator(); i.hasNext();) {
            // Key is 'host'.
            String key = (String) i.next();
            CrawlHost host = stats.serverCache.getHostFor(key);
            AtomicLong val = (AtomicLong)hd.get(key);
            writeReportLine(writer,
                    ((val==null)?"-":val.get()),
                    stats.getBytesPerHost(key),
                    key,
                    host.getSubstats().getRobotsDenials(),
                    host.getSubstats().getRemaining());
        }
        // StatisticsTracker doesn't know of zero-completion hosts; 
        // so supplement report with those entries from host cache
        Closure logZeros = new Closure() {
            public void execute(Object obj) {
                CrawlHost host = (CrawlHost)obj;
                if(host.getSubstats().getRecordedFinishes()==0) {
                    writeReportLine(writer,
                            host.getSubstats().getRecordedFinishes(),
                            host.getSubstats().getTotalBytes(),
                            host.getHostName(),
                            host.getSubstats().getRobotsDenials(),
                            host.getSubstats().getRemaining());
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
