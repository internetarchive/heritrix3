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
package org.archive.crawler.datamodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import junit.framework.TestCase;

import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;

/**
 * Test  CandidateURI serialization.
 * @author stack
 */
public class CandidateURITest extends TestCase {
    public void testSerialization()
    throws IOException, ClassNotFoundException {
        doOneSerialization("http://www.archive.org/");
        doOneSerialization("http://www.archive.org/a?" +
            "sch=%2E%2F%3Faction%3Dsearch");
    }
    
    private void doOneSerialization(final String urlStr)
    throws IOException, ClassNotFoundException {
        CrawlURI cauri =
            new CrawlURI(UURIFactory.getInstance(urlStr));
        cauri = serialize(cauri);
        assertEquals(urlStr + " doesn't serialize", urlStr,
            cauri.getUURI().toString());  
    }
    
    private CrawlURI serialize(CrawlURI cauri)
    throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(cauri);
        oos.flush();
        oos.close();
        ByteArrayInputStream bais =
            new ByteArrayInputStream(baos.toByteArray());
        return (CrawlURI)(new ObjectInputStream(bais)).readObject();
    }
}
