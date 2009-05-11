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


/**
 * The "Seeds Report", results per provided seed.
 * 
 * @contributor gojomo
 */
public class SeedsReport extends Report {

    @Override
    public void write(PrintWriter writer) {
        // Build header.
        writer.print("[code] [status] [seed] [redirect]\n");

        long seedsCrawled = 0;
        long seedsTotal = 0;
        for (Iterator<SeedRecord> i = stats.getSeedRecordsSortedByStatusCode(stats.getSeedsIterator());
                i.hasNext();) {
            SeedRecord sr = (SeedRecord)i.next();
            writer.print(sr.getStatusCode());
            writer.print(" ");
            seedsTotal++;
            if((sr.getStatusCode() > 0)) {
                seedsCrawled++;
                writer.print("CRAWLED");
            } else {
                writer.print("NOTCRAWLED");
            }
            writer.print(" ");
            writer.print(sr.getUri());
            if(sr.getRedirectUri()!=null) {
                writer.print(" ");
                writer.print(sr.getRedirectUri());
            }
            writer.print("\n");
        }
        stats.seedsTotal = seedsTotal;
        stats.seedsCrawled = seedsCrawled; 
    }

    @Override
    public String getFilename() {
        return "seeds-report.txt";
    }

}
