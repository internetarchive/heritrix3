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
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.util.Arrays;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.archive.bdb.AutoKryo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CrawlHostTest {

    @Test
    public void testSerialization() throws Exception {
        testSerialization(new CrawlHost("hi"));
        
        InetAddress localhost = InetAddress.getLocalHost();
        CrawlHost crawlHost = new CrawlHost(localhost.getHostName());
        crawlHost.setIP(localhost, 431243);
        testSerialization(crawlHost);
        
        byte[] serialCrawlHost = serialize(crawlHost);
        ByteArrayInputStream binp = new ByteArrayInputStream(serialCrawlHost);
        ObjectInputStream oinp = new ObjectInputStream(binp);
        Object o = oinp.readObject();
        oinp.close();
        assertEquals(crawlHost.getClass(), o.getClass());
        assertEquals(crawlHost, o);
    }

    @Test
    public void testKryoSerialization() throws Exception {
        AutoKryo kryo = new AutoKryo();
        kryo.autoregister(CrawlHost.class);

        InetAddress localhost = InetAddress.getLocalHost();
        CrawlHost crawlHost0 = new CrawlHost(localhost.getHostName());
        crawlHost0.setIP(localhost, 431243);

        Output buffer = new Output(1024, -1);
        kryo.writeObject(buffer, crawlHost0);

        CrawlHost crawlHost1 = kryo.readObject(new Input(buffer.toBytes()), CrawlHost.class);

        assertEquals(crawlHost0.getClass(), crawlHost1.getClass());
        assertEquals(crawlHost0, crawlHost1);
        assertEquals(localhost, crawlHost1.getIP());
    }


    public static void testSerialization(Object proc) throws Exception {
        byte[] first = serialize(proc);
        ByteArrayInputStream binp = new ByteArrayInputStream(first);
        ObjectInputStream oinp = new ObjectInputStream(binp);
        Object o = oinp.readObject();
        oinp.close();
        assertEquals(proc.getClass(), o.getClass());
        byte[] second = serialize(o);
        assertTrue(Arrays.equals(first, second));
    }

    public static byte[] serialize(Object o) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);
        oout.writeObject(o);
        oout.close();
        return bout.toByteArray();
    }
}
