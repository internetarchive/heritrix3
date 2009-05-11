/* BaseQueuePrecedencePolicy
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

import org.archive.crawler.frontier.WorkQueue;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

/**
 * QueuePrecedencePolicy that sets a uri-queue's precedence to a configured
 * single value. This value may vary for some queues (as in override 
 * settings sheets) or be changed by an operator mid-crawl (subject to limits 
 * on when such changes are noted in a uri-queues lifecycle). 
 * 
 */
public class BaseQueuePrecedencePolicy extends QueuePrecedencePolicy
implements HasKeyedProperties {
    private static final long serialVersionUID = 8312032856661175869L;
    
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    /** constant precedence to assign; default is 1 */
    {
        setBasePrecedence(1);
    }
    public int getBasePrecedence() {
        return (Integer) kp.get("basePrecedence");
    }
    public void setBasePrecedence(int precedence) {
        kp.put("basePrecedence", precedence);
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.QueuePrecedencePolicy#queueCreated(org.archive.crawler.frontier.WorkQueue)
     */
    @Override
    public void queueCreated(WorkQueue wq) {
        installProvider(wq);
    }

    /**
     * Install the appropriate provider helper object into the WorkQueue,
     * if necessary. 
     * 
     * @param wq target WorkQueue this policy will operate on
     */
    protected void installProvider(WorkQueue wq) {
        SimplePrecedenceProvider precedenceProvider = 
            new SimplePrecedenceProvider(calculatePrecedence(wq));
        wq.setPrecedenceProvider(precedenceProvider);
    }

    /**
     * Calculate the precedence value for the given queue. 
     * 
     * @param wq WorkQueue
     * @return int precedence value
     */
    protected int calculatePrecedence(WorkQueue wq) {
        return getBasePrecedence();
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.QueuePrecedencePolicy#queueReevaluate(org.archive.crawler.frontier.WorkQueue)
     */
    @Override
    public void queueReevaluate(WorkQueue wq) {
        PrecedenceProvider precedenceProvider =
            wq.getPrecedenceProvider();
        // TODO: consider if this fails to replace provider that is 
        // a subclass of Simple when necessary
        if(precedenceProvider instanceof SimplePrecedenceProvider) {
            // reset inside provider
            ((SimplePrecedenceProvider)precedenceProvider).setPrecedence(
                    calculatePrecedence(wq));
        } else {
            // replace provider
            installProvider(wq);
        }
    }
}
