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
