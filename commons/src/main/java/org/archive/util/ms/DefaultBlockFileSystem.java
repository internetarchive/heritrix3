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
package org.archive.util.ms;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import org.archive.io.SeekInputStream;
import org.archive.util.ArchiveUtils;
import org.archive.util.LRU;


/**
 * Default implementation of the Block File System.
 * 
 * <p>The overall structure of a BlockFileSystem file (such as a .doc file) is
 * as follows.  The file is divided into blocks, which are of uniform length
 * (512 bytes).  The first block (at file pointer 0) is called the header
 * block.  It's used to look up other blocks in the file.
 * 
 * <p>Subfiles contained within the .doc file are organized using a Block
 * Allocation Table, or BAT.  The BAT is basically a linked list; given a 
 * block number, the BAT will tell you the next block number.  Note that
 * the header block has no number; block #0 is the first block after the
 * header.  Thus, to convert a block number to a file pointer:
 * <code>int filePointer = (blockNumber + 1) * BLOCK_SIZE</code>.
 * 
 * <p>The BAT itself is discontinuous, however.  To find the blocks that 
 * comprise the BAT, you have to look in the header block.  The header block
 * contains an array of 109 pointers to the blocks that comprise the BAT.
 * If more than 109 BAT blocks are required (in other words, if the .doc
 * file is larger than ~6 megabytes), then something called the 
 * XBAT comes into play.
 * 
 * <p>XBAT blocks contain pointers to the 110th BAT block and beyond.
 * The first XBAT block is stored at a file pointer listed in the header.
 * The other XBAT blocks are always stored in order after the first; the 
 * XBAT table is continuous.  One is inclined to wonder why the BAT itself
 * is not so stored, but oh well.
 * 
 * <p>The BAT only tells you the next block for a given block.  To find the 
 * first block for a subfile, you have to look up that subfile's directory
 * entry.  Each directory entry is a 128 byte structure in the file, so four
 * of them fit in a block.  The number of the first block of the entry list
 * is stored in the header.  To find subsequent entry blocks, the BAT must
 * be used.
 * 
 * <p>I'm telling you all this so that you understand the caching that this
 * class provides.
 * 
 * <p>First, directory entries are not cached.  It's assumed that they will
 * be looked up at the beginning of a lengthy operation, and then forgotten
 * about.  This is certainly the case for {@link Doc#getText(BlockFileSystem)}. 
 * If you need to remember directory entries, you can manually store the Entry 
 * objects in a map or something, as they don't grow stale.
 * 
 * <p>This class keeps all 512 bytes of the header block in memory at all 
 * times.  This prevents a potentially expensive file pointer repositioning
 * every time you're trying to figure out what comes next.
 * 
 * <p>BAT and XBAT blocks are stored in a least-recently used cache.  The 
 * <i>n</i> most recent BAT and XBAT blocks are remembered, where <i>n</i>
 * is set at construction time.  The minimum value of <i>n</i> is 1.  For small
 * files, this can prevent file pointer repositioning for BAT look ups.
 * 
 * <p>The BAT/XBAT cache only takes up memory as needed.  If the specified
 * cache size is 100 blocks, but the file only has 4 BAT blocks, then only 
 * 2048 bytes will be used by the cache.
 * 
 * <p>Note this class only caches BAT and XBAT blocks.  It does not cache the
 * blocks that actually make up a subfile's contents.  It is assumed that those
 * blocks will only be accessed once per operation (again, this is what
 * {Doc.getText(BlockFileSystem)} typically requires.)
 * 
 * @author pjack
 * @see http://jakarta.apache.org/poi/poifs/fileformat.html
 */
public class DefaultBlockFileSystem implements BlockFileSystem {


    /**
     * Pointers per BAT block.
     */
    final private static int POINTERS_PER_BAT = 128;


    /**
     * Size of a BAT pointer in bytes.  (In other words, 4).
     */
    final private static int BAT_POINTER_SIZE = BLOCK_SIZE / POINTERS_PER_BAT;

    
    /**
     * The number of BAT pointers in the header block.  After this many 
     * BAT blocks, the XBAT blocks must be consulted.
     */
    final private static int HEADER_BAT_LIMIT = 109;
    
    
    /**
     * The size of an entry record in bytes.
     */
    final private static int ENTRY_SIZE = 128;
    
    
    /**
     * The number of entries that can fit in a block.
     */
    final private static int ENTRIES_PER_BLOCK = BLOCK_SIZE / ENTRY_SIZE;


