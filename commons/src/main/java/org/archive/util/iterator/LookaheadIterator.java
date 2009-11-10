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
package org.archive.util.iterator;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Superclass for Iterators which must probe ahead to know if
 * a 'next' exists, and thus have a cached next between a call
 * to hasNext() and next().
 * 
 * @author gojomo
 *
 */
public abstract class LookaheadIterator<T> implements Iterator<T> {
    protected T next;

    /** 
     * Test whether any items remain; loads next item into
     * holding 'next' field. 
     * 
     * @see java.util.Iterator#hasNext()
     */
    public boolean hasNext() {
        return (this.next != null)? true: lookahead();
    }
    
    /**
     * Caches the next item if available.
     * 
     * @return  true if there was a next item to cache, false otherwise
     */
    protected abstract boolean lookahead();

    /** 
     * Return the next item.
     * 
     * @see java.util.Iterator#next()
     */
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        // 'next' is guaranteed non-null by a hasNext() which returned true
        T returnObj = this.next;
        this.next = null;
        return returnObj;
    }
    
    /* (non-Javadoc)
     * @see java.util.Iterator#remove()
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
