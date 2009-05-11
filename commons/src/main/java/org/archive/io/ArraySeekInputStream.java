/* ArraySeekInputStream
*
* Created on September 18, 2006
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
