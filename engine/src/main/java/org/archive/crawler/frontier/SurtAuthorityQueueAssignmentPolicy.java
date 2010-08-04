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

import org.archive.net.UURI;

/**
 * SurtAuthorityQueueAssignmentPolicy based on the surt form of hostname.
 */
public class SurtAuthorityQueueAssignmentPolicy
extends URIAuthorityBasedQueueAssignmentPolicy {
    private static final long serialVersionUID = 3L;
    
    @Override
    protected String getCoreKey(UURI basis) {
        String candidate = getSurtAuthority(basis.getSurtForm());
        return candidate.replace(':','#');
    }
    
    protected String getSurtAuthority(String surt) {
        int indexOfOpen = surt.indexOf("://(");
        int indexOfClose = surt.indexOf(")");
        if (indexOfOpen == -1 || indexOfClose == -1
                || ((indexOfOpen + 4) >= indexOfClose)) {
            return DEFAULT_CLASS_KEY;
        }
        return surt.substring(indexOfOpen + 4, indexOfClose);
    }
}
