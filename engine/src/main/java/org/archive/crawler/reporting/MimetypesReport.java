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
import java.util.concurrent.atomic.AtomicLong;

/**
 * The "Mimetypes Report", tallies by MIME type.
 * 
 * @contributor gojomo
 */
public class MimetypesReport extends Report {

    @Override
    public void write(PrintWriter writer) {
        // header
        writer.print("[#urls] [#bytes] [mime-types]\n");
        TreeMap<String,AtomicLong> fd = stats.getReverseSortedCopy(stats.getFileDistribution());
        for (Iterator<String> i = fd.keySet().iterator(); i.hasNext();) {
            Object key = i.next();
            // Key is mime type.
            writer.print(Long.toString(((AtomicLong)fd.get(key)).get()));
            writer.print(" ");
            writer.print(Long.toString(stats.getBytesPerFileType((String)key)));
            writer.print(" ");
            writer.print((String)key);
            writer.print("\n");
        }    }

    @Override
    public String getFilename() {
        return "mimetype-report.txt";
    }

}
