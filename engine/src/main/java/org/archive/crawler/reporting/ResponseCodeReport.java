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
import java.util.TreeMap;

import org.archive.util.LongWrapper;

/**
 * The "Response Codes Report", tallies by response/disposition code.
 * 
 * @contributor gojomo
 */
public class ResponseCodeReport extends Report {

    @Override
    public void write(PrintWriter writer) {
        // header
        writer.print("[rescode] [#urls]\n");
        
        TreeMap<String,LongWrapper> scd = 
            stats.getReverseSortedCopy(stats.getStatusCodeDistribution());
        for (Iterator<String> i = scd.keySet().iterator(); i.hasNext();) {
            Object key = i.next();
            writer.print((String)key);
            writer.print(" ");
            writer.print(Long.toString(((LongWrapper)scd.get(key)).longValue));
            writer.print("\n");
        }
    }

    @Override
    public String getFilename() {
        return "responsecode-report.txt";
    }

}
