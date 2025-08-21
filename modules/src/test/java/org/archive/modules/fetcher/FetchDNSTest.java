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
package org.archive.modules.fetcher;

import org.archive.modules.ProcessorTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;

/**
 * @author pjack
 *
 */
public class FetchDNSTest extends ProcessorTestBase {

    @Test
    public void testZeroAddressIsIgnored() throws TextParseException {
        FetchDNS dns = new FetchDNS();
        Assertions.assertNull(dns.getFirstARecord(new Record[]{
                new ARecord(Name.fromString("example.org."), 0, 1000L, new byte[]{0, 0, 0, 0})
        }));
        Assertions.assertEquals("1.2.3.4", dns.getFirstARecord(new Record[]{
                new ARecord(Name.fromString("example.org."), 0, 1000L, new byte[]{0, 0, 0, 0}),
                new ARecord(Name.fromString("example.org."), 0, 1000L, new byte[]{1, 2, 3, 4}),
        }).getAddress().getHostAddress());
    }
    
}
