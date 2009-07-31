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

import junit.framework.TestCase;

import org.apache.commons.lang.math.RandomUtils;

import st.ata.util.FPGenerator;

public class LongToIntConsistentHashTest extends TestCase {
    LongToIntConsistentHash conhash;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        conhash = new LongToIntConsistentHash();
    }

    public void testRange() {
        for(long in = 0; in < 10000; in++) {
            long longHash = FPGenerator.std64.fp(""+in);
            int upTo = RandomUtils.nextInt(32)+1;
            int bucket = conhash.bucketFor(longHash, upTo);
            assertTrue("bucket returned >= upTo",bucket < upTo);
            assertTrue("bucket returned < 0: "+bucket,bucket >= 0);

        }
    }
    
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
            assertTrue("excessive changes",Math.abs(landings[0]-landings[1]) < 2000); 
        }
    }
    
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
        assertTrue("excessive changes: "+changedCount,changedCount < 2000); 
    }
    
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
        assertTrue("excessive changes: "+changedCount,changedCount < 2000); 
    }
}
