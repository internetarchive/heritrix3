/* BloomUriUniqFilter
 *
 * $Id$
 *
 * Created on June 21, 2005
 *
 * Copyright (C) 2005 Internet Archive.
 *
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

import java.io.Serializable;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.util.BloomFilter;
import org.archive.util.BloomFilter32bitSplit;


/**
 * A MG4J BloomFilter-based implementation of an AlreadySeen list.
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
 * The default size is very close to the maximum practical size of the 
 * Bloom filter implementation, BloomFilter32bitSplit, created in the
 * initialize() method, due to integer arithmetic limits. 
 * 
 * If you need a larger filter, you should edit the initialize 
 * method to intantiate a BloomFilter64bit instead.  
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class BloomUriUniqFilter extends SetBasedUriUniqFilter
implements Serializable {
	private static final long serialVersionUID = 1061526253773091309L;

	private static Logger LOGGER =
        Logger.getLogger(BloomUriUniqFilter.class.getName());

    BloomFilter bloom; // package access for testing convenience
    protected int expected_n; // remember bloom contruction param

    protected static final String EXPECTED_SIZE_KEY = ".expected-size";
    protected static final String HASH_COUNT_KEY = ".hash-count";

    // these defaults create a bloom filter that is
    // 1.44*125mil*22/8 ~= 495MB in size, and at full
    // capacity will give a false contained indication
    // 1/(2^22) ~= 1 in every 4 million probes
    private static final int DEFAULT_EXPECTED_SIZE = 125000000; // 125 million
    private static final int DEFAULT_HASH_COUNT = 22; // 1 in 4 million false pos

    /**
     * Default constructor
     */
    public BloomUriUniqFilter() {
        super();
        String ns = System.getProperty(this.getClass().getName() + EXPECTED_SIZE_KEY);
        int n = (ns == null) ? DEFAULT_EXPECTED_SIZE : Integer.parseInt(ns);
        String ds = System.getProperty(this.getClass().getName() + HASH_COUNT_KEY);
        int d = (ds == null) ? DEFAULT_HASH_COUNT : Integer.parseInt(ds);
        initialize(n,d);
    }

    /**
     * Constructor.
     *
     * @param n the expected number of elements.
     * @param d the number of hash functions; if the filter adds not more
     * than <code>n</code> elements, false positives will happen with
     * probability 2<sup>-<var>d</var></sup>.
     */
    public BloomUriUniqFilter( final int n, final int d ) {
        super();
        initialize(n, d);
    }

    /**
     * Initializer shared by constructors.
     *
     * @param n the expected number of elements.
     * @param d the number of hash functions; if the filter adds not more
     * than <code>n</code> elements, false positives will happen with
     * probability 2<sup>-<var>d</var></sup>.
     */
    protected void initialize(final int n, final int d) {
        this.expected_n = n;
        bloom = new BloomFilter32bitSplit(n,d);
    }

    public void forget(String canonical, CrawlURI item) {
        // TODO? could use in-memory exception list of currently-forgotten items
        LOGGER.severe("forget(\""+canonical+"\",CrawlURI) not supported");
    }

    
    protected boolean setAdd(CharSequence uri) {
        boolean added = bloom.add(uri);
        // warn if bloom has reached its expected size (and its false-pos
        // rate will now exceed the theoretical/designed level)
        if( added && (count() == expected_n)) {
            LOGGER.warning("Bloom has reached expected limit "+expected_n);
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
