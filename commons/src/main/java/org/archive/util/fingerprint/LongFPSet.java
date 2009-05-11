/* LongSet
 *
 * $Id$
 *
 * Created on Oct 19, 2003
 *
 * Copyright (C) 2003 Internet Archive.
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
