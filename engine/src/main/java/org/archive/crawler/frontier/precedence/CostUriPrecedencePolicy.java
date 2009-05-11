/* CostUriPrecedencePolicy
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

import org.archive.crawler.datamodel.CrawlURI;

/**
 * UriPrecedencePolicy which sets a URI's precedence to its 'cost' -- which
 * simulates the in-queue sorting order in Heritrix 1.x, where cost 
 * contributed the same bits to the queue-insert-key that precedence now does.
 */
public class CostUriPrecedencePolicy extends UriPrecedencePolicy {
    private static final long serialVersionUID = -8164425278358540710L;

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.precedence.UriPrecedencePolicy#uriScheduled(org.archive.crawler.datamodel.CrawlURI)
     */
    @Override
    public void uriScheduled(CrawlURI curi) {
        curi.setPrecedence(curi.getHolderCost()); 
    }
}
