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
 * A repositionable stream backed by an array.
 * 
 * @author pjack
 */
public class ArraySeekInputStream extends SeekInputStream {


    /**
     * The array of bytes to read from.
     */
    private byte[] array;
    
    
    /**
     * The offset in the array of the next byte to read.
     */
    private int offset;
    
    
    /**
     * Constructor.  Note that changes to the given array will be reflected
     * in the stream.
     * 
     * @param array  The array to read bytes from.
     */
    public ArraySeekInputStream(byte[] array) {
        this.array = array;
        this.offset = 0;
    }
    
    
    @Override
    public int read() {
        if (offset >= array.length) {
            return -1;
        }
        int r = array[offset] & 0xFF;
        offset++;
        return r;
    }

    
    @Override
    public int read(byte[] buf, int ofs, int len) {
        if (offset >= array.length) {
            return 0;
        }
        len = Math.min(len, array.length - offset);
        System.arraycopy(array, offset, buf, ofs, len);
        offset += len;
        return len;
    }
    
    
    @Override
    public int read(byte[] buf) {
        return read(buf, 0, buf.length);
    }

    
    /**
     * Returns the position of the stream.
     */
    public long position() {
        return offset;
    }


    /**
     * Repositions the stream.
     * 
     * @param  p  the new position for the stream
     * @throws IOException if the given position is out of bounds
     */
    public void position(long p) throws IOException {
        if ((p < 0) || (p > array.length)) {
            throw new IOException("Invalid position: " + p);
        }
        offset = (int)p;
    }

}
