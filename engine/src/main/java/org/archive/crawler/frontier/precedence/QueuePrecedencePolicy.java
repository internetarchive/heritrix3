/* QueuePrecedencePolicy
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

import java.io.Serializable;

import org.archive.crawler.frontier.WorkQueue;

/**
 * Superclass for QueuePrecedencePolicies, which set a integer precedence value 
 * on uri-queues inside the frontier when the uri-queue is first created, and 
 * before the uri-queue is placed on a new internal queue-of-queues. 
 */
abstract public class QueuePrecedencePolicy implements Serializable {
    
    /**
     * Set an appropriate initial precedence value on the given
     * newly-created WorkQueue.
     * 
     * @param wq WorkQueue to modify
     */
    abstract public void queueCreated(WorkQueue wq);

    /**
     * Update an appropriate initial precedence value on the given
     * already-existing WorkQueue.
     * @param wq WorkQueue to modify
     */
    abstract public void queueReevaluate(WorkQueue wq);
}
