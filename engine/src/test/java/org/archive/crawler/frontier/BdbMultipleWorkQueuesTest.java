/* BdbMultipleWorkQueuesTest
*
* $Id$
*
* Created on Jul 21, 2005
*
* Copyright (C) 2005 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
            CrawlURI cauri1 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/foo"));
            CrawlURI curi1 = new CrawlURI(cauri1, ordinalOrigin);
            curi1.setClassKey("foo");
            byte[] key1 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi1).getData();
            CrawlURI cauri2 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/bar"));
            CrawlURI curi2 = new CrawlURI(cauri2, ordinalOrigin + 1);
            curi2.setClassKey("foo");
            byte[] key2 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi2).getData();
            CrawlURI cauri3 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/baz"));
            CrawlURI curi3 = new CrawlURI(cauri3, ordinalOrigin + 2);
            curi3.setClassKey("foo");
            curi3.setSchedulingDirective(SchedulingConstants.HIGH);
            byte[] key3 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi3).getData();
            CrawlURI cauri4 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/zle"));
            CrawlURI curi4 = new CrawlURI(cauri4, ordinalOrigin + 3);
            curi4.setClassKey("foo");
            curi4.setPrecedence(2);
            byte[] key4 = 
                BdbMultipleWorkQueues.calculateInsertKey(curi4).getData();
            CrawlURI cauri5 = 
                new CrawlURI(UURIFactory.getInstance("http://archive.org/gru"));
            CrawlURI curi5 = new CrawlURI(cauri5, ordinalOrigin + 4);
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
