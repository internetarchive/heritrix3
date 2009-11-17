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
package org.archive.modules.net;

import junit.framework.TestCase;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.fetcher.DefaultServerCache;
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
        servers.getServerFor(uuri);
        servers.getHostFor(uuri);
        assertTrue("cache lost server",
            servers.containsServer(CrawlServer.getServerKey(uuri)));
        assertTrue("cache lost host",
            servers.containsHost(uuri.getHost()));
    }
}
