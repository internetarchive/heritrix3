/* BloomFilter
*
* $Id$
*
* Created on Jun 21, 2005
*
* Copyright (C) 2005 Internet Archive; a slight adaptation of
* LGPL work (C) Sebastiano Vigna
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
*/

package org.archive.util;

import java.io.Serializable;
import java.security.SecureRandom;
import java.util.Random;

/** A Bloom filter.
 *
 * ADAPTED/IMPROVED VERSION OF MG4J it.unimi.dsi.mg4j.util.BloomFilter
 * 
 * <p>KEY CHANGES:
 *
 * <ul>
 * <li>NUMBER_OF_WEIGHTS is 2083, to better avoid collisions between 
 * similar strings (common in the domain of URIs)</li>
 * 
 * <li>Removed dependence on cern.colt MersenneTwister (replaced with
 * SecureRandom) and QuickBitVector (replaced with local methods).</li>
 * 
 * <li>Adapted to allow long bit indices</li>
 * 
 * <li>Stores bitfield in an array of up to 2^22 arrays of 2^26 longs. Thus, 
 * bitfield may grow to 2^48 longs in size -- 2PiB, 2*54 bitfield indexes.
 * (I expect this will outstrip available RAM for the next few years.)</li>
 * </ul>
 * 
 * <hr>
 * 
 * <P>Instances of this class represent a set of character sequences (with 
 * false positives) using a Bloom filter. Because of the way Bloom filters work,
 * you cannot remove elements.
 *
 * <P>Bloom filters have an expected error rate, depending on the number
 * of hash functions used, on the filter size and on the number of elements in 
 * the filter. This implementation uses a variable optimal number of hash 
 * functions, depending on the expected number of elements. More precisely, a 
 * Bloom filter for <var>n</var> character sequences with <var>d</var> hash 
 * functions will use ln 2 <var>d</var><var>n</var> &#8776; 
 * 1.44 <var>d</var><var>n</var> bits; false positives will happen with 
 * probability 2<sup>-<var>d</var></sup>.
 *
 * <P>Hash functions are generated at creation time using universal hashing. 
 * Each hash function uses {@link #NUMBER_OF_WEIGHTS} random integers, which 
 * are cyclically multiplied by the character codes in a character sequence. 
 * The resulting integers are XOR-ed together.
 *
 * <P>This class exports access methods that are very similar to those of 
 * {@link java.util.Set}, but it does not implement that interface, as too 
 * many non-optional methods would be unimplementable (e.g., iterators).
 *
 * @author Sebastiano Vigna
 * @contributor Gordon Mohr
 */
public class BloomFilter64bit implements Serializable, BloomFilter {
    private static final long serialVersionUID = 2L;

    /** The number of weights used to create hash functions. */
    protected final static int NUMBER_OF_WEIGHTS = 2083; // CHANGED FROM 16
    /** The number of bits in this filter. */
    final protected long m;
    /** if bitfield is an exact power of 2 in length, it is this power */ 
    protected int power = -1; 
    /** The expected number of inserts; determines calculated size */ 
    final protected long expectedInserts; 
    /** The number of hash functions used by this filter. */
    final protected int d;
    /** The underlying bit vector */
    final protected long[][] bits;
    /** The random integers used to generate the hash functions. */
    final protected long[][] weight;

    /** The number of elements currently in the filter. It may be
     * smaller than the actual number of additions of distinct character
     * sequences because of false positives.
     */
    protected int size;

    /** The natural logarithm of 2, used in the computation of the number of bits. */
    protected final static double NATURAL_LOG_OF_2 = Math.log( 2 );

    /** power-of-two to use as maximum size of bitfield subarrays */
    protected final static int SUBARRAY_POWER_OF_TWO = 26; // 512MiB of longs
    /** number of longs in one subarray */
    protected final static int SUBARRAY_LENGTH_IN_LONGS = 1 << SUBARRAY_POWER_OF_TWO; 
    /** mask for lowest SUBARRAY_POWER_OF_TWO bits */
    protected final static int SUBARRAY_MASK = SUBARRAY_LENGTH_IN_LONGS - 1; //0x0FFFFFFF

    protected final static boolean DEBUG = false;

