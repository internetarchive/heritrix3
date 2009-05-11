/* UriPrecedencePolicy.java
*
* $Id: CostAssignmentPolicy.java 4981 2007-03-12 07:06:01Z paul_jack $
*
* Created on Nov 18, 2007
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

import java.io.Serializable;

import org.archive.crawler.datamodel.CrawlURI;

/**
 * Superclass for URI precedence policies, which set a integer 
 * precedence value on individual URIs when they are first 
 * submitted to a frontier for scheduling. 
 * 
 * A URI's precedence directly affects where it lands in an 
 * individual URI queue, but does not affect a queue's precedence
 * relative to other queues *unless* a queue-precedencence-policy 
 * that consults URI precedence values is chosen. 
 * 
 */
abstract public class UriPrecedencePolicy implements Serializable {

    /**
     * Add a precedence value to the supplied CrawlURI, which is being 
     * scheduled onto a frontier queue for the first time. 
     * @param curi CrawlURI to assign a precedence value
     */
    abstract public void uriScheduled(CrawlURI curi);

}
