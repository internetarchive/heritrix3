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

import org.archive.bdb.KryoBinding;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

import org.junit.jupiter.api.Test;

import com.sleepycat.je.DatabaseEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CrawlServer class unit tests.
 */
public class CrawlServerTest {

    @Test
    public void testSerialization() throws Exception {
        CrawlHostTest.testSerialization(new CrawlServer("hi"));
    }

    @Test
    public void testKryoRoundTripKeepsSoft404Fingerprint() {
        CrawlServer server = new CrawlServer("example.com:443");
        CrawlServer.Soft404Fingerprint fingerprint =
                new CrawlServer.Soft404Fingerprint(42, 1234);
        server.startSoft404Probe();
        server.setSoft404FingerprintIfAbsent(fingerprint);

        KryoBinding<CrawlServer> binding = new KryoBinding<>(CrawlServer.class);
        DatabaseEntry entry = new DatabaseEntry();
        binding.objectToEntry(server, entry);
        CrawlServer restored = binding.entryToObject(entry);

        assertTrue(restored.isSoft404ProbeScheduled());
        assertEquals(fingerprint, restored.getSoft404Fingerprint());
    }

    @Test
    public void testGetServerKey() throws Exception {
        UURI u1 = UURIFactory.getInstance("https://www.example.com");
        assertEquals("www.example.com:443", CrawlServer.getServerKey(u1));
    }

}
