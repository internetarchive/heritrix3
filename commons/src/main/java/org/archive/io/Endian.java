/* Endian
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
package org.archive.io;


import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;


/**
 * Reads integers stored in big or little endian streams.
 * 
 * @author pjack
 */
public class Endian {


    /**
     * Static utility class.
     */
    private Endian() {
    }
    

    /**
     * Reads the next little-endian unsigned 16 bit integer from the
     * given stream.
     * 
     * @param input  the input stream to read from
     * @return  the next 16-bit little-endian integer
     * @throws IOException   if an IO error occurs
     */
    public static char littleChar(InputStream input) throws IOException {
        int lo = input.read();
        if (lo < 0) {
            throw new EOFException();
        }
        int hi = input.read();
        if (hi < 0) {
            throw new EOFException();
        }
        return (char)((hi << 8) | lo);
    }
    
    
    /**
     * Reads the next little-endian signed 16-bit integer from the
     * given stream.
     * 
     * @param input  the input stream to read from
     * @return  the next 16-bit little-endian integer
     * @throws IOException   if an IO error occurs
     */
    public static short littleShort(InputStream input) throws IOException {
        return (short)littleChar(input);
    }


    /**
     * Reads the next little-endian signed 32-bit integer from the
     * given stream.
     * 
     * @param input  the input stream to read from
     * @return  the next 32-bit little-endian integer
     * @throws IOException   if an IO error occurs
     */
    public static int littleInt(InputStream input) throws IOException {
        char lo = littleChar(input);
        char hi = littleChar(input);
        return (hi << 16) | lo;
    }

    
    /**
     * Reads the next big-endian unsigned 16 bit integer from the
     * given stream.
     * 
     * @param input  the input stream to read from
     * @return  the next 16-bit big-endian integer
     * @throws IOException   if an IO error occurs
     */
    public static char bigChar(InputStream input) throws IOException {
        int hi = input.read();
        if (hi < 0) {
            throw new EOFException();
        }
        int lo = input.read();
        if (lo < 0) {
            throw new EOFException();
        }
        return (char)((hi << 8) | lo);
    }


    /**
     * Reads the next big-endian signed 32-bit integer from the
     * given stream.
     * 
     * @param input  the input stream to read from
     * @return  the next 32-bit big-endian integer
     * @throws IOException   if an IO error occurs
     */
    public static int bigInt(InputStream input) throws IOException {
        char hi = bigChar(input);
        char lo = bigChar(input);
        return (hi << 16) | lo;
    }
}
