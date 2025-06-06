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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


/**
 * BloomFilter tests. 
 * 
 * @author gojomo
 * @version $Date: 2009-11-19 14:39:53 -0800 (Thu, 19 Nov 2009) $, $Revision: 6674 $
 */
public abstract class BloomFilterTest {
    
    abstract BloomFilter createBloom(long n, int d, Random random);

    protected void trialWithParameters(long targetSize, int hashCount, long addCount, long containsCount) {
        BloomFilter bloom = createBloom(targetSize,hashCount,new Random(1996L));
        
        int addFalsePositives = checkAdds(bloom,addCount);
        checkDistribution(bloom); 
        // this is a *very* rough and *very* lenient upper bound for adds <= targetSize
        long maxTolerableDuringAdds = addCount / (1<<hashCount);
        assertTrue(
                addFalsePositives<10,
                "excessive false positives ("+addFalsePositives+">"+maxTolerableDuringAdds+") during adds");
        
        if(containsCount==0) {
            return; 
        }
        int containsFalsePositives = checkContains(bloom,containsCount); 
        // expect at least 0 if bloom wasn't saturated in add phase
        // if was saturated, expect at least 1/4th of the theoretical 1-in-every-(2<<hashCount) 
        long minTolerableDuringContains = (addCount < targetSize) ? 0 : containsCount / ((1<<hashCount) * 4);
        // expect no more than 4 times the theoretical-at-saturation
        long maxTolerableDuringContains = containsCount * 4 / (1<<hashCount);
        assertTrue(
                containsFalsePositives<=maxTolerableDuringContains,
                "excessive false positives ("+containsFalsePositives+">"+maxTolerableDuringContains+") during contains"); // no more than double expected 1-in-4mil
        assertTrue(
                containsFalsePositives>=minTolerableDuringContains,
                "missing false positives ("+containsFalsePositives+"<"+minTolerableDuringContains+") during contains");  // should be at least a couple
    }
                
    /**
     * Test very-large (almost 800MB, spanning more than Integer.MAX_VALUE bit 
     * indexes) bloom at saturation for expected behavior and level of 
     * false-positives. 
     * 
     * Renamed to non-'test' name so not automatically run, because can 
     * take 15+ minutes to complete.
     */
    @Test
    @Disabled
    public void xestOversized() {
        trialWithParameters(200000000,22,200000000,32000000);
    }
    
    /**
     * Test large (495MB), default-sized bloom at saturation for 
     * expected behavior and level of false-positives. 
     * 
     * Renamed to non-'test' name so not automatically run, because can 
     * take 15+ minutes to complete.
     */
    @Test
    @Disabled
    public void xestDefaultFull() {
        trialWithParameters(125000000,22,125000000,34000000);
    }

    @Test
    @EnabledIfSystemProperty(named = "runSlowTests", matches = "true", disabledReason = "it takes about 25 seconds")
    public void testDefaultAbbreviated() {
        trialWithParameters(125000000,22,17000000,0);
    }

    @Test
    @EnabledIfSystemProperty(named = "runSlowTests", matches = "true", disabledReason = "it takes about 20 seconds")
    public void testSmall() {
        trialWithParameters(10000000, 20, 10000000, 10000000);
    }
    
    /**
     * Check that the given filter behaves properly as a large number of
     * constructed unique strings are added: responding positively to 
     * contains, and negatively to redundant adds. Assuming that the filter
     * was empty before it was called, any add()s that report the string was
     * already present are false-positives; report the total of same so the
     * caller can evaluate if that level was suspiciously out of the expected
     * error rate. 
     * 
     * @param bloom BloomFilter to check
     * @param count int number of unique strings to check
     * @return
     */
    protected int checkAdds(BloomFilter bloom, long count) {
        int falsePositives = 0; 
        for(int i = 0; i < count; i++) {
            String str = "add"+Integer.toString(i);
            if(!bloom.add(str)) {
                falsePositives++;
            }
            assertTrue(bloom.contains(str));
            assertFalse(bloom.add(str),str+" not present on re-add");
        }
        return falsePositives;
    }
    
    /**
     * Check if the given filter contains any of the given constructed
     * strings. Since the previously-added strings (of checkAdds) were
     * different from these, *any* positive contains results are 
     * false-positives. Return the total count so that the calling method
     * can determine if the false-positive rate is outside the expected 
     * range. 
     * 
     * @param bloom BloomFilter to check
     * @param count int number of unique strings to check
     * @return
     */
    protected int checkContains(BloomFilter bloom, long count) {
        int falsePositives = 0; 
        for(int i = 0; i < count; i++) {
            String str = "contains"+Integer.toString(i);
            if(bloom.contains(str)) {
                falsePositives++;
            }
        }
        return falsePositives;
    }
    
    /**
     * Check that the given bloom filter, assumed to have already had a 
     * significant number of items added, has bits set in the lower and upper
     * 10% of its bit field. 
     * 
     * (This would have caught previous int/long bugs in the filter hashing 
     * or conversion of bit indexes into array indexes and bit masks.)
     * 
     * @param bloom BloomFilter to check
     */
    public void checkDistribution(BloomFilter bloom) {
        long bitLength = bloom.getSizeBytes() * 8L;
        for(long i = 0; i<bitLength; i++) {
            // verify that first set bit is in first 20% of bitfield
            if(bloom.getBit(i)) {
                assertTrue((i/(double)bitLength)<0.1d,"set bits not as expected in early positions");
                break; 
            }
        }
        for(long i = bitLength-1; i>=0; i--) {
            // verify that first set bit is in first 20% of bitfield
            if(bloom.getBit(i)) {
                assertTrue((i/(double)bitLength)>0.1d,"set bits not as expected in late positions");
                break; 
            }
        }
    }
}
