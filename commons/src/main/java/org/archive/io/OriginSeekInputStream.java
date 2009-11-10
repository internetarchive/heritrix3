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
