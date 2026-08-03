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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.archive.bdb.DisposableStoredSortedMap;

/**
 * The "Source Report", tallies of source tags (usually seeds) by host.
 * 
 * @author gojomo
 */
public class SourceTagsReport extends Report {

    private boolean includeResCode = false;

    public boolean isIncludeResCode() {
        return includeResCode;
    }

    public void setIncludeResCode(boolean includeResCode) {
        this.includeResCode = includeResCode;
    }

    @Override
    public void write(PrintWriter writer, StatisticsTracker stats) {

        Set<String> sourceTags = stats.sourceHostDistribution.keySet();
        
        if(sourceTags.isEmpty()) {
            writer.println("No source tag information. (Is 'sourceTagSeeds' enabled?)");
            return; 
        }

        if (isIncludeResCode()) {
            writer.print("[source] [host] [rescode] [#urls]\n");
            // for each source
            for (String sourceKey : sourceTags) {
                Map<String, ConcurrentMap<String, AtomicLong>> hostCounts = stats.sourceHostStatusDistribution.get(sourceKey);
                if (hostCounts != null) {
                    // For each sorted host
                    for (Map.Entry<String, ConcurrentMap<String, AtomicLong>> entry : hostCounts.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                        Map<String, AtomicLong> map = entry.getValue();
                        // Sort status code by #urls
                        DisposableStoredSortedMap<Long,String> sortedHostCounts = stats.getReverseSortedHostCounts(map);
                        for (Map.Entry<Long, String> entry2 : sortedHostCounts.entrySet()) {
                            writer.print(sourceKey.toString());
                            writer.print(" ");
                            writer.print(entry.getKey());
                            writer.print(" ");
                            writer.print(entry2.getValue());
                            writer.print(" ");
                            writer.print(Math.abs(entry2.getKey()));
                            writer.print("\n");
                        }
                    }
                }
            }
        } else {
            writer.print("[source] [host] [#urls]\n");
            // for each source
            for (String sourceKey : sourceTags) {
                Map<String, AtomicLong> hostCounts = (Map<String, AtomicLong>) stats.sourceHostDistribution.get(sourceKey);
                // sort hosts by #urls
                DisposableStoredSortedMap<Long, String> sortedHostCounts = stats.getReverseSortedHostCounts(hostCounts);
                // for each host
                for (Map.Entry<Long, String> entry : sortedHostCounts.entrySet()) {
                    writer.print(sourceKey.toString());
                    writer.print(" ");
                    writer.print(entry.getValue());
                    writer.print(" ");
                    writer.print(Math.abs(entry.getKey()));
                    writer.print("\n");
                }
                sortedHostCounts.dispose();
            }
        }
    }

    @Override
    public String getFilename() {
        return "source-report.txt";
    }
}
