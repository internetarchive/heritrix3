/* TransformingIteratorWrapper
*
* $Id$
*
* Created on Mar 25, 2005
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
