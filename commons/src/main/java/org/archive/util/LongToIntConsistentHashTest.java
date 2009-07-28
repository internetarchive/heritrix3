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
        assertTrue("excessive changes",changedCount < 2000); 
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
        assertTrue("excessive changes",changedCount < 2000); 
    }
}
