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
 * Set for holding primitive long fingerprints.
 *
 * @author Gordon Mohr
 */
public interface LongFPSet {
    /**
     * Add a fingerprint to the set.  Note that subclasses can implement
     * different policies on how to add - some might grow the available space,
     * others might implement some type of LRU caching.
     *
     * In particular, you cannot on the {@link #count()} method returning
     * 1 greater than before the addition.
     *
     * @param l the fingerprint to add
     * @return <code>true</code> if set has changed with this addition
     */
    boolean add(long l);

    /**
     *  Does this set contain a given fingerprint.
     * @param l the fingerprint to check for
     * @return <code>true</code> if the fingerprint is in the set
     */
    boolean contains(long l);

    /**
     *  Remove a fingerprint from the set, if it is there
     * @param l the fingerprint to remove
     * @return <code>true</code> if we removed the fingerprint
     */
    boolean remove(long l);

    /** get the number of elements in the Set
     * @return the number of elements in the Set
     */
    long count();

    /**
     * Do a contains() check that doesn't require laggy
     * activity (eg disk IO). If this returns true,
     * fp is definitely contained; if this returns
     * false, fp  *MAY* still be contained -- must use
     * full-cost contains() to be sure.
     *
     * @param fp the fingerprint to check for
     * @return <code>true</code> if contains the fingerprint
     */
    boolean quickContains(long fp);
}
