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

/**
 * Abstract superclass for named crawl reports that need only a 
 * StatisticsTracker and can dump a plain-text representation to a
 * PrintWriter. 
 * 
 * @contributor gojomo
 */
public abstract class Report {
    public Report() {
    }
    
    public abstract void write(PrintWriter writer, StatisticsTracker stats); 
    
    public abstract String getFilename();

    private boolean shouldReportAtEndOfCrawl = true;
    public boolean getShouldReportAtEndOfCrawl() {
        return shouldReportAtEndOfCrawl;
    }

    public void setShouldReportAtEndOfCrawl(boolean shouldReportAtEndOfCrawl) {
        this.shouldReportAtEndOfCrawl = shouldReportAtEndOfCrawl;
    }

    private boolean shouldReportDuringCrawl = true;
    public boolean getShouldReportDuringCrawl() {
        return shouldReportDuringCrawl;
    }

    public void setShouldReportDuringCrawl(boolean shouldReportDuringCrawl) {
        this.shouldReportDuringCrawl = shouldReportDuringCrawl;
    }
}
