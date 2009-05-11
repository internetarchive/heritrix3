/* BlockInputStream
*
* Created on September 12, 2006
*
* Copyright (C) 2006 Internet Archive.
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
package org.archive.util.ms;

import java.io.IOException;

import org.archive.io.SeekInputStream;
import static org.archive.util.ms.BlockFileSystem.BLOCK_SIZE;


/**
 * InputStream for a file contained in a BlockFileSystem.
 */
public class BlockInputStream extends SeekInputStream {


    /**
     * The starting block number.
     */
    private int start;
    
    
    /**
     * The current block.
     */
    private int block;
    
    
    /**
     * The BlockFileSystem that produced this stream.
     */
    private BlockFileSystem bfs;
    
    
    /**
     * The raw input stream of the BlockFileSystem.
     */
    private SeekInputStream raw;
    
    
    /**
     * The current logical position of this stream.
     */
    private long position;
    
    
    /**
     * The current file pointer position of the raw input stream.
     */
    private long expectedRawPosition;

    
    /**
     * The number of bytes read in the current block.
     */
    private int blockBytesRead;


    /**
     * Constructor.
     * 
     * @param bfs    The block file system that owns this stream
     * @param block  The starting block number.
     */
    public BlockInputStream(BlockFileSystem bfs, int block) throws IOException {
        this.raw = bfs.getRawInput();
        this.bfs = bfs;
        this.start = block;
        this.block = block;
        this.position = 0;
        seek(block, 0);
    }
    
    
    
    private void seek(long block, long rem) throws IOException {
        assert rem < BLOCK_SIZE;
        long pos = (block + 1) * BLOCK_SIZE + rem;
        blockBytesRead = (int)rem;
        expectedRawPosition = pos;
        raw.position(pos);
    }
    
    
    private void ensureRawPosition() throws IOException {
        if (raw.position() != expectedRawPosition) {
            raw.position(expectedRawPosition);
        }
    }
    
    private boolean ensureBuffer() throws IOException {
        if (block < 0) {
            return false;
        }
        ensureRawPosition();
        if (blockBytesRead < BLOCK_SIZE) {
            return true;
        }
        block = bfs.getNextBlock(block);
        if (block < 0) {
            return false;
        }
        seek(block, 0);
        return true;
    }

    
    public long skip(long v) throws IOException {
        // FIXME
        int r = read();
        return (r < 0) ? 0 : 1;
    }

    public int read() throws IOException {
        if (!ensureBuffer()) {
            return -1;
        }
        int r = raw.read();
        position++;
        expectedRawPosition++;
        blockBytesRead++;
        return r;
    }
    
    
    public int read(byte[] b, int ofs, int len) throws IOException {
        if (!ensureBuffer()) {
            return 0;
        }
        int rem = BLOCK_SIZE - (int)(position % BLOCK_SIZE);
        len = Math.min(len, rem);
        int c = raw.read(b, ofs, len);
        position += c;
        expectedRawPosition += c;
        blockBytesRead++;
        return len;
    }


    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }


    public long position() {
        return position;
    }


    public void position(long v) throws IOException {
        ensureRawPosition();
        if (v == position) {
            return;
        }
        
        // If new position is in same block, just seek.
        if (v / BLOCK_SIZE == position / BLOCK_SIZE) {
            long rem = v % BLOCK_SIZE;
            seek(block, rem);
            position = v;
            return;
        }
        
        if (v > position) {
            seekAfter(v);
        } else {
            seekBefore(v);
        }
    }

    
    private void seekAfter(long v) throws IOException {
        long currentBlock = position / BLOCK_SIZE;
        long destBlock = v / BLOCK_SIZE;
        long blockAdvance = destBlock - currentBlock;
        for (int i = 0; i < blockAdvance; i++) {
            block = bfs.getNextBlock(block);
        }
        seek(block, v % BLOCK_SIZE);
        position = v;
    }

    
    private void seekBefore(long v) throws IOException {
        long blockAdvance = (v - 1) / BLOCK_SIZE;
        block = start;
        for (int i = 0; i < blockAdvance; i++) {
            block = bfs.getNextBlock(block);
        }
        seek(block, v % BLOCK_SIZE);
    }
}
