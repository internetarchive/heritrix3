/* AbstractFrontier
 *
 * $Id: AbstractFrontier.java 5053 2007-04-10 02:34:20Z gojomo $
 *
 * Created on June 18, 2007
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
package org.archive.crawler.frontier;

import org.archive.crawler.datamodel.CrawlURI;
import org.archive.net.PublicSuffixes;

/**
 * Create a queueKey based on the SURT authority, reduced to the 
 * public-suffix-plus-one domain (topmost assignable domain). 
 * 
 * @author gojomo
 */
public class TopmostAssignedSurtQueueAssignmentPolicy extends
        SurtAuthorityQueueAssignmentPolicy {
    private static final long serialVersionUID = -1533545293624791702L;

    @Override
    public String getClassKey(CrawlURI cauri) {
        String candidate =  super.getClassKey(cauri);
        candidate = PublicSuffixes.reduceSurtToTopmostAssigned(candidate); 
        return candidate; 
    }

}
