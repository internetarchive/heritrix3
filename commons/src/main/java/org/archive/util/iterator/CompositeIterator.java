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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An iterator that's built up out of any number of other iterators.
 * @author gojomo
 */
public class CompositeIterator<E> implements Iterator<E> {
    ArrayList<Iterator<E>> iterators = new ArrayList<Iterator<E>>();
    Iterator<E> currentIterator;
    int indexOfCurrentIterator = -1;

    /**
     * Moves to the next (non empty) iterator. Returns false if there are no
     * more (non empty) iterators, true otherwise.
     * @return false if there are no more (non empty) iterators, true otherwise.
     */
    private boolean nextIterator() {
        if (++indexOfCurrentIterator < iterators.size()) {
            currentIterator = iterators.get(indexOfCurrentIterator);
            // If the new iterator was empty this will move us to the next one.
            return hasNext();
        } else {
            currentIterator = null;
            return false;
        }
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#hasNext()
     */
    public boolean hasNext() {
        if(currentIterator!=null && currentIterator.hasNext()) {
            // Got more
            return true;
        } else {
            // Have got more if we can queue up a new iterator.
            return nextIterator();
        }
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#next()
     */
    public E next() {
        if(hasNext()) {
            return currentIterator.next();
        } else {
            throw new NoSuchElementException();
        }
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#remove()
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * Create an empty CompositeIterator. Internal
     * iterators may be added later.
     */
    public CompositeIterator() {
        super();
    }

    /**
     * Convenience method for concatenating together
     * two iterators.
     * @param i1
     * @param i2
     */
    public CompositeIterator(Iterator<E> i1, Iterator<E> i2) {
        this();
        add(i1);
        add(i2);
    }

    /**
     * Add an iterator to the internal chain.
     *
     * @param i an iterator to add.
     */
    public void add(Iterator<E> i) {
        iterators.add(i);
    }

}