    /** Creates a new Bloom filter with given number of hash functions and 
     * expected number of elements.
     *
     * @param n the expected number of elements.
     * @param d the number of hash functions; if the filter add not more 
     * than <code>n</code> elements, false positives will happen with 
     * probability 2<sup>-<var>d</var></sup>.
     */
    public BloomFilter64bit( final long n, final int d) {
        this(n,d, new SecureRandom(), false);
    }
    
    public BloomFilter64bit( final long n, final int d, boolean roundUp) {
        this(n,d, new SecureRandom(), roundUp);
    }
    
    /** Creates a new Bloom filter with given number of hash functions and 
     * expected number of elements.
     *
     * @param n the expected number of elements.
     * @param d the number of hash functions; if the filter add not more 
     * than <code>n</code> elements, false positives will happen with 
     * probability 2<sup>-<var>d</var></sup>.
     * @param Random weightsGenerator may provide a seeded Random for reproducible
     * internal universal hash function weighting
     * @param roundUp if true, round bit size up to next-nearest-power-of-2
     */
    public BloomFilter64bit( final long n, final int d, Random weightsGenerator, boolean roundUp ) {
        this.expectedInserts = n; 
        this.d = d;
        long lenInLongs = (long)Math.ceil( ( (long)n * (long)d / NATURAL_LOG_OF_2 ) / 64L );
        if ( lenInLongs > (1L<<48) ) {
            throw new IllegalArgumentException(
                    "This filter would require " + lenInLongs + " longs, " +
                    "greater than this classes maximum of 2^48 longs (2PiB)." );
        }
        long lenInBits = lenInLongs * 64L;
        
        if(roundUp) {
            int pow = 0;
            while((1L<<pow) < lenInBits) {
                pow++;
            }
            this.power = pow;
            this.m = 1L<<pow;
            lenInLongs = m/64L;
        } else {
            this.m = lenInBits;
        }

        
        int arrayOfArraysLength = (int)((lenInLongs+SUBARRAY_LENGTH_IN_LONGS-1)/SUBARRAY_LENGTH_IN_LONGS);
        bits = new long[ (int)(arrayOfArraysLength) ][];
        // ensure last subarray is no longer than necessary
        long lenInLongsRemaining = lenInLongs; 
        for(int i = 0; i < bits.length; i++) {
            bits[i] = new long[(int)Math.min(lenInLongsRemaining,SUBARRAY_LENGTH_IN_LONGS)];
            lenInLongsRemaining -= bits[i].length;
        }

        if ( DEBUG ) System.err.println( "Number of bits: " + m );

        weight = new long[ d ][];
        for( int i = 0; i < d; i++ ) {
            weight[ i ] = new long[ NUMBER_OF_WEIGHTS ];
            for( int j = 0; j < NUMBER_OF_WEIGHTS; j++ )
                 weight[ i ][ j ] = weightsGenerator.nextLong();
        }
    }

    /** The number of character sequences in the filter.
     *
     * @return the number of character sequences in the filter (but 
     * see {@link #contains(CharSequence)}).
     */

    public int size() {
        return size;
    }

    /** Hashes the given sequence with the given hash function.
     *
     * @param s a character sequence.
     * @param l the length of <code>s</code>.
     * @param k a hash function index (smaller than {@link #d}).
     * @return the position in the filter corresponding to <code>s</code> for the hash function <code>k</code>.
     */
    protected long hash( final CharSequence s, final int l, final int k ) {
        final long[] w = weight[ k ];
        long h = 0;
        int i = l;
        while( i-- != 0 ) h ^= s.charAt( i ) * w[ i % NUMBER_OF_WEIGHTS ];
        long retVal; 
        if(power>0) {
            retVal =  h >>> (64-power); 
        } else { 
            //                ####----####----
            retVal =  ( h & 0x7FFFFFFFFFFFFFFFL ) % m;
        }
        return retVal; 
    }
    
    public long[] bitIndexesFor(CharSequence s) {
        long[] ret = new long[d];
        for(int i = 0; i < d; i++) {
             ret[i] = hash(s,s.length(),i); 
        }
        return ret;
    }
    
