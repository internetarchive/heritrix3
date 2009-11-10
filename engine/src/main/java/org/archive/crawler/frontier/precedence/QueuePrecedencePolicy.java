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
