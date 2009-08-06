/* WagCostAssignmentPolicy
*
* $Id$
*
* Created on Dec 10, 2004
*
* Copyright (C) 2004 Internet Archive.
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
