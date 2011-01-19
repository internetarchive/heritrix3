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
package org.archive.crawler.frontier;

import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.net.UURIFactory;

import com.sleepycat.je.tree.Key;

import junit.framework.TestCase;

/**
 * Unit tests for BdbMultipleWorkQueues functionality. 
 * 
 * @author gojomo
 */
public class BdbMultipleWorkQueuesTest extends TestCase {
    private static Logger logger =
        Logger.getLogger(BdbMultipleWorkQueuesTest.class.getName());

    
    /**
     * Basic sanity checks for calculateInsertKey() -- ensure ordinal, cost,
     * and schedulingDirective have the intended effects, for ordinal values
     * up through 1/4th of the maximum (about 2^61).
     * 
     * @throws URIException
     */
    public void testCalculateInsertKey() throws URIException {
        while(Thread.interrupted()) {
            logger.warning("stray interrupt cleared");
        }

        for (long ordinalOrigin = 1; ordinalOrigin < Long.MAX_VALUE / 4; ordinalOrigin <<= 1) {
            CrawlURI curi1 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/foo"));
            curi1.setOrdinal(ordinalOrigin);
            curi1.setClassKey("foo");
            byte[] key1 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi1).getData();
            CrawlURI curi2 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/bar"));
            curi2.setOrdinal(ordinalOrigin + 1);
            curi2.setClassKey("foo");
            byte[] key2 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi2).getData();
            CrawlURI curi3 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/baz"));
            curi3.setOrdinal(ordinalOrigin + 2);
            curi3.setClassKey("foo");
            curi3.setSchedulingDirective(SchedulingConstants.HIGH);
            byte[] key3 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi3).getData();
            CrawlURI curi4 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/zle"));
            curi4.setOrdinal(ordinalOrigin + 3);
            curi4.setClassKey("foo");
            curi4.setPrecedence(2);
            byte[] key4 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi4).getData();
            CrawlURI curi5 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/gru"));
            curi5.setOrdinal(ordinalOrigin + 4);
            curi5.setClassKey("foo");
            curi5.setPrecedence(1);
            byte[] key5 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi5).getData();
            // ensure that key1 (with lower ordinal) sorts before key2 (higher
            // ordinal)
            assertTrue("lower ordinal sorting first (" + ordinalOrigin + ")",
                    Key.compareKeys(key1, key2, null) < 0);
            // ensure that key3 (with HIGH scheduling) sorts before key2 (even
            // though
            // it has lower ordinal)
            assertTrue("lower directive sorting first (" + ordinalOrigin + ")",
                    Key.compareKeys(key3, key2, null) < 0);
            // ensure that key5 (with lower cost) sorts before key4 (even though 
            // key4  has lower ordinal and same default NORMAL scheduling directive)
            assertTrue("lower cost sorting first (" + ordinalOrigin + ")", Key
                    .compareKeys(key5, key4, null) < 0);
        }
    }
}
