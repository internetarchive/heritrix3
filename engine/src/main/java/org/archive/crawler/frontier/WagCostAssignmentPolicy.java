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
package org.archive.crawler.frontier;

import org.archive.modules.CrawlURI;
import org.archive.net.UURI;

/**
 * A CostAssignmentPolicy based on some wild guesses of kinds of URIs
 * that should be deferred into the (potentially never-crawled) future.
 * 
 * @author gojomo
 */
public class WagCostAssignmentPolicy extends CostAssignmentPolicy {
    private static final long serialVersionUID = 1L;

    /**
     * Add constant penalties for certain features of URI (and
     * its 'via') that make it more delayable/skippable. 
     * 
     * @param curi CrawlURI to be assigned a cost
     * 
     * @see org.archive.crawler.frontier.CostAssignmentPolicy#costOf(org.archive.modules.CrawlURI)
     */
    public int costOf(CrawlURI curi) {
        int cost = 1;
        UURI uuri = curi.getUURI();
        if (uuri.hasQuery()) {
            // has query string
            cost++;
            int qIndex = uuri.toString().indexOf('?');
            if (curi.flattenVia().startsWith(uuri.toString().substring(0,qIndex))) {
                // non-query-string portion of URI is same as previous
                cost++;
            }
            // TODO: other potential query-related cost penalties:
            //  - more than X query-string attributes
            //  - calendarish terms
            //  - query-string over certain size
        }
        // TODO: other potential path-based penalties
        //  - new path is simply extension of via path
        //  - many path segments
        // TODO: other potential hops-based penalties
        //  - more than X hops
        //  - each speculative hop
        return cost;
    }
}
