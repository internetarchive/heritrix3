/* SafeSeekInputStream
*
* Created on September 14, 2006
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
 * Enables multiple concurrent streams based on the same underlying stream.
 * 
 * @author pjack
 */
public class SafeSeekInputStream extends SeekInputStream {


    /**
     * The underlying stream.
     */
    private SeekInputStream input;


    /**
     * The expected position of the underlying stream.
     */
    private long expected;


    /**
     * Constructor.  The given stream will be positioned to 0 so that an
     * accurate position can be tracked.
     * 
     * @param input  the underlying input stream
     * @throws IOException   if an IO error occurs
     */
    public SafeSeekInputStream(SeekInputStream input) throws IOException {
        this.input = input;
        this.expected = input.position();
    }


    /**
     * Ensures that the underlying stream's position is what we expect to be.
     * 
     * @throws IOException  if an IO error occurs
     */
    private void ensure() throws IOException {
        if (expected != input.position()) {
            input.position(expected);
        }
    }


    @Override
    public int read() throws IOException {
        ensure();
        int c = input.read();
        if (c >= 0) {
            expected++;
        }
        return c;
    }


    @Override
    public int read(byte[] buf, int ofs, int len) throws IOException {
        ensure();
        int r = input.read(buf, ofs, len);
        if (r > 0) {
            expected += r;
        }
        return r;
    }


    @Override
    public int read(byte[] buf) throws IOException {
        ensure();
        int r = input.read(buf);
        if (r > 0) {
            expected += r;
        }
        return r;
    }


    @Override
    public long skip(long c) throws IOException {
        ensure();
        long r = input.skip(c);
        if (r > 0) {
            expected += r;
        }
        return r;
    }
    
    
    public void position(long p) throws IOException {
        input.position(p);
        expected = p;
    }


    public long position() throws IOException {
        return expected;
    }

}
