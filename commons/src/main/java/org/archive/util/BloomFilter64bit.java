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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Random;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.Funnels;
import com.google.common.primitives.Ints;

public class BloomFilter64bit implements Serializable, BloomFilter {
    private static final long serialVersionUID = 3L;

    /** The expected number of inserts; determines calculated size */ 
    private final long expectedInserts; 

    /** The number of elements currently in the filter. It may be
     * smaller than the actual number of additions of distinct character
     * sequences because of false positives.
     */
    private int size;

    private final com.google.common.hash.BloomFilter<CharSequence> delegate;
    private final long bitSize;
    private final int numHashFunctions;

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
     * @param weightsGenerator may provide a seeded Random for reproducible
     * internal universal hash function weighting
     * @param roundUp if true, round bit size up to next-nearest-power-of-2
     */
    public BloomFilter64bit(final long n, final int d, Random weightsGenerator, boolean roundUp ) {
        delegate = com.google.common.hash.BloomFilter.create(Funnels.unencodedCharsFunnel(), Ints.saturatedCast(n), Math.pow(2, -d));
        this.expectedInserts = n; 
        try {
        Method bitSizeMethod = delegate.getClass().getDeclaredMethod("bitSize", new Class[] {});
        bitSizeMethod.setAccessible(true);
        bitSize = (long) bitSizeMethod.invoke(delegate, new Object[] {}); 

        Field numHashFunctionField = delegate.getClass().getDeclaredField("numHashFunctions");
        numHashFunctionField.setAccessible(true);
        numHashFunctions = numHashFunctionField.getInt(delegate);
        } catch (Exception e) {
          throw new RuntimeException(e);
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
      return delegate.mightContain(s);
    }

    /** Adds a character sequence to the filter.
     *
     * @param s a character sequence.
     * @return true if the character sequence was not in the filter (but see {@link #contains(CharSequence)}).
     */

    public boolean add( final CharSequence s ) {
      boolean added = delegate.put(s);
      if (added) {
        size++;
      }
      return added;
    }

    /* (non-Javadoc)
     * @see org.archive.util.BloomFilter#getSizeBytes()
     */
    public long getSizeBytes() {
      return bitSize / 8;
    }

    @Override
    public long getExpectedInserts() {
        return expectedInserts;
    }

    @Override
    public long getHashCount() {
        return numHashFunctions;
    }

    @VisibleForTesting
    public boolean getBit(long bitIndex) {
      try {
        Field bitsField = delegate.getClass().getDeclaredField("bits");
        bitsField.setAccessible(true);
        Object bitarray = bitsField.get(delegate);
        Method getBitMethod = bitarray.getClass().getDeclaredMethod("get", long.class);
        getBitMethod.setAccessible(true);
        return (boolean) getBitMethod.invoke(bitarray, bitIndex);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
}