    /** Checks whether the given character sequence is in this filter.
     *
     * <P>Note that this method may return true on a character sequence that is has
     * not been added to the filter. This will happen with probability 2<sub>-<var>d</var></sub>,
     * where <var>d</var> is the number of hash functions specified at creation time, if
     * the number of the elements in the filter is less than <var>n</var>, the number
     * of expected elements specified at creation time.
     *
     * @param s a character sequence.
     * @return true if the sequence is in the filter (or if a sequence with the
     * same hash sequence is in the filter).
     */

    public boolean contains( final CharSequence s ) {
        int i = d, l = s.length();
        while( i-- != 0 ) if ( ! getBit( hash( s, l, i ) ) ) return false;
        return true;
    }

    /** Adds a character sequence to the filter.
     *
     * @param s a character sequence.
     * @return true if the character sequence was not in the filter (but see {@link #contains(CharSequence)}).
     */

    public boolean add( final CharSequence s ) {
        boolean result = false;
        int i = d, l = s.length();
        long h;
        while( i-- != 0 ) {
            h = hash( s, l, i );
            if ( ! setGetBit( h ) ) {
                result = true;
            }
        }
        if ( result ) size++;
        return result;
    }
    
    protected final static long ADDRESS_BITS_PER_UNIT = 6; // 64=2^6
    protected final static long BIT_INDEX_MASK = (1<<6)-1; // = 63 = 2^BITS_PER_UNIT - 1;

    /**
     * Returns from the local bitvector the value of the bit with 
     * the specified index. The value is <tt>true</tt> if the bit 
     * with the index <tt>bitIndex</tt> is currently set; otherwise, 
     * returns <tt>false</tt>.
     *
     * (adapted from cern.colt.bitvector.QuickBitVector)
     * 
     * @param     bitIndex   the bit index.
     * @return    the value of the bit with the specified index.
     */
    public boolean getBit(long bitIndex) {
        long longIndex = bitIndex >>> ADDRESS_BITS_PER_UNIT;
        int arrayIndex = (int) (longIndex >>> SUBARRAY_POWER_OF_TWO); 
        int subarrayIndex = (int) (longIndex & SUBARRAY_MASK); 
        return ((bits[arrayIndex][subarrayIndex] & (1L << (bitIndex & BIT_INDEX_MASK))) != 0);
    }

    /**
     * Changes the bit with index <tt>bitIndex</tt> in local bitvector.
     *
     * (adapted from cern.colt.bitvector.QuickBitVector)
     * 
     * @param     bitIndex   the index of the bit to be set.
     */
    protected void setBit( long bitIndex) {
        long longIndex = bitIndex >>> ADDRESS_BITS_PER_UNIT;
        int arrayIndex = (int) (longIndex >>> SUBARRAY_POWER_OF_TWO); 
        int subarrayIndex = (int) (longIndex & SUBARRAY_MASK); 
        bits[arrayIndex][subarrayIndex] |= (1L << (bitIndex & BIT_INDEX_MASK));
    }
    
    /**
     * Sets the bit with index <tt>bitIndex</tt> in local bitvector -- 
     * returning the old value. 
     *
     * (adapted from cern.colt.bitvector.QuickBitVector)
     * 
     * @param     bitIndex   the index of the bit to be set.
     */
    protected boolean setGetBit( long bitIndex) {
        long longIndex = bitIndex >>> ADDRESS_BITS_PER_UNIT;
        int arrayIndex = (int) (longIndex >>> SUBARRAY_POWER_OF_TWO); 
        int subarrayIndex = (int) (longIndex & SUBARRAY_MASK); 
        long mask = 1L << (bitIndex & BIT_INDEX_MASK);
        boolean ret = (bits[arrayIndex][subarrayIndex] & mask)!=0;
        bits[arrayIndex][subarrayIndex] |= mask;
        return ret; 
    }
    
	/* (non-Javadoc)
	 * @see org.archive.util.BloomFilter#getSizeBytes()
	 */
	public long getSizeBytes() {
	    // account for ragged-sized last array
	    return 8*(((bits.length-1)*bits[0].length)+bits[bits.length-1].length);
	}

    @Override
    public long getExpectedInserts() {
        return expectedInserts;
    }

    @Override
    public long getHashCount() {
        return d;
    }
}
