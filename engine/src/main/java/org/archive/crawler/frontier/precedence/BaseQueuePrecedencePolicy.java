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
    
    /** constant precedence to assign; default is 3 (which leaves room 
     * for a simple overlay to prioritize queues) */
    {
        setBasePrecedence(3);
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
