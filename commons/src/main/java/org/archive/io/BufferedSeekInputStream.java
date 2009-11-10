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
package org.archive.io;


import java.io.IOException;


/**
 * Buffers data from some other SeekInputStream.
 * 
 * @author pjack
 */
public class BufferedSeekInputStream extends SeekInputStream {


    /**
     * The underlying input stream.
     */
    final private SeekInputStream input;


    /**
     * The buffered data.
     */
    final private byte[] buffer;
    
    
    /**
     * The maximum offset of valid data in the buffer.  Usually the same
     * as buffer.length, but may be shorter if we're in the last region
     * of the stream.
     */
    private int maxOffset;


    /**
     * The offset of within the buffer of the next byte to read.
     */
    private int offset;


    /**
     * Constructor.
     * 
     * @param input  the underlying input stream
     * @param capacity   the size of the buffer
     * @throws IOException   if an IO occurs filling the first buffer
     */
    public BufferedSeekInputStream(SeekInputStream input, int capacity) 
    throws IOException {
        this.input = input;
        this.buffer = new byte[capacity];
        buffer();
    }

    /**
     * Fills the buffer.
     * 
     * @throws IOException  if an IO error occurs
     */
    private void buffer() throws IOException {
        int remaining = buffer.length;
        while (remaining > 0) {
            int r = input.read(buffer, buffer.length - remaining, remaining);
            if (r <= 0) {
                // Not enough information to fill the buffer
                offset = 0;
                maxOffset = buffer.length - remaining;
                return;
            }
            remaining -= r;
        }
        maxOffset = buffer.length;
        offset = 0;
    }


    /**
     * Ensures that the buffer is valid.
     * 
     * @throws IOException  if an IO error occurs
     */
    private void ensureBuffer() throws IOException {
        if (offset >= maxOffset) {
            buffer();
        }
    }


    /**
     * Returns the number of unread bytes in the current buffer.
     * 
     * @return  the remaining bytes
     */
    private int remaining() {
        return maxOffset - offset;
    }


    @Override
    public int read() throws IOException {
        ensureBuffer();
        if (maxOffset == 0) {
            return -1;
        }
        int ch = buffer[offset] & 0xFF;
        offset++;
        return ch;
    }


    @Override
    public int read(byte[] buf, int ofs, int len) throws IOException {
        ensureBuffer();
        if (maxOffset == 0) {
            return 0;
        }
        len = Math.min(len, remaining());
        System.arraycopy(buffer, offset, buf, ofs, len);
        offset += len;
        return len;
    }


    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }


    @Override
    public long skip(long c) throws IOException {
        ensureBuffer();
        if (maxOffset == 0) {
            return 0;
        }
        int count = (c > Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)c;
        int skip = Math.min(count, remaining());
        offset += skip;
        return skip;
    }


    /**
     * Returns the stream's current position.
     * 
     * @return  the current position
     */
    public long position() throws IOException {
        return input.position() - buffer.length + offset;
    }


    /**
     * Seeks to the given position.  This method avoids re-filling the buffer
     * if at all possible.
     * 
     * @param p  the position to set
     * @throws IOException if an IO error occurs
     */
    public void position(long p) throws IOException {
        long blockStart = (input.position() - maxOffset) 
         / buffer.length * buffer.length;
        long blockEnd = blockStart + maxOffset;
        if ((p >= blockStart) && (p < blockEnd)) {
            // Desired position is somewhere inside current buffer
            long adj = p - blockStart;
            offset = (int)adj;
            return;
        }
        positionDirect(p);
    }


    /**
     * Positions the underlying stream at the given position, then refills
     * the buffer.
     * 
     * @param p  the position to set
     * @throws IOException  if an IO error occurs
     */
    private void positionDirect(long p) throws IOException {
        long newBlockStart = p / buffer.length * buffer.length;
        input.position(newBlockStart);
        buffer();
        offset = (int)(p % buffer.length);        
    }

    /** 
     * Close the stream, including the wrapped input stream. 
     */
    public void close() throws IOException {
        super.close();
        if(this.input!=null) {
            this.input.close();
        }
    }


}
