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
package org.archive.util;

import java.io.Serializable;
import java.util.logging.Logger;

import org.archive.util.fingerprint.LongFPSet;

/**
 * Shell of functionality for a Set of primitive long fingerprints, held
 * in an array of possibly-empty slots.
 * 
 * The implementation of that holding array is delegated to subclasses.
 *
 * <p>Capacity is always a power of 2.
 *
 * <p>Fingerprints are already assumed to be well-distributed, so the
 * hashed position for a value is just its high-order bits.
 *
 * @author gojomo
 * @version $Date$, $Revision$
 */
public abstract class AbstractLongFPSet implements LongFPSet, Serializable {
    private static Logger logger =
        Logger.getLogger("org.archive.util.AbstractLongFPSet");

    /**
     * A constant used to indicate that a slot in the set storage is empty.
     * A zero or positive value means slot is filled
     */
    protected static byte EMPTY = -1;

    /** the capacity of this set, specified as the exponent of a power of 2 */
    protected int capacityPowerOfTwo;

    /** The load factor, as a fraction.  This gives the amount of free space
     * to keep in the Set. */
    protected float loadFactor;

    /** The current number of elements in the set */
    protected long count;

    /**
     * To support serialization
     * TODO: verify needed?
     */
    public AbstractLongFPSet() {
        super();
    }
    
    /**
     * Create a new AbstractLongFPSet with a given capacity and load Factor
     *
     * @param capacityPowerOfTwo The capacity as the exponent of a power of 2.
     *  e.g if the capacity is <code>4</code> this means <code>2^^4</code>
     * entries
     * @param loadFactor The load factor as a fraction.  This gives the amount
     * of free space to keep in the Set.
     */
    public AbstractLongFPSet(final int capacityPowerOfTwo, float loadFactor) {
        this.capacityPowerOfTwo = capacityPowerOfTwo;
        this.loadFactor = loadFactor;
        this.count = 0;
    }

    /**
     * Does this set contain the given value?
     *
     * @see org.archive.util.fingerprint.LongFPSet#contains(long)
     */
    public boolean contains(long val) {
        long i = indexFor(val);
        if (slotHasData(i)) {
            noteAccess(i);
            return true;
        }
        return false;
    }

    /**
     * Check the state of a slot in the storage.
     *
     * @param i the index of the slot to check
     * @return -1 if slot is filled; nonegative if full.
     */
    protected abstract int getSlotState(long i);

    /**
     * Note access (hook for subclass cache-replacement strategies)
     *
     * @param index The index of the slot to check.
     */
    private void noteAccess(long index) {
        // by default do nothing
        // cache subclasses may use to update access counts, etc.
    }

    /**
     * Return the number of entries in this set.
     *
     * @see org.archive.util.fingerprint.LongFPSet#count()
     */
    public long count() {
        return count;
    }

    /**
     * Add the given value to this set
     *
     * @see org.archive.util.fingerprint.LongFPSet#add(long)
     */
    public boolean add(long val) {
        logger.finest("Adding " + val);
        long i = indexFor(val);
        if (slotHasData(i)) {
            // positive index indicates already in set
            return false;
        }
        // we have a possible slot now, which is encoded as a negative number

        // check for space, and grow if needed
        if ((count + 1) > (loadFactor * (1 << capacityPowerOfTwo))) {
            makeSpace();
            // find new i
            i = indexFor(val);
            assert i < 0 : "slot should be empty";
        }

        i = asDataSlot(i); // convert to positive index
        setAt(i, val);
        count++;
        noteAccess(i);
        return true;
    }

    /**
     * Make additional space to keep the load under the target
     * loadFactor level.
     * 
     * Subclasses may grow or discard entries to satisfy.
     */
    protected abstract void makeSpace();

    /**
     * Set the stored value at the given slot.
     *
     * @param i the slot index
     * @param l the value to set
     */
    protected abstract void setAt(long i, long l);

    /** 
     * Get the stored value at the given slot.
     *
     * @param i the slot index
     * @return The stored value at the given slot.
     */
    protected abstract long getAt(long i);

    /** 
     * Given a value, check the store for its existence. If it exists, it
     * will return the index where the value resides.  Otherwise it return
     * an encoded index, which is a possible storage location for the value.
     *
     * <p>Note, if we have a loading factor less than 1.0, there should always
     * be an empty location where we can store the value
     *
     * @param val the fingerprint value to check for
     * @return The (positive) index where the value already resides,
     * or an empty index where it could be inserted (encoded as a
     * negative number).
     */
    private long indexFor(long val) {
        long candidateIndex = startIndexFor(val);
        while (true) {
            if (getSlotState(candidateIndex) < 0) {
                // slot empty; return negative number encoding index
                return asEmptySlot(candidateIndex);
            }
            if (getAt(candidateIndex) == val) {
                // already present; return positive index
                return candidateIndex;
            }
            candidateIndex++;
            if (candidateIndex == 1 << capacityPowerOfTwo) {
                candidateIndex = 0; // wraparound
            }
        }
    }

    /**
     * Return the recommended storage index for the given value.
     * Assumes values are already well-distributed; merely uses
     * high-order bits.
     *
     * @param val
     * @return The recommended storage index for the given value.
     */
    private long startIndexFor(long val) {
        return (val >>> (64 - capacityPowerOfTwo));
    }

    public boolean remove(long l) {
        long i = indexFor(l);
        if (!slotHasData(i)) {
            // not present, not changed
            return false;
        }
        removeAt(i);
        return true;
    }

    /**
     * Remove the value at the given index, relocating its
     * successors as necessary.
     *
     *  @param index
     */
    protected void removeAt(long index) {
        count--;
        clearAt(index);
        long probeIndex = index + 1;
        while (true) {
            if (probeIndex == 1 << capacityPowerOfTwo) {
                probeIndex = 0; //wraparound
            }
            if (getSlotState(probeIndex) < 0) {
                // vacant
                break;
            }
            long val = getAt(probeIndex);
            long newIndex = indexFor(val);
            if (newIndex != probeIndex) {
                // value must shift down
                newIndex = asDataSlot(newIndex); // positivize
                relocate(val, probeIndex, newIndex);
            }
            probeIndex++;
        }
    }

    protected abstract void clearAt(long index);

    protected abstract void relocate(long value, long fromIndex, long toIndex);

    /**
     * Low-cost, non-definitive (except when true) contains
     * test. Default answer of false is acceptable.
     *
     * @see org.archive.util.fingerprint.LongFPSet#quickContains(long)
     */
    public boolean quickContains(long fp) {
        return false;
    }

    /**
     * given a slot index, which could or could not be empty, return it as
     * a slot index indicating an non-empty slot
     *
     * @param index the slot index to convert
     * @return the index, converted to represent an slot with data
     */
    private long asDataSlot(final long index) {
        if (slotHasData(index)) { // slot already has data
            return index;
        }
        return - (index + 1);
    }

    /** 
     * Given a slot index, which could or could not be empty, return it as
     * a slot index indicating an empty slot
     * @param index the slot index to convert
     * @return the index, converted to represent an empty slot
     */
    private long asEmptySlot(final long index) {
        if (!slotHasData(index)) { // already empty slot
            return index;
        }
        return -index - 1;
    }

    /** 
     * Does this index represent a slot with data?
     *
     * @param index the index to check
     * @return <code>true</code> if the slot has data
     */
    private boolean slotHasData(final long index) {
        return index >= 0;
    }
}