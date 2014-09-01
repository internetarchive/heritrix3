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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.LinkContext.SimpleLinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TmpDirTestCase;

/**
 * Tests related to CrawlURI
 *
 * @contributor stack
 * @contributor gojomo
 * @version $Revision$, $Date$
 */
public class CrawlURITest extends TmpDirTestCase {

    CrawlURI seed = null;

    protected void setUp() throws Exception {
        super.setUp();
        final String url = "http://www.dh.gov.uk/Home/fs/en";
        this.seed = new CrawlURI(UURIFactory.getInstance(url));
        this.seed.setSchedulingDirective(SchedulingConstants.MEDIUM);
        this.seed.setSeed(true);
        // Force caching of string.
        this.seed.toString();
        // TODO: should this via really be itself?
        this.seed.setVia(UURIFactory.getInstance(url));
    }

    /**
     * Test serialization/deserialization works.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    final public void testSerialization()
            throws IOException, ClassNotFoundException {
        File serialize = new File(getTmpDir(),
                this.getClass().getName() + ".serialize");
        try {
            FileOutputStream fos = new FileOutputStream(serialize);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.seed);
            oos.reset();
            oos.writeObject(this.seed);
            oos.reset();
            oos.writeObject(this.seed);
            oos.close();
            // Read in the object.
            FileInputStream fis = new FileInputStream(serialize);
            ObjectInputStream ois = new ObjectInputStream(fis);
            CrawlURI deserializedCuri = (CrawlURI)ois.readObject();
            deserializedCuri = (CrawlURI)ois.readObject();
            deserializedCuri = (CrawlURI)ois.readObject();
            assertEquals("Deserialized not equal to original",
                    this.seed.toString(), deserializedCuri.toString());
            String host = this.seed.getUURI().getHost();
            assertTrue("Deserialized host not null",
                    host != null && host.length() >= 0);
        } finally {
            serialize.delete();
        }
    }

    public void testCandidateURIWithLoadedAList()
            throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.archive.org");
        CrawlURI curi = new CrawlURI(uuri);
        curi.setSeed(true);
        curi.getData().put("key", "value");
        assertTrue("Didn't find AList item",
                curi.getData().get("key").equals("value"));
    }

    public void testExtendHopsPath() {
        assertEquals("from empty","L",CrawlURI.extendHopsPath("",'L'));

        assertEquals("from one","LX",CrawlURI.extendHopsPath("L",'X'));

        assertEquals(
                "from fortynine",
                "LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLX",
                CrawlURI.extendHopsPath("LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL",'X'));

        assertEquals(
                "from fifty",
                "1+LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLX",
                CrawlURI.extendHopsPath("LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL",'X'));

        assertEquals(
                "from 149",
                "100+LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLX",
                CrawlURI.extendHopsPath("99+LLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL",'X'));
    }


    public void testNullPathFromSeed() throws URIException {
        // check comparing with null
        CrawlURI a = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                null, // a < b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a == b
        assertEquals("", a.getPathFromSeed());

        CrawlURI b = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                "", // a < b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a == b
        assertEquals("", b.getPathFromSeed());

        assertEquals(0, a.compareTo(b));
        assertEquals(0, b.compareTo(a));

    }

    public void testOrdering() throws URIException {
        // check that via is highest precedence
        CrawlURI a = new CrawlURI(
                UURIFactory.getInstance("http://example.com/2"), // a > b
                "2", // a > b
                UURIFactory.getInstance("http://example.com/via/1"), // a < b
                new SimpleLinkContext("2")); // a > b
        CrawlURI b = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a > b
                "1", // a > b
                UURIFactory.getInstance("http://example.com/via/2"), // a < b
                new SimpleLinkContext("1")); // a > b
        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));

        // check that uri is next highest
        a = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a < b
                "2", // a > b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("2")); // a > b
        b = new CrawlURI(
                UURIFactory.getInstance("http://example.com/2"), // a < b
                "1", // a > b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a > b
        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));

        // check that via context is next
        a = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                "2", // a > b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a < b
        b = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                "1", // a > b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("2")); // a < b
        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));

        // check that pathFromSeed is next
        a = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                "1", // a < b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a == b
        b = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                "2", // a < b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a == b
        assertEquals(-1, a.compareTo(b));
        assertEquals(1, b.compareTo(a));

        // check equality
        a = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                "1", // a == b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a == b
        b = new CrawlURI(
                UURIFactory.getInstance("http://example.com/1"), // a == b
                "1", // a == b
                UURIFactory.getInstance("http://example.com/via/1"), // a == b
                new SimpleLinkContext("1")); // a == b
        assertEquals(0, a.compareTo(b));
        assertEquals(0, b.compareTo(a));
    }
}
