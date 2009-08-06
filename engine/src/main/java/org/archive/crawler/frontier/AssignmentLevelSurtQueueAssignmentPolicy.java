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

import org.archive.modules.CrawlURI;
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
    public String getClassKey(CrawlURI cauri) {
        String candidate =  super.getClassKey(cauri);
        candidate = PublicSuffixes.reduceSurtToAssignmentLevel(candidate); 
        return candidate; 
    }

}
