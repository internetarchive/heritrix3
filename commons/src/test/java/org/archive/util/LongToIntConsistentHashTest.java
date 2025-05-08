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

import org.apache.commons.lang.math.RandomUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import st.ata.util.FPGenerator;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LongToIntConsistentHashTest {
    protected LongToIntConsistentHash conhash;
    
    @BeforeEach
    protected void setUp() throws Exception {
        conhash = new LongToIntConsistentHash();
    }

    @Test
    public void testRange() {
        for(long in = 0; in < 10000; in++) {
            long longHash = FPGenerator.std64.fp(""+in);
            int upTo = RandomUtils.nextInt(32)+1;
            int bucket = conhash.bucketFor(longHash, upTo);
            assertTrue(bucket < upTo, "bucket returned >= upTo");
            assertTrue(bucket >= 0, "bucket returned < 0: "+bucket);

        }
    }

    @Test
    public void testTwoWayDistribution() {
//        SecureRandom rand = new SecureRandom("foobar".getBytes()); 
        for(int p = 0; p < 20; p++) {
            int[] landings = new int[2];
            for(long in = 0; in < 100000; in++) {
                long longHash = FPGenerator.std64.fp(p+"a"+in);
//                long longHash = rand.nextLong();
//                long longHash = ArchiveUtils.doubleMurmur((p+":"+in).getBytes());
                landings[conhash.bucketFor(longHash, 2)]++;
            }
//            System.out.println(landings[0]+","+landings[1]);
            assertTrue(Math.abs(landings[0]-landings[1]) < 2000, "excessive changes");
        }
    }

    @Test
    public void testConsistencyUp() {
        int initialUpTo = 10;
        int changedCount = 0;
        for(long in = 0; in < 10000; in++) {
            long longHash = FPGenerator.std64.fp(""+in);
            int firstBucket = conhash.bucketFor(longHash, initialUpTo);
            int secondBucket = conhash.bucketFor(longHash, initialUpTo+1);
            if(secondBucket!=firstBucket) {
                changedCount++;
            }
        }
        assertTrue(changedCount < 2000, "excessive changes: " + changedCount);
    }

    @Test
    public void testConsistencyDown() {
        int initialUpTo = 10;
        int changedCount = 0;
        for(long in = 0; in < 10000; in++) {
            long longHash = FPGenerator.std64.fp(""+in);
            int firstBucket = conhash.bucketFor(longHash, initialUpTo);
            int secondBucket = conhash.bucketFor(longHash, initialUpTo-1);
            if(secondBucket!=firstBucket) {
                changedCount++;
            }
        }
        assertTrue(changedCount < 2000, "excessive changes: " + changedCount);
    }
}
