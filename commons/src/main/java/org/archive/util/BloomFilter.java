/* BloomFilter
*
* Copyright (C) 2010 Internet Archive; an adaptation of
* LGPL work (C) Sebastiano Vigna
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* This class is free software; you can redistribute it and/or modify
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

/**
 * Common interface for different Bloom filter implementations
 * 
 * @author Gordon Mohr
 */
public interface BloomFilter {
	/** The number of character sequences in the filter (considered to be the 
	 * number of add()s that returned 'true')
	 *
	 * @return the number of character sequences in the filter (but see {@link #contains(CharSequence)}).
	 */
	public abstract int size();

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
	public abstract boolean contains(final CharSequence s);

	/** Adds a character sequence to the filter.
	 *
	 * @param s a character sequence.
	 * @return true if the character sequence was not in the filter (but see {@link #contains(CharSequence)}).
	 */
	public abstract boolean add(final CharSequence s);

	/**
     * The amount of memory in bytes consumed by the bloom 
     * bitfield.
     *
	 * @return memory used by bloom bitfield, in bytes
	 */
	public abstract long getSizeBytes();
	
	/**
	 * Report the number of expected inserts used at instantiation time to 
	 * calculate the bitfield size. 
	 * 
	 * @return long number of inserts expected at instantiation
	 */
    public abstract long getExpectedInserts();
    
    /**
     * Report the number of internal independent hash function (and thus the
     * number of bits set/checked for each item presented). 
     * 
     * @return long count of hash functions
     */
    public abstract long getHashCount(); 
    
    // public for white-box unit testing
    public boolean getBit(long bitIndex);
}