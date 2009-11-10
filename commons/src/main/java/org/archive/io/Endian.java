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
