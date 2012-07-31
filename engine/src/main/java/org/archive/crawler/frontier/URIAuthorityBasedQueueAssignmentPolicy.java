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

import org.apache.commons.lang.StringUtils;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.util.LongToIntConsistentHash;

/**
 * SurtAuthorityQueueAssignmentPolicy based on the surt form of hostname.
 */
public abstract class URIAuthorityBasedQueueAssignmentPolicy
extends 
 QueueAssignmentPolicy 
implements
 HasKeyedProperties {
    private static final long serialVersionUID = 3L;
    
    //for when neat class-key fails us
    protected static String DEFAULT_CLASS_KEY = "default...";

    protected LongToIntConsistentHash conhash = new LongToIntConsistentHash();
    
    /**
     * Whether to always defer to a previously-assigned key inside 
     * the CrawlURI. If true, any key already in the CrawlURI will
     * be returned as the classKey. 
     */
    public boolean getDeferToPrevious() {
        return (Boolean) kp.get("deferToPrevious");
    }
    {
        setDeferToPrevious(true);
    }
    public void setDeferToPrevious(boolean defer) {
        kp.put("deferToPrevious",defer);
    }
    
    /**
     * The number of parallel queues to split a core key into. By 
     * default is 1. If larger than 1, the non-authority-based portion
     * of the URI will be used to distribute over that many separate
     * queues. 
     * 
     */
    public int getParallelQueues() {
        return (Integer) kp.get("parallelQueues");
    }
    {
        setParallelQueues(1);
    }
    public void setParallelQueues(int count) {
        kp.put("parallelQueues",count);
    }

    public String getClassKey(CrawlURI curi) {
        if(getDeferToPrevious() && !StringUtils.isEmpty(curi.getClassKey())) {
            return curi.getClassKey();
        }
        
        if(!StringUtils.isEmpty(getForceQueueAssignment())) {
            return getForceQueueAssignment(); 
        }
        
        // all whois urls in the same queue
        if (curi.getUURI().getScheme().equals("whois")) {
            return "whois...";
        }
        
        UURI basis = curi.getPolicyBasisUURI();
        String candidate = getCoreKey(basis); 
        
        if(StringUtils.isEmpty(candidate)) {
            return DEFAULT_CLASS_KEY;
        }
        
        if(getParallelQueues()>1) {
            int subqueue = getSubqueue(basis,getParallelQueues());
            if (subqueue>0) {
                candidate += "+"+subqueue;
            }
        }
        return candidate; 
    }
    
    protected int getSubqueue(UURI basisUuri, int parallelQueues) {
        String basis = bucketBasis(basisUuri);
        if(StringUtils.isEmpty(basis)) {
            return 0; 
        }
        return conhash.bucketFor(basis, parallelQueues);
    }
    
    /**
     * Base subqueue on first path-segment, if any. (Means unbalanced
     * subqueues, but consistency for most-common case where fanout
     * can be at first segment, and it's beneficial to keep similar
     * URIs in same queue.)
     * @param uuri
     * @return
     */
    protected String bucketBasis(UURI uuri) {
        String path = new String(uuri.getRawPath());
        int i = path.indexOf('/',1);
        if(i<0) {
            return null; 
        }
        return path.substring(1,i);
    }

    protected abstract String getCoreKey(UURI basis);
}
