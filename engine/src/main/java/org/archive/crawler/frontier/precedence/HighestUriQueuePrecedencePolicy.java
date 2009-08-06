/* HighestUriQueuePrecedencePolicy
*
* $Id: CostAssignmentPolicy.java 4981 2007-03-12 07:06:01Z paul_jack $
*
* Created on Nov 17, 2007
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

import org.archive.crawler.frontier.WorkQueue;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessorURI;
import org.archive.modules.fetcher.FetchStats.Stage;
import org.archive.util.Histotable;

/**
 * QueuePrecedencePolicy that sets a uri-queue's precedence to that of the
 * highest URI currently enqueued within itself, added to the configured 
 * base-precedence. 
 * 
 * It does this by maintaining a count of the included URIs at each 
 * URI-precedence, updated on each URI add or remove. 
 */
public class HighestUriQueuePrecedencePolicy extends BaseQueuePrecedencePolicy {
    private static final long serialVersionUID = -8652293180921419601L;
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.precedence.BaseQueuePrecedencePolicy#installProvider(org.archive.crawler.frontier.WorkQueue)
     */
    @Override
    protected void installProvider(WorkQueue wq) {
        // TODO:SPRINGY ensure proper override context installed for getBasePrecedence() below
        HighestUriPrecedenceProvider provider = new HighestUriPrecedenceProvider(getBasePrecedence());
        wq.setPrecedenceProvider(provider);
    }

    /**
     * Helper provider for maintaining the tracked distribution of included
     * URIs and calculating the queue precedence. 
     */
    public class HighestUriPrecedenceProvider extends SimplePrecedenceProvider {
        private static final long serialVersionUID = 5545297542888582745L;
        
        protected Histotable<Integer> enqueuedCounts = new Histotable<Integer>();
        public HighestUriPrecedenceProvider(int base) {
            super(base);
        }
        
        /* (non-Javadoc)
         * @see org.archive.crawler.frontier.precedence.PrecedenceProvider#tally(org.archive.modules.ProcessorURI, org.archive.modules.fetcher.FetchStats.Stage)
         */
        @Override
        public void tally(ProcessorURI puri, Stage stage) {
            CrawlURI curi = (CrawlURI)puri; 
            switch(stage) {
            case SCHEDULED:
                // enqueued
                enqueuedCounts.tally(curi.getPrecedence());
                break;
            case SUCCEEDED:
            case DISREGARDED:
            case FAILED:
                // dequeued
                enqueuedCounts.tally(curi.getPrecedence(),-1);
                break;
            case RETRIED:
                // do nothing, already tallied
                break;
            }
        }

        /* (non-Javadoc)
         * @see org.archive.crawler.frontier.precedence.SimplePrecedenceProvider#getPrecedence()
         */
        @Override
        public int getPrecedence() {
            // base plus highest URI still in queue
            Integer delta = (enqueuedCounts.size() > 0) ? enqueuedCounts.firstKey() : 0;
            return super.getPrecedence() + delta;
        }

        /* (non-Javadoc)
         * @see org.archive.crawler.frontier.precedence.PrecedenceProvider#singleLineLegend()
         */
        @Override
        public String singleLineLegend() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.singleLineLegend());
            sb.append(":");
            for(Integer p : enqueuedCounts.keySet()) {
                sb.append(" p");
                sb.append(p);
            }
            return sb.toString(); 
        }

        /* (non-Javadoc)
         * @see org.archive.crawler.frontier.precedence.PrecedenceProvider#singleLineReportTo(java.io.PrintWriter)
         */
        @Override
        public void singleLineReportTo(PrintWriter writer) {
            boolean betwixt = false; 
            for(Long count : enqueuedCounts.values()) {
                if(betwixt) writer.print(" ");
                writer.print(count);
                betwixt = true;
            }
        }

    }
}
