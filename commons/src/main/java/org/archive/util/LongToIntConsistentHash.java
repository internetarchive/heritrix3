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

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import st.ata.util.FPGenerator;

/**
 * Simple consistent-hashing implementation: provided a long and an
 * integer bucket-number upper-bound (exclusive), return the matching
 * integer. 
 */
public class LongToIntConsistentHash {
    TreeMap<Long,Integer> circle = new TreeMap<Long,Integer>();
    int replicasInstalledUpTo=-1; 
    int numReplicas = 32; 

    public LongToIntConsistentHash() {
        this(32); 
    }
    
    public LongToIntConsistentHash(int numReplicas) {
        this.numReplicas = numReplicas;
        installReplicas(0);
    }

    /**
     * Install necessary replicas, if not already present.
     * @param upTo
     */
    public void installReplicas(int upTo) {
        if(replicasInstalledUpTo>upTo) {
            return;
        }
        for(;replicasInstalledUpTo<upTo;replicasInstalledUpTo++) {
            for(int i = 0; i < numReplicas; i++) {
                circle.put(
                        FPGenerator.std64.fp(
                                ""+replicasInstalledUpTo+":"+i),
                        replicasInstalledUpTo);
            }
        }
    }
    
    /**
     * Return the proper integer bucket-number for the given long hash,
     * up to the given integer boundary (exclusive). 
     * 
     * @param longHash
     * @param upTo
     * @return
     */
    public int bucketFor(long longHash, int upTo) {
        installReplicas(upTo); 
        
        NavigableMap<Long, Integer> tailMap = circle.tailMap(longHash, true);
        Map.Entry<Long,Integer> match = null;
        for(Map.Entry<Long,Integer> candidate : tailMap.entrySet()) {
            if(candidate.getValue() < upTo) {
                match = candidate; 
                break;
            }
        }
        
        if (match == null) {
            return bucketFor(Long.MIN_VALUE,upTo);
        } 
        return match.getValue();
    }

    /**
     * Convenience alternative which creates longHash from CharSequence
     * 
     * @param string
     * @param upTo
     * @return
     */
    public int bucketFor(CharSequence cs, int upTo) {
        return bucketFor(FPGenerator.std64.fp(cs), upTo);
    }

    public int bucketFor(char[] chars, int upTo) {
        return bucketFor(FPGenerator.std64.fp(chars,0,chars.length), upTo);
    }
}
