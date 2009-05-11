/* QueueAssignmentPolicy
*
* $Id$
*
* Created on Oct 5, 2004
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

import java.io.Serializable;

import org.archive.crawler.datamodel.CrawlURI;

/**
 * Establishes a mapping from CrawlURIs to String keys (queue names).
 * 
 * @author gojomo
 */
public abstract class QueueAssignmentPolicy implements Serializable {
    /** 
     * Get the String key (name) of the queue to which the 
     * CrawlURI should be assigned. 
     * 
     * Note that changes to the CrawlURI, or its associated 
     * components (such as CrawlServer), may change its queue
     * assignment.
     * @param controller This crawls' controller.
     * 
     * @param cauri CandidateURI to calculate class key for.
     * @return the String key of the queue to assign the CrawlURI 
     */
    public abstract String getClassKey(CrawlURI cauri);
    
    /**
     * Returns the maximum number of different keys this policy
     * can create. If there is no maximum, -1 is returned (default).
     * 
     * @return  Maximum number of different keys, or -1 if unbounded.
     */
    public int maximumNumberOfKeys() {
        return -1;
    }
}
