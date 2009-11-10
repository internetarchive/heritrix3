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
package org.archive.util.fingerprint;

/**
 * Simple long fingerprint cache using a backing array; any long maps to 
 * one of 'smear' slots. Longs inserted should be randomly distributed, 
 * 
 * @author gojomo
 */
public class ArrayLongFPCache implements LongFPSet {
    public static final int DEFAULT_CAPACITY = 1 << 20; // 1 million, 8MB
    public static final int DEFAULT_SMEAR = 5; 

    long cache[] = new long[DEFAULT_CAPACITY];
    int smear = DEFAULT_SMEAR;
    int count = 0;
    
    public void setCapacity(int newCapacity) {
        long[] oldCache = cache;
        cache = new long[newCapacity];
        for(int i=0;i<oldCache.length;i++) {
            add(oldCache[i]);
        }
    }
    
    /* (non-Javadoc)
     * @see org.archive.util.fingerprint.LongFPSet#add(long)
     */
    public boolean add(long l) {
        if(contains(l)) {
            return false; 
        }
        int index = (Math.abs((int) (l % cache.length)) + (count % smear)) % cache.length;
        count++;
        cache[index]=l;
        return true;
    }

    /* (non-Javadoc)
     * @see org.archive.util.fingerprint.LongFPSet#contains(long)
     */
    public boolean contains(long l) {
        int index = Math.abs((int) (l % cache.length));
        for(int i = index; i < index + smear; i++) {
            if(cache[i%cache.length]==l) {
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.archive.util.fingerprint.LongFPSet#remove(long)
     */
    public boolean remove(long l) {
        int index = Math.abs((int) (l % cache.length));
        for(int i = index; i < index + smear; i++) {
            if(cache[i%cache.length]==l) {
                cache[i%cache.length]=0;
                count = Math.min(count,cache.length);
                count--;
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.archive.util.fingerprint.LongFPSet#count()
     */
    public long count() {
        return Math.min(count,cache.length);
    }

    /* (non-Javadoc)
     * @see org.archive.util.fingerprint.LongFPSet#quickContains(long)
     */
    public boolean quickContains(long fp) {
        return contains(fp);
    }
    
    public int cacheLength() {
        return cache.length;
    }

}
