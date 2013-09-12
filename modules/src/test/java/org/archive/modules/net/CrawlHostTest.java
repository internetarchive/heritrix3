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

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import junit.framework.TestCase;

import org.archive.bdb.AutoKryo;
import org.archive.util.TestUtils;

public class CrawlHostTest extends TestCase {

    public void testSerialization() throws Exception {
        TestUtils.testSerialization(new CrawlHost("hi"));
        
        InetAddress localhost = InetAddress.getLocalHost();
        CrawlHost crawlHost = new CrawlHost(localhost.getHostName());
        crawlHost.setIP(localhost, 431243);
        TestUtils.testSerialization(crawlHost);
        
        byte[] serialCrawlHost = TestUtils.serialize(crawlHost);
        ByteArrayInputStream binp = new ByteArrayInputStream(serialCrawlHost);
        ObjectInputStream oinp = new ObjectInputStream(binp);
        Object o = oinp.readObject();
        oinp.close();
        TestCase.assertEquals(crawlHost.getClass(), o.getClass());
        TestCase.assertEquals(crawlHost, o);
    }
    
    public void testKryoSerialization() throws Exception {
        AutoKryo kryo = new AutoKryo();
        kryo.autoregister(CrawlHost.class);

        InetAddress localhost = InetAddress.getLocalHost();
        CrawlHost crawlHost0 = new CrawlHost(localhost.getHostName());
        crawlHost0.setIP(localhost, 431243);

        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        kryo.writeObject(buffer, crawlHost0);
        buffer.flip();
        
        CrawlHost crawlHost1 = kryo.readObject(buffer, CrawlHost.class);

        TestCase.assertEquals(crawlHost0.getClass(), crawlHost1.getClass());
        TestCase.assertEquals(crawlHost0, crawlHost1);
        TestCase.assertEquals(localhost, crawlHost1.getIP());
    }
}
