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
import org.archive.net.PublicSuffixes;

/**
 * Create a queueKey based on the SURT authority, reduced to the 
 * public-suffix-plus-one domain (topmost assignable domain). 
 * 
 * @author gojomo
 */
public class AssignmentLevelSurtQueueAssignmentPolicy extends
        SurtAuthorityQueueAssignmentPolicy {
    private static final long serialVersionUID = -1533545293624791702L;

    @Override
    public String getClassKey(CrawlURI curi) {
        if(getDeferToPrevious() && !StringUtils.isEmpty(curi.getClassKey())) {
	    return curi.getClassKey();
	}

	UURI basis = curi.getPolicyBasisUURI();
	String candidate =  super.getClassKey(curi);
        candidate = PublicSuffixes.reduceSurtToAssignmentLevel(candidate);

	if(!StringUtils.isEmpty(getForceQueueAssignment())) {
	    candidate = getForceQueueAssignment();
	}

	// all whois urls in the same queue
	if (curi.getUURI().getScheme().equals("whois")) {
	    return "whois...";
	}

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

}
