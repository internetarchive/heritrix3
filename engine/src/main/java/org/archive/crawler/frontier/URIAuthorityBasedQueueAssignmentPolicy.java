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
import org.archive.crawler.datamodel.CrawlURI;
import org.archive.modules.extractor.Hop;
import org.archive.net.UURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;
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
    
    KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    //for when neat class-key fails us
    protected static String DEFAULT_CLASS_KEY = "default...";

    LongToIntConsistentHash conhash = new LongToIntConsistentHash();
    
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
        
        UURI basis = getBasisURI(curi); 
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
    
    protected int getSubqueue(UURI basis, int parallelQueues) {
        if(null==basis.getRawPathQuery()) {
            return 0; 
        }
        return conhash.bucketFor(basis.getRawPathQuery(), parallelQueues);
    }

    abstract String getCoreKey(UURI basis);

    protected UURI getBasisURI(CrawlURI curi) {
        UURI effectiveuuri = null;
        // always use 'via' of prerequisite URIs, if available, so
        // prerequisites go to same queue as trigger URI
        if (curi.getPathFromSeed().endsWith(Hop.PREREQ.getHopString())) {
            effectiveuuri = curi.getVia();
        }
        if(effectiveuuri==null) {
            effectiveuuri = curi.getUURI();
        }
        return effectiveuuri;
    }
}
