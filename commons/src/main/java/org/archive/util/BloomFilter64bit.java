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
 * <li>NUMBER_OF_WEIGHTS is 2083, to better avoid collisions between 
 * similar strings</li>
 * <li>Removed dependence on cern.colt MersenneTwister (replaced with
 * SecureRandom) and QuickBitVector (replaced with local methods).</li>
 * <li>Adapted to allow long bit indices so long as the index/64 (used 
 * an array index in bit vector) fits within Integer.MAX_VALUE. (Thus
 * it supports filters up to 64*Integer.MAX_VALUE bits in size, or 
 * 16GiB.)</li>
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
public class BloomFilter64bit implements Serializable, BloomFilter {

    private static final long serialVersionUID = 2317000663009608403L;

    /** The number of weights used to create hash functions. */
    final public static int NUMBER_OF_WEIGHTS = 2083; // CHANGED FROM 16
    /** The number of bits in this filter. */
    final public long m;
    /** The number of hash functions used by this filter. */
    final public int d;
    /** The underlying bit vector. package access for testing */
    final long[] bits;
    /** The random integers used to generate the hash functions. */
    final long[][] weight;

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
    public BloomFilter64bit( final int n, final int d ) {
        this.d = d;
        int len = (int)Math.ceil( ( (long)n * (long)d / NATURAL_LOG_OF_2 ) / 64L );
        if ( len/64 > Integer.MAX_VALUE ) throw new IllegalArgumentException( "This filter would require " + len * 64L + " bits" );
        bits = new long[ len ];
        m = bits.length * 64L;

        if ( DEBUG ) System.err.println( "Number of bits: " + m );

        // seeded for reproduceable behavior in repeated runs; BUT: 
        // SecureRandom's default implementation (as of 1.5) 
        // seems to mix in its own seeding.
        final SecureRandom random = new SecureRandom(new byte[] {19,96});
        weight = new long[ d ][];
        for( int i = 0; i < d; i++ ) {
            weight[ i ] = new long[ NUMBER_OF_WEIGHTS ];
            for( int j = 0; j < NUMBER_OF_WEIGHTS; j++ )
                 weight[ i ][ j ] = random.nextLong();
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

    private long hash( final CharSequence s, final int l, final int k ) {
        final long[] w = weight[ k ];
        long h = 0;
        int i = l;
        while( i-- != 0 ) h ^= s.charAt( i ) * w[ i % NUMBER_OF_WEIGHTS ];
        return ( h & 0x7FFFFFFFFFFFFFFFL ) % m;
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
            if ( ! getBit( h ) ) {
                result = true;
                setBit( h );
            }
        }
        if ( result ) size++;
        return result;
    }
    
    protected final static long ADDRESS_BITS_PER_UNIT = 6; // 64=2^6
    protected final static long BIT_INDEX_MASK = 63; // = BITS_PER_UNIT - 1;

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
    protected boolean getBit(long bitIndex) {
        return ((bits[(int)(bitIndex >> ADDRESS_BITS_PER_UNIT)] & (1L << (bitIndex & BIT_INDEX_MASK))) != 0);
    }

    /**
     * Changes the bit with index <tt>bitIndex</tt> in local bitvector.
     *
     * (adapted from cern.colt.bitvector.QuickBitVector)
     * 
     * @param     bitIndex   the index of the bit to be set.
     */
    protected void setBit( long bitIndex) {
            bits[(int)(bitIndex >> ADDRESS_BITS_PER_UNIT)] |= 1L << (bitIndex & BIT_INDEX_MASK);
    }
    
	/* (non-Javadoc)
	 * @see org.archive.util.BloomFilter#getSizeBytes()
	 */
	public long getSizeBytes() {
		return bits.length*8;
	}
}
