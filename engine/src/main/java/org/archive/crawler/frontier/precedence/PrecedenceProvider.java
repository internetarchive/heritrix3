/* PrecedenceProvider
*
* $Id: CostAssignmentPolicy.java 4981 2007-03-12 07:06:01Z paul_jack $
*
* Created on Nov 20, 2007
*
* Copyright (C) 2007 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.archive.crawler.frontier.precedence;

import java.io.PrintWriter;
import java.io.Serializable;

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
        writer.println(singleLineLegend());
        singleLineReportTo(writer);
    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#reportTo(java.io.PrintWriter)
     */
    public void reportTo(PrintWriter writer) {
        reportTo(null,writer);
    }

    public String singleLineLegend() {
        return getClass().getSimpleName();
    }

    public String singleLineReport() {
        return ArchiveUtils.singleLineReport(this);
    }

    public void singleLineReportTo(PrintWriter writer) {
        writer.print(getPrecedence());
    }
}
