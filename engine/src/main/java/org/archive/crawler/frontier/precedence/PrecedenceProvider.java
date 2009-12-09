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
package org.archive.crawler.frontier.precedence;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStats;
import org.archive.modules.fetcher.FetchStats.Stage;
import org.archive.util.ArchiveUtils;
import org.archive.util.MultiReporter;

/**
 * Parent class for precedence-providers, stateful helpers that can be 
 * installed in a WorkQueue to implement various queue-precedence policies. 
 */
abstract public class PrecedenceProvider implements MultiReporter, 
FetchStats.CollectsFetchStats, Serializable {

    private static final long serialVersionUID = 1L;

    abstract public int getPrecedence();

    /* (non-Javadoc)
     * @see org.archive.modules.fetcher.FetchStats.CollectsFetchStats#tally(org.archive.modules.CrawlURI, org.archive.modules.fetcher.FetchStats.Stage)
     */
    public void tally(CrawlURI curi, Stage stage) {
        // by default do nothing; subclasses do more
    }

    public String[] getReports() {
        // TODO Auto-generated method stub
        return null;
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.lang.String, java.io.PrintWriter)
     */
    public void reportTo(String name, PrintWriter writer) {
        // name ignored, only one report
        writer.println(shortReportLegend());
        shortReportLineTo(writer);
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.io.PrintWriter)
     */
    public void reportTo(PrintWriter writer) {
        reportTo(null,writer);
    }

    public String shortReportLegend() {
        return getClass().getSimpleName();
    }

    public String shortReportLine() {
        return ArchiveUtils.shortReportLine(this);
    }

    public Map<String, Object> shortReportMap() {
        Map<String,Object> data = new LinkedHashMap<String, Object>();
        data.put("precedence", getPrecedence());
        return data;
    }

    public void shortReportLineTo(PrintWriter writer) {
        writer.print(getPrecedence());
    }
}
