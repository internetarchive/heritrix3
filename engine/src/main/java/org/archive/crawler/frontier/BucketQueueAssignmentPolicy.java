/* BucketQueueAssignmentPolicy
 * 
 * $Header$
 * 
 * Created on May 06, 2005
 *
 *  Copyright (C) 2005 Christian Kohlschuetter
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.archive.crawler.frontier;

import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Uses the target IPs as basis for queue-assignment,
 * distributing them over a fixed number of sub-queues.
 * 
 * @author Christian Kohlschuetter
 */
public class BucketQueueAssignmentPolicy extends QueueAssignmentPolicy {

    private static final long serialVersionUID = 3L;

    private static final int DEFAULT_NOIP_BITMASK = 1023;
    private static final int DEFAULT_QUEUES_HOSTS_MODULO = 1021;

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    public String getClassKey(final CrawlURI curi) {
        
        CrawlHost host;
        host = serverCache.getHostFor(curi.getUURI());
        if(host == null) {
            return "NO-HOST";
        } else if(host.getIP() == null) {
            return "NO-IP-".concat(Long.toString(Math.abs((long) host
                    .getHostName().hashCode()) & DEFAULT_NOIP_BITMASK));
        } else {
            return Long.toString(Math.abs((long) host.getIP().hashCode())
                    % DEFAULT_QUEUES_HOSTS_MODULO);
        }
    }

    public int maximumNumberOfKeys() {
        return DEFAULT_NOIP_BITMASK + DEFAULT_QUEUES_HOSTS_MODULO + 2;
    }
}
