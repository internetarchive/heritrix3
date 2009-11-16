/* BloomUriUniqFilter
*
 * $Id$
*
* Created on June 21, 2005
*
* Copyright (C) 2005 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
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

import java.io.Serializable;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.util.BloomFilter;
import org.archive.util.BloomFilter64bit;
import org.springframework.beans.factory.InitializingBean;


/**
 * An implementation of an AlreadySeen list based on the MG4J BloomFilter.
 *
 * This implementation performs adequately without blowing out
 * the heap through to very large numbers of URIs. See
 * <a href="http://crawler.archive.org/cgi-bin/wiki.pl?AlreadySeen">AlreadySeen</a>.
 * 
 * It is inherent to Bloom filters that as they get 'saturated', their
 * false-positive rate rises. The default parameters used by this class
 * attempt to maintain a 1-in-4 million (1 in 2^22) false-positive chance
 * through 125 million unique inserts, which creates a filter structure 
 * about 495MB in size. 
 * 
 * You may use the following system properties to tune the size and 
 * false-positive rate of the bloom filter structure used by this class:
 * 
 *  org.archive.crawler.util.BloomUriUniqFilter.expected-size (default 125000000)
 *  org.archive.crawler.util.BloomUriUniqFilter.hash-count (default 22)
 * 
 * The resulting filter will take up approximately...
 * 
 *    1.44 * expected-size * hash-count / 8 
 *    
 * ...bytes. 
 * 
 * The BloomFilter64bit implementation class supports filters up to 
 * 16GiB in size. 
 * 
 * (If you only need a filter up to 512MiB in size, the 
 * BloomFilter32bitSplit *might* offer better performance, on 32bit
 * JVMs or with respect to heap-handling of giant arrays. The only 
 * current way to swap in this class is by editing the source.)
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class BloomUriUniqFilter extends SetBasedUriUniqFilter
implements Serializable, InitializingBean {
	private static final long serialVersionUID = 1061526253773091309L;

	private static Logger LOGGER =
        Logger.getLogger(BloomUriUniqFilter.class.getName());

    BloomFilter bloom; // package access for testing convenience
    
    // these defaults create a bloom filter that is
    // 1.44*125mil*22/8 ~= 495MB in size, and at full
    // capacity will give a false contained indication
    // 1/(2^22) ~= 1 in every 4 million probes
    protected int expectedInserts= 125000000; // default 125 million;
    public int getExpectedInserts() {
        return expectedInserts;
    }
    public void setExpectedInserts(int expectedInserts) {
        this.expectedInserts = expectedInserts;
    }

    protected int hashCount = 22; // 1 in 4 million false pos
    public int getHashCount() {
        return hashCount;
    }
    public void setHashCount(int hashCount) {
        this.hashCount = hashCount;
    }
    
    
    /**
     * Default constructor
     */
    public BloomUriUniqFilter() {
        super();
    }

    /**
     * Initializer.
     *
     * @param n the expected number of elements.
     * @param d the number of hash functions; if the filter adds not more
     * than <code>n</code> elements, false positives will happen with
     * probability 2<sup>-<var>d</var></sup>.
     */
    public void afterPropertiesSet() {
        bloom = new BloomFilter64bit(expectedInserts,hashCount);
    }

    public void forget(String canonical, CrawlURI item) {
        // TODO? could use in-memory exception list of currently-forgotten items
        LOGGER.severe("forget(\""+canonical+"\",CrawlURI) not supported");
    }

    protected boolean setAdd(CharSequence uri) {
        boolean added = bloom.add(uri);
        // warn if bloom has reached its expected size (and its false-pos
        // rate will now exceed the theoretical/designed level)
        if( added && (count() == expectedInserts)) {
            LOGGER.warning("Bloom has reached expected limit "+expectedInserts);
        }
        return added;
    }

    protected long setCount() {
        return bloom.size();
    }

    protected boolean setRemove(CharSequence uri) {
        throw new UnsupportedOperationException();
    }
}
