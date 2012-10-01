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

import java.io.Serializable;

import org.archive.modules.CrawlURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

/**
 * Establishes a mapping from CrawlURIs to String keys (queue names).
 * 
 * @author gojomo
 */
public abstract class QueueAssignmentPolicy implements Serializable, HasKeyedProperties {
    private static final long serialVersionUID = 1L;
    
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    /** queue assignment to force onto CrawlURIs; intended to be overridden */
    {
        setForceQueueAssignment("");
    }
    public String getForceQueueAssignment() {
        return (String) kp.get("forceQueueAssignment");
    }
    public void setForceQueueAssignment(String forceQueueAssignment) {
        kp.put("forceQueueAssignment",forceQueueAssignment);
    }
    
    
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
