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

import org.archive.crawler.frontier.WorkQueue;
import org.archive.modules.CrawlURI;
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
         * @see org.archive.crawler.frontier.precedence.PrecedenceProvider#tally(org.archive.modules.CrawlURI, org.archive.modules.fetcher.FetchStats.Stage)
         */
        @Override
        public void tally(CrawlURI curi, Stage stage) {
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

        /*
         * @see org.archive.crawler.frontier.precedence.PrecedenceProvider#shortReportLegend()()
         */
        @Override
        public String shortReportLegend() {
            StringBuilder sb = new StringBuilder();
            sb.append(super.shortReportLegend());
            sb.append(":");
            for(Integer p : enqueuedCounts.keySet()) {
                sb.append(" p");
                sb.append(p);
            }
            return sb.toString(); 
        }

        @Override
        public void shortReportLineTo(PrintWriter writer) {
            boolean betwixt = false; 
            for(Long count : enqueuedCounts.values()) {
                if(betwixt) writer.print(" ");
                writer.print(count);
                betwixt = true;
            }
        }

    }
}
