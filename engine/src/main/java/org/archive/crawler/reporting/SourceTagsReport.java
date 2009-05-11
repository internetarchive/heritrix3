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
import java.util.Map;
import java.util.SortedMap;

import org.archive.util.LongWrapper;

/**
 * The "Source Report", tallies of source tags (usually seeds) by host.
 * 
 * @contributor gojomo
 */
public class SourceTagsReport extends Report {

    @Override
    public void write(PrintWriter writer) {

        writer.print("[source] [host] [#urls]\n");
        // for each source
        for (Iterator<String> i = stats.sourceHostDistribution.keySet().iterator(); i.hasNext();) {
            String sourceKey = i.next();
            Map<String,LongWrapper> hostCounts = 
                (Map<String,LongWrapper>)stats.sourceHostDistribution.get(sourceKey);
            // sort hosts by #urls
            SortedMap<String,LongWrapper> sortedHostCounts = 
                stats.getReverseSortedHostCounts(hostCounts);
            // for each host
            for (Iterator<String> j = sortedHostCounts.keySet().iterator(); j.hasNext();) {
                Object hostKey = j.next();
                LongWrapper hostCount = (LongWrapper) hostCounts.get(hostKey);
                writer.print(sourceKey.toString());
                writer.print(" ");
                writer.print(hostKey.toString());
                writer.print(" ");
                writer.print(hostCount.longValue);
                writer.print("\n");
            }
        }
    }

    @Override
    public String getFilename() {
        return "source-report.txt";
    }
}
