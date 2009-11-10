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

import java.io.Serializable;
import java.util.logging.Logger;

import org.archive.util.AbstractLongFPSet;

/**
 * Open-addressing in-memory hash set for holding primitive long fingerprints.
 *
 * @author Gordon Mohr
 */
public class MemLongFPSet extends AbstractLongFPSet
implements LongFPSet, Serializable {
    
    
    private static final long serialVersionUID = -4301879539092625698L;


    private static Logger logger =
        Logger.getLogger(MemLongFPSet.class.getName());
    private static final int DEFAULT_CAPACITY_POWER_OF_TWO = 10;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    protected byte[] slots;
    protected long[] values;

    public MemLongFPSet() {
        this(DEFAULT_CAPACITY_POWER_OF_TWO, DEFAULT_LOAD_FACTOR);
    }

    /**
     * @param capacityPowerOfTwo The capacity as the exponent of a power of 2.
     *  e.g if the capacity is <code>4</code> this means <code>2^^4</code>
     * entries.
     * @param loadFactor The load factor as a fraction.  This gives the amount
     * of free space to keep in the Set.
     */
    public MemLongFPSet(int capacityPowerOfTwo, float loadFactor) {
        super(capacityPowerOfTwo, loadFactor);
        slots = new byte[1 << capacityPowerOfTwo];
        for(int i = 0; i < (1 << capacityPowerOfTwo); i++) {
            slots[i] = EMPTY; // flag value for unused
        }
        values = new long[1 << capacityPowerOfTwo];
    }

    protected void setAt(long i, long val) {
        slots[(int)i] = 1;
        values[(int)i] = val;
    }

    protected long getAt(long i) {
        return values[(int)i];
    }

    protected void makeSpace() {
        grow();
    }

    private void grow() {
        // Catastrophic event.  Log its occurance.
        logger.info("Doubling fingerprinting slots to "
            + (1 << this.capacityPowerOfTwo));
        long[] oldValues = values;
        byte[] oldSlots = slots;
        capacityPowerOfTwo++;
        values = new long[1 << capacityPowerOfTwo];
        slots = new byte[1 << capacityPowerOfTwo];
        for(int i = 0; i < (1 << capacityPowerOfTwo); i++) {
            slots[i]=EMPTY; // flag value for unused
        }
        count=0;
        for(int i = 0; i< oldValues.length; i++) {
            if(oldSlots[i]>=0) {
                add(oldValues[i]);
            }
        }
    }

    protected void relocate(long val, long oldIndex, long newIndex) {
        values[(int)newIndex] = values[(int)oldIndex];
        slots[(int)newIndex] = slots[(int)oldIndex];
        slots[(int)oldIndex] = EMPTY;
    }

    protected int getSlotState(long i) {
        return slots[(int)i];
    }

    protected void clearAt(long index) {
        slots[(int)index]=EMPTY;
        values[(int)index]=0;
    }

    public boolean quickContains(long fp) {
        return contains(fp);
    }
}