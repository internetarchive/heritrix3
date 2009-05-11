/* Copyright (C) 2003 Internet Archive.
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
 *
 * LongFPSetCache.java
 * Created on Oct 21, 2003
 *
 * $Header$
 */
package org.archive.util.fingerprint;



/**
 * Like a MemLongFPSet, but with fixed capacity and maximum size.
 * When an add would expand past the maximum size, an old entry
 * is deleted via a clock/counter algorithm.
 *
 * @author gojomo
 *
 */
public class LongFPSetCache extends MemLongFPSet {
    
    private static final long serialVersionUID = -5307436423975825566L;

    long sweepHand = 0;

    public LongFPSetCache() {
        super();
    }

    public LongFPSetCache(int capacityPowerOfTwo, float loadFactor) {
        super(capacityPowerOfTwo, loadFactor);
    }

    protected void noteAccess(long index) {
        if(slots[(int)index]<Byte.MAX_VALUE) {
            slots[(int)index]++;
        }
    }

    protected void makeSpace() {
        discard(1);
    }

    private void discard(int i) {
        int toDiscard = i;
        while(toDiscard>0) {
            if(slots[(int)sweepHand]==0) {
                removeAt(sweepHand);
                toDiscard--;
            } else {
                if (slots[(int)sweepHand]>0) {
                    slots[(int)sweepHand]--;
                }
            }
            sweepHand++;
            if (sweepHand==slots.length) {
                sweepHand = 0;
            }
        }
    }
}
