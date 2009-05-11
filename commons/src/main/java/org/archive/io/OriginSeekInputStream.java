/* OriginSeekInputStream
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
 * Alters the origin of some other SeekInputStream.  This class allows you
 * to completely ignore everything in the underlying stream before a specified
 * position, the origin position.
 * 
 * <p>With the exception of {@link #position()} and {@link position(long)},
 * all of the methods in this class simply delegate to the underlying input 
 * stream.  The <code>position</code> methods adjust the position of the
 * underlying stream relative to the origin specified at construction time.
 * 
 * @author pjack
 */
public class OriginSeekInputStream extends SeekInputStream {


    /**
     * The underlying stream.
     */
    final private SeekInputStream input;


    /**
     * The origin position.  In other words, this.position(0)
     * resolves to input.position(start).
     */
    final private long origin;


    /**
     * Constructor.
     * 
     * @param input   the underlying stream
     * @param origin   the origin position
     * @throws IOException   if an IO error occurs
     */
    public OriginSeekInputStream(SeekInputStream input, long origin) 
    throws IOException {
        this.input = input;
        this.origin = origin;
        input.position(origin);
    }


    @Override
    public int available() throws IOException {
        return input.available();
    }


    @Override
    public int read() throws IOException {
        return input.read();
    }


    @Override
    public int read(byte[] buf, int ofs, int len) throws IOException {
        return input.read(buf, ofs, len);
    }


    @Override
    public int read(byte[] buf) throws IOException {
        return input.read(buf);
    }


    @Override
    public long skip(long count) throws IOException {
        return input.skip(count);
    }


    /**
     * Returns the position of the underlying stream relative to the origin.
     * 
     * @return  the relative position
     * @throws IOException  if an IO error occurs
     */
    public long position() throws IOException {
        return input.position() - origin;
    }


    /**
     * Positions the underlying stream relative to the origin.
     * In other words, this.position(0) resolves to input.position(origin),
     * where input is underlying stream and origin is the origin specified
     * at construction time.
     * 
     * @param p   the new position for this stream
     * @throws IOException  if an IO error occurs
     */
    public void position(long p) throws IOException {
        input.position(p + origin);
    }
}
