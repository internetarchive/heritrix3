/* RecyclingFastBufferedOutputStream
*
* $Id$
*
* Created on May 26, 2005
*
* Based on FastBufferedOutputStream in MG4J; see:
* 
*   http://mg4j.dsi.unimi.it/
* 
* (Sole addition is one new constructor.)
*  
* Revisions copyright (C) 2005 Internet Archive.
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
import java.io.OutputStream;

/** Lightweight, unsynchronised, aligned output stream buffering class.
 *
 * <P>This class provides buffering for output streams, but it does so with 
 * purposes and an internal logic that are radically different from the ones
 * adopted in {@link java.io.BufferedOutputStream}.
 * 
 * <P>All methods are unsychronised. Moreover,
 * it is guaranteed that <em>all writes performed by this class will be
 * multiples of the given buffer size</em>.  If, for instance, you use the
 * default buffer size, writes will be performed on the underlying input stream
 * in multiples of 16384 bytes. This is very important on operating systems
 * that optimise disk reads on disk block boundaries.
 */

public class RecyclingFastBufferedOutputStream extends OutputStream {

    /** The default size of the internal buffer in bytes (16Ki). */
    public final static int DEFAULT_BUFFER_SIZE = 16 * 1024;

    /** The internal buffer. */
    protected byte buffer[];

    /** The current position in the buffer. */
    protected int pos;

    /** The number of buffer bytes available starting from {@link #pos}. */
    protected int avail;

    /** The underlying output stream. */
    protected OutputStream os;

    /** Creates a new fast buffered output stream by wrapping a given output stream, using a given buffer 
    *
    * @param os an output stream to wrap.
    * @param buffer buffer to use internally.
    */

   public RecyclingFastBufferedOutputStream( final OutputStream os, final byte[] buffer ) {
       this.os = os;
       this.buffer = buffer;
       avail = buffer.length;
   }
   
    /** Creates a new fast buffered output stream by wrapping a given output stream with a given buffer size. 
     *
     * @param os an output stream to wrap.
     * @param bufSize the size in bytes of the internal buffer.
     */

    public RecyclingFastBufferedOutputStream( final OutputStream os, final int bufSize ) {
        this(os, new byte [ bufSize]);
    }

    /** Creates a new fast buffered ouptut stream by wrapping a given output stream with a buffer of {@link #DEFAULT_BUFFER_SIZE} bytes. 
     *
     * @param os an output stream to wrap.
     */
    public RecyclingFastBufferedOutputStream( final OutputStream os ) {
        this( os, DEFAULT_BUFFER_SIZE );
    }

    private void dumpBufferIfFull() throws IOException {
        if ( avail == 0 ) {
            os.write( buffer );
            pos = 0;
            avail = buffer.length;
        }
    }

    public void write( final int b ) throws IOException {
        avail--;
        buffer[ pos++ ] = (byte)b;
        dumpBufferIfFull();
    }


    public void write( final byte b[], int offset, int length ) throws IOException {
        if ( length <= avail ) {
            System.arraycopy( b, offset, buffer, pos, length );
            pos += length;
            avail -= length;
            dumpBufferIfFull();
            return;
        }
    
        System.arraycopy( b, offset, buffer, pos, avail );
        os.write( buffer );

        offset += avail;
        length -= avail;

        final int residual = length % buffer.length;

        os.write( b, offset, length - residual ); 
        System.arraycopy( b, offset + length - residual, buffer, 0, residual );
        pos = residual;
        avail = buffer.length - residual;
    }

    public void close() throws IOException {
        if ( os == null ) return;
        if ( pos != 0 ) os.write( buffer, 0, pos );
        if ( os != System.out ) os.close();
        os = null;
        buffer = null;
    }

}
    

// Local Variables:
// mode: jde
// tab-width: 4
// End:

