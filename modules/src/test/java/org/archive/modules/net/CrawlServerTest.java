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

import org.archive.modules.net.CrawlServer;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TestUtils;

import junit.framework.TestCase;

/**
 * CrawlServer class unit tests.
 */
public class CrawlServerTest extends TestCase {

    
    public void testSerialization() throws Exception {
        TestUtils.testSerialization(new CrawlServer("hi"));
    }

    public void testGetServerKey() throws Exception {
        UURI u1 = UURIFactory.getInstance("https://www.example.com");
        assertEquals(
                "bad https key",
                "www.example.com:443",
                CrawlServer.getServerKey(u1));
    }
    
}