    /**
     * The .doc file as a stream.
     */
    private SeekInputStream input;
    
    
    /**
     * The header block.
     */
    private HeaderBlock header;


    /**
     * Cache of BAT and XBAT blocks.
     */
    private Map<Integer,ByteBuffer> cache;
    
    
    /**
     * Constructor.
     * 
     * @param input   the file to read from
     * @param batCacheSize  number of BAT and XBAT blocks to cache
     * @throws IOException  if an IO error occurs
     */
    public DefaultBlockFileSystem(SeekInputStream input, int batCacheSize)
    throws IOException {
        this.input = input;
        byte[] temp = new byte[BLOCK_SIZE];
        ArchiveUtils.readFully(input, temp);
        this.header = new HeaderBlock(ByteBuffer.wrap(temp));
        this.cache = new LRU<Integer,ByteBuffer>(batCacheSize);
    }


    public Entry getRoot() throws IOException {
        // Position to the first block of the entry list.
        int block = header.getEntriesStart();
        input.position((block + 1) * BLOCK_SIZE);
        
        // The root entry is always entry #0.
        return new DefaultEntry(this, input, 0);
    }


    /**
     * Returns the entry with the given number.
     * 
     * @param entryNumber   the number of the entry to return
     * @return   that entry, or null if no such entry exists
     * @throws IOException  if an IO error occurs
     */
    Entry getEntry(int entryNumber) throws IOException {
        // Entry numbers < 0 typically indicate an end-of-stream.
        if (entryNumber < 0) {
            return null;
        }
        
        // It's impossible to check against the upper bound, because the
        // upper bound is not recorded anywhere.
        
        // Advance to the block containing the desired entry.
        int blockCount = entryNumber / ENTRIES_PER_BLOCK;
        int remainder = entryNumber % ENTRIES_PER_BLOCK;        
        int block = header.getEntriesStart();
        for (int i = 0; i < blockCount; i++) {
            block = getNextBlock(block);
        }
        
        if (block < 0) {
            // Given entry number exceeded the number of available entries.
            return null;
        }

        int filePos = (block + 1) * BLOCK_SIZE + remainder * ENTRY_SIZE;
        input.position(filePos);
        
        return new DefaultEntry(this, input, entryNumber);
    }


    public int getNextBlock(int block) throws IOException {
        if (block < 0) {
            return block;
        }
        
        // Index into the header array of BAT blocks.
        int headerIndex = block / POINTERS_PER_BAT;
        
        // Index within that BAT block of the block we're interested in.
        int batBlockIndex = block % POINTERS_PER_BAT;

        int batBlockNumber = batLookup(headerIndex);
        ByteBuffer batBlock = getBATBlock(batBlockNumber);
        return batBlock.getInt(batBlockIndex * BAT_POINTER_SIZE);
    }

    
    /**
     * Looks up the block number of a BAT block.
     * 
     * @param headerIndex  
     * @return
     * @throws IOException
     */
    private int batLookup(int headerIndex) throws IOException {
        if (headerIndex < HEADER_BAT_LIMIT + 1) {
            return header.getBATBlockNumber(headerIndex);
        }
        
        // Find the XBAT block of interest
        headerIndex -= HEADER_BAT_LIMIT;
        int xbatBlockNumber = headerIndex / POINTERS_PER_BAT;
        xbatBlockNumber += header.getExtendedBATStart();
        ByteBuffer xbat = getBATBlock(xbatBlockNumber);

        // Find the bat Block number inside the XBAT block
        int xbatBlockIndex = headerIndex % POINTERS_PER_BAT;
        return xbat.getInt(xbatBlockIndex * BAT_POINTER_SIZE);
    }

    
    /**
     * Returns the BAT block with the given block number.
     * If the BAT block were previously cached, then the cached version
     * is returned.  Otherwise, the file pointer is repositioned to 
     * the start of the given block, and the 512 bytes are read and
     * stored in the cache.
     * 
     * @param block   the block number of the BAT block to return
     * @return   the BAT block
     * @throws IOException
     */
    private ByteBuffer getBATBlock(int block) throws IOException {
        ByteBuffer r = cache.get(block);
        if (r != null) {
            return r;
        }

        byte[] buf = new byte[BLOCK_SIZE];
        input.position((block + 1) * BLOCK_SIZE);
        ArchiveUtils.readFully(input, buf);

        r = ByteBuffer.wrap(buf);
        r.order(ByteOrder.LITTLE_ENDIAN);
        cache.put(block, r);
        return r;
    }


    public SeekInputStream getRawInput() {
        return input;
    }
}
