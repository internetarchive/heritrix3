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

/** A Bloom filter.
 *
 * SLIGHTLY ADAPTED VERSION OF MG4J it.unimi.dsi.mg4j.util.BloomFilter
 * 
 * <p>KEY CHANGES:
 *
 * <ul>
 * <li>Adapted to use 32bit ops as much as possible... may be slightly
 * faster on 32bit hardware/OS</li>
 * <li>Changed to use bitfield that is a power-of-two in size, allowing
 * hash() to use bitshifting rather than modulus... may be slightly
 * faster</li>
 * <li>NUMBER_OF_WEIGHTS is 2083, to better avoid collisions between 
 * similar strings</li>
 * <li>Removed dependence on cern.colt MersenneTwister (replaced with
 * SecureRandom) and QuickBitVector (replaced with local methods).</li>
 * </ul>
 * 
 * <hr>
 * 
 * <P>Instances of this class represent a set of character sequences (with false positives)
 * using a Bloom filter. Because of the way Bloom filters work,
 * you cannot remove elements.
 *
 * <P>Bloom filters have an expected error rate, depending on the number
 * of hash functions used, on the filter size and on the number of elements in the filter. This implementation
 * uses a variable optimal number of hash functions, depending on the expected
 * number of elements. More precisely, a Bloom
 * filter for <var>n</var> character sequences with <var>d</var> hash functions will use
 * ln 2 <var>d</var><var>n</var> &#8776; 1.44 <var>d</var><var>n</var> bits;
 * false positives will happen with probability 2<sup>-<var>d</var></sup>.
 *
 * <P>Hash functions are generated at creation time using universal hashing. Each hash function
 * uses {@link #NUMBER_OF_WEIGHTS} random integers, which are cyclically multiplied by
 * the character codes in a character sequence. The resulting integers are XOR-ed together.
 *
 * <P>This class exports access methods that are very similar to those of {@link java.util.Set},
 * but it does not implement that interface, as too many non-optional methods
 * would be unimplementable (e.g., iterators).
 *
 * @author Sebastiano Vigna
 */
public class BloomFilter32bp2Split implements Serializable, BloomFilter {

    private static final long serialVersionUID = -1504889954381695129L;
    
    /** The number of weights used to create hash functions. */
    final public static int NUMBER_OF_WEIGHTS = 2083; // CHANGED FROM 16
    /** The number of bits in this filter. */
    final public long m; 
    /** the power-of-two that m is */
    final public long power; // 1<<power == m
    /** The number of hash functions used by this filter. */
    final public int d;
    /** The underlying bit vectorS. */
    final private int[][] bits;
    /** Bitshift to get first index */
    final private int aShift;
    /** Mask to get second index */
    final private int bMask;
    /** The random integers used to generate the hash functions. */
    final private int[][] weight;

    /** The number of elements currently in the filter. It may be
     * smaller than the actual number of additions of distinct character
     * sequences because of false positives.
     */
    private int size;

    /** The natural logarithm of 2, used in the computation of the number of bits. */
    private final static double NATURAL_LOG_OF_2 = Math.log( 2 );

    private final static boolean DEBUG = false;

    /** Creates a new Bloom filter with given number of hash functions and expected number of elements.
     *
     * @param n the expected number of elements.
     * @param d the number of hash functions; if the filter add not more than <code>n</code> elements,
     * false positives will happen with probability 2<sup>-<var>d</var></sup>.
     */
    public BloomFilter32bp2Split( final int n, final int d ) {
        this.d = d;
        long minBits = (long) ((long)n * (long)d / NATURAL_LOG_OF_2);
        long pow = 0;
        while((1L<<pow) < minBits) {
        	pow++;
        }
        this.power = pow;
        this.m = 1L<<pow;
        int len = (int) (m / 32);
        if ( m > 1L<<32 ) {
        	throw new IllegalArgumentException( "This filter would require " + m + " bits" );
        }

        aShift = (int) (pow - ADDRESS_BITS_PER_UNIT - 8);
        bMask = (1<<aShift) - 1;
        bits = new int[256][ 1<<aShift ];

        System.out.println("power "+power+" bits "+m+" len "+len);
        System.out.println("aShift "+aShift+" bMask "+bMask);

        if ( DEBUG ) System.err.println( "Number of bits: " + m );

        // seeded for reproduceable behavior in repeated runs; BUT: 
        // SecureRandom's default implementation (as of 1.5) 
        // seems to mix in its own seeding.
        final SecureRandom random = new SecureRandom(new byte[] {19,96});
        weight = new int[ d ][];
        for( int i = 0; i < d; i++ ) {
            weight[ i ] = new int[ NUMBER_OF_WEIGHTS ];
            for( int j = 0; j < NUMBER_OF_WEIGHTS; j++ )
                 weight[ i ][ j ] = random.nextInt();
        }
    }

    /** The number of character sequences in the filter.
     *
     * @return the number of character sequences in the filter (but see {@link #contains(CharSequence)}).
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
	private int hash( final CharSequence s, final int l, final int k ) {
		final int[] w = weight[ k ];
		int h = 0, i = l;
		while( i-- != 0 ) h ^= s.charAt( i ) * w[ i % NUMBER_OF_WEIGHTS ];
		return h >>> (32-power); 
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
        int h;
        while( i-- != 0 ) {
            h = hash( s, l, i );
            if ( ! setGetBit( h ) ) result = true;
        }
        if ( result ) size++;
        return result;
    }
    
    protected final static int ADDRESS_BITS_PER_UNIT = 5; // 32=2^5
    protected final static int BIT_INDEX_MASK = 31; // = BITS_PER_UNIT - 1;

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
    protected boolean getBit(int bitIndex) {
        int intIndex = (int)(bitIndex >>> ADDRESS_BITS_PER_UNIT);
        return ((bits[intIndex>>>aShift][intIndex&bMask] & (1 << (bitIndex & BIT_INDEX_MASK))) != 0);
    }

    /**
     * Changes the bit with index <tt>bitIndex</tt> in local bitvector.
     *
     * (adapted from cern.colt.bitvector.QuickBitVector)
     * 
     * @param     bitIndex   the index of the bit to be set.
     */
    protected void setBit(int bitIndex) {
        int intIndex = (int)(bitIndex >>> ADDRESS_BITS_PER_UNIT);
        bits[intIndex>>>aShift][intIndex&bMask] |= 1 << (bitIndex & BIT_INDEX_MASK);
    }
    
    /**
     * Sets the bit with index <tt>bitIndex</tt> in local bitvector -- 
     * returning the old value. 
     *
     * (adapted from cern.colt.bitvector.QuickBitVector)
     * 
     * @param     bitIndex   the index of the bit to be set.
     */
    protected boolean setGetBit(int bitIndex) {
        int intIndex = (int)(bitIndex >>> ADDRESS_BITS_PER_UNIT);
        int a = intIndex>>>aShift;
        int b = intIndex&bMask;
        int mask = 1 << (bitIndex & BIT_INDEX_MASK);
        boolean ret = ((bits[a][b] & (mask)) != 0);
        bits[a][b] |= mask;
        return ret;
    }
    
	/* (non-Javadoc)
	 * @see org.archive.util.BloomFilter#getSizeBytes()
	 */
	public long getSizeBytes() {
		return bits.length*bits[0].length*4;
	}
}
