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
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Uses target IP as basis for queue-assignment, unless it is unavailable,
 * in which case it behaves as HostnameQueueAssignmentPolicy.
 * 
 * @author gojomo
 */
public class IPQueueAssignmentPolicy
    extends HostnameQueueAssignmentPolicy {
    private static final long serialVersionUID = 3L;

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    public String getClassKey(CrawlURI cauri) {
        CrawlHost host = serverCache.getHostFor(cauri.getUURI());
        if (host == null || host.getIP() == null) {
            // if no server or no IP, use superclass implementation
            return super.getClassKey(cauri);
        }
        // use dotted-decimal IP address
        return host.getIP().getHostAddress();
    }
}
