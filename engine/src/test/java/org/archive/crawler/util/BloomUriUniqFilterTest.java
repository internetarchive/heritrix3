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
 
package org.archive.crawler.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

import org.archive.url.URIException;
import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.BloomFilter64bit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test BloomUriUniqFilter.
 * @author gojomo
 */
public class BloomUriUniqFilterTest
implements UriUniqFilter.CrawlUriReceiver {
    private Logger logger =
        Logger.getLogger(BloomUriUniqFilterTest.class.getName());

    private BloomUriUniqFilter filter = null;

    /**
     * Set to true if we visited received.
     */
    private boolean received = false;

    @BeforeEach
    protected void setUp() {
        this.filter = new BloomUriUniqFilter();
        this.filter.setBloomFilter(new BloomFilter64bit(2000, 24));
        this.filter.afterPropertiesSet();
        this.filter.setDestination(this);
    }

    @Test
    public void testAdding() throws URIException {
        this.filter.add(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addNow(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addForce(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        // Should only have add 'this' once.
        assertEquals(1, this.filter.count(), "Count is off");
    }

    /**
     * Test inserting.
     */
    @Test
    public void testWriting() throws URIException {
        long start = System.currentTimeMillis();
        ArrayList<UURI> list = new ArrayList<UURI>(1000);
        int count = 0;
        final int MAX_COUNT = 1000;
        for (; count < MAX_COUNT; count++) {
            assertEquals(count,filter.count(), "count off");
            UURI u = UURIFactory.getInstance("http://www" +
                    count + ".archive.org/" + count + "/index.html");
            assertFalse(filter.bloom.contains(u.toString()), "already contained "+u);
            logger.fine("adding " + u);
            filter.add(u.toString(), new CrawlURI(u));
            assertTrue(filter.bloom.contains(u.toString()), "not in bloom");
            if (count > 0 && ((count % 100) == 0)) {
                list.add(u);
            }
        }
        logger.fine("Added " + count + " in " +
                (System.currentTimeMillis() - start));

        start = System.currentTimeMillis();
        for (Iterator<UURI> i = list.iterator(); i.hasNext();) {
            UURI uuri = i.next();
            filter.add(uuri.toString(), new CrawlURI(uuri));
        }
        logger.fine("Readded subset " + list.size() + " in " +
                (System.currentTimeMillis() - start));

        assertEquals(MAX_COUNT, filter.count());
    }

    @Test
    public void testNote() {
        filter.note(this.getUri());
        assertFalse(this.received, "Receiver was called");
    }

// FORGET CURRENTLY UNSUPPORTED IN BloomUriUniqFilter
//    public void testForget() throws URIException {
//        this.filter.forget(this.getUri(),
//                new CrawlURI(UURIFactory.getInstance(this.getUri())));
//        assertTrue("Didn't forget", this.filter.count() == 0);
//    }

    public void receive(CrawlURI item) {
        this.received = true;
    }

    public String getUri() {
        return "http://www.archive.org";
    }
}
