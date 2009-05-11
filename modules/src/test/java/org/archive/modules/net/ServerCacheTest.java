/* ServerCacheTest
*
* Created on August 4, 2004
*
* Copyright (C) 2004 Internet Archive.
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
*/
package org.archive.modules.net;

import junit.framework.TestCase;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCacheUtil;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

/**
 * Test the BigMapServerCache
 * 
 * @author gojomo
 */
public class ServerCacheTest extends TestCase {
    public void testHolds() throws Exception {
        DefaultServerCache servers = new DefaultServerCache();
        String serverKey = "www.example.com:9090";
        String hostKey = "www.example.com";
        servers.getServerFor(serverKey);
        servers.getHostFor(hostKey);
        assertTrue("cache lost server", servers.containsServer(serverKey));
        assertTrue("cache lost host", servers.containsHost(hostKey));
    }
    
    public void testCrawlURIKeys()
    throws Exception {
        DefaultServerCache servers = new DefaultServerCache();
        testHostServer(servers, "http://www.example.com");
        testHostServer(servers, "http://www.example.com:9090");
        testHostServer(servers, "dns://www.example.com:9090");
    }
    
    private void testHostServer(DefaultServerCache servers, String uri)
    throws URIException {
        UURI uuri = UURIFactory.getInstance(uri);
        ServerCacheUtil.getServerFor(servers, uuri);
        ServerCacheUtil.getHostFor(servers, uuri);;
        assertTrue("cache lost server",
            servers.containsServer(CrawlServer.getServerKey(uuri)));
        assertTrue("cache lost host",
            servers.containsHost(uuri.getHost()));
    }
}
