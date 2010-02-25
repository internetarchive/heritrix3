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
    protected static final int DEFAULT_REPLICAS = 128;
    TreeMap<Long,Integer> circle = new TreeMap<Long,Integer>();
    int replicasInstalledUpTo=-1; 
    int numReplicas; 

    public LongToIntConsistentHash() {
        this(DEFAULT_REPLICAS); 
    }
    
    public LongToIntConsistentHash(int numReplicas) {
        this.numReplicas = numReplicas;
        installReplicas(0);
        replicasInstalledUpTo=1;
    }

    /**
     * Install necessary replicas, if not already present.
     * @param upTo
     */
    public void installReplicasUpTo(int upTo) {
        if(replicasInstalledUpTo>upTo) {
            return;
        }
        for(;replicasInstalledUpTo<upTo;replicasInstalledUpTo++) {
            installReplicas(replicasInstalledUpTo);
        }
    }

    private void installReplicas(int bucket) {
        for(int i = 0; i < numReplicas; i++) {
            circle.put(
                    replicaLocation(bucket,i),
                    bucket);
        }
    }
    
//    SecureRandom rand = new SecureRandom(); 
    protected long replicaLocation(int bucketNumber, int replicaNumber) {
//      return rand.nextLong();
//      return RandomUtils.nextLong();
//      return ArchiveUtils.doubleMurmur(string.getBytes());
//      return (new JenkinsHash()).hash(string.getBytes());
       return FPGenerator.std64.fp(bucketNumber+"."+replicaNumber);
    }
    
    protected long hash(CharSequence cs) {
//      return ArchiveUtils.doubleMurmur(string.getBytes());
//      return (new JenkinsHash()).hash(string.getBytes());
       return FPGenerator.std64.fp(cs);
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
        installReplicasUpTo(upTo); 
        
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
        return bucketFor(hash(cs), upTo);
    }

    public int bucketFor(char[] chars, int upTo) {
        return bucketFor(hash(new String(chars)), upTo);
    }
}
