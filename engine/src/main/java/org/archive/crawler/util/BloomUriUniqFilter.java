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
 * You may swap in an differently-configured BloomFilter class to alter
 * these tradeoffs. 
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class BloomUriUniqFilter extends SetBasedUriUniqFilter
implements Serializable, InitializingBean {
	private static final long serialVersionUID = 1061526253773091309L;

	private static Logger LOGGER =
        Logger.getLogger(BloomUriUniqFilter.class.getName());

	protected BloomFilter bloom; // package access for testing convenience
    public BloomFilter getBloomFilter() {
        return bloom; 
    }
    public void setBloomFilter(BloomFilter filter) {
        bloom = filter; 
    }

    /**
     * Default constructor
     */
    public BloomUriUniqFilter() {
        super();
    }

    /**
     * Initializer.
     */
    public void afterPropertiesSet() {
        if(bloom==null) {
            // configure default bloom filter if operator hasn't already

            // these defaults create a bloom filter that is
            // 1.44*125mil*22/8 ~= 495MB in size, and at full
            // capacity will give a false contained indication
            // 1/(2^22) ~= 1 in every 4 million probes
            bloom = new BloomFilter64bit(125000000,22);
        }
    }

    public void forget(String canonical, CrawlURI item) {
        // TODO? could use in-memory exception list of currently-forgotten items
        LOGGER.severe("forget(\""+canonical+"\",CrawlURI) not supported");
    }

    protected boolean setAdd(CharSequence uri) {
        boolean added = bloom.add(uri);
        // warn if bloom has reached its expected size (and its false-pos
        // rate will now exceed the theoretical/designed level)
        if( added && (count() == bloom.getExpectedInserts())) {
            LOGGER.warning(
                "Bloom has reached expected limit "+bloom.getExpectedInserts()+
                "; false-positive rate will now rise above goal of "+
                "1-in-(2^"+bloom.getHashCount());
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
