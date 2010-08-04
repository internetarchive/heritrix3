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

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringUtils;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

/**
 * QueueAssignmentPolicy based on the hostname:port evident in the given
 * CrawlURI.
 * 
 * @author gojomo
 */
public class HostnameQueueAssignmentPolicy 
extends URIAuthorityBasedQueueAssignmentPolicy {
    private static final long serialVersionUID = 3L;

    @Override
    protected String getCoreKey(UURI basis) {
        String scheme = basis.getScheme();
        String candidate = null;
        try {
            candidate = basis.getAuthorityMinusUserinfo();
        } catch (URIException ue) {}// let next line handle
        
        if(StringUtils.isEmpty(candidate)) {
            return null; 
        }
        if (UURIFactory.HTTPS.equals(scheme)) {
            // If https and no port specified, add default https port to
            // distinguish https from http server without a port.
            if (!candidate.matches(".+:[0-9]+")) {
                candidate += UURIFactory.HTTPS_PORT;
            }
        }
        // Ensure classKeys are safe as filenames on NTFS
        return candidate.replace(':','#');
    }


}
