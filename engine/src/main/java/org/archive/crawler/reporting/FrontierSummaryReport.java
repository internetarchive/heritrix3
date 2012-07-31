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

import java.io.IOException;
import java.io.PrintWriter;

/**
 * Frontier summary report showing a limited number of queues of each 
 * type -- as typically consulted during a crawl in progress. 
 * 
 * @contributor gojomo
 */
public class FrontierSummaryReport extends Report {

    @Override
    public void write(PrintWriter writer, StatisticsTracker stats) {
        if(!stats.controller.getFrontier().isRunning()) {
            writer.println("frontier unstarted");
        } else {
            try {
                stats.controller.getFrontier().reportTo(writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String getFilename() {
        return "frontier-summary-report.txt";
    }

}
