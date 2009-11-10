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
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

/**
 * Read in the GZIP header.
 * 
 * See RFC1952 for specification on what the header looks like.
 * Assumption is that stream is cued-up with the gzip header as the
 * next thing to be read.
 * 
 * <p>Of <a href="http://jguru.com/faq/view.jsp?EID=13647">Java
 * and unsigned bytes</a>. That is, its always a signed int in
 * java no matter what the qualifier whether byte, char, etc.
 * 
 * <p>Add accessors for optional filename, comment and MTIME.
 * 
 * @author stack
 */
public class GzipHeader {
    /**
     * Length of minimal GZIP header.
     *
     * See RFC1952 for explaination of value of 10.
     */
    public static final int MINIMAL_GZIP_HEADER_LENGTH = 10;
    
    /**
     * Total length of the gzip header.
     */
    protected int length = 0;

    /**
     * The GZIP header FLG byte.
     */
    protected int flg;
    
    /**
     * GZIP header XFL byte.
     */
    private int xfl;
    
    /**
     * GZIP header OS byte.
     */
    private int os;
    
    /**
     * Extra header field content.
     */
    private byte [] fextra = null;
    
    /**
     * GZIP header MTIME field.
     */
    private int mtime;
    
    
    /**
     * Shutdown constructor.
     * 
     * Must pass an input stream.
     */
    public GzipHeader() {
        super();
    }
    
    /**
     * Constructor.
     * 
     * This constructor advances the stream past any gzip header found.
     * 
     * @param in InputStream to read from.
     * @throws IOException
     */
    public GzipHeader(InputStream in) throws IOException {
        super();
        readHeader(in);
    }
    
    /**
     * Read in gzip header.
     * 
     * Advances the stream past the gzip header.
     * @param in InputStream.
     * 
     * @throws IOException Throws if does not start with GZIP Header.
     */
    public void readHeader(InputStream in) throws IOException {
        CRC32 crc = new CRC32();
        crc.reset();
        if (!testGzipMagic(in, crc)) {
            throw new NoGzipMagicException();
        }
        this.length += 2;
        if (readByte(in, crc) != Deflater.DEFLATED) {
            throw new IOException("Unknown compression");
        }
        this.length++;
       
        // Get gzip header flag.
        this.flg = readByte(in, crc);
        this.length++;
        
        // Get MTIME.
        this.mtime = readInt(in, crc);
        this.length += 4;
        
        // Read XFL and OS.
        this.xfl = readByte(in, crc);
        this.length++;
        this.os = readByte(in, crc);
        this.length++;
        
        // Skip optional extra field -- stuff w/ alexa stuff in it.
        final int FLG_FEXTRA = 4;
        if ((this.flg & FLG_FEXTRA) == FLG_FEXTRA) {
            int count = readShort(in, crc);
            this.length +=2;
            this.fextra = new byte[count];
            readByte(in, crc, this.fextra, 0, count);
            this.length += count;
        }   
        
        // Skip file name.  It ends in null.
        final int FLG_FNAME  = 8;
        if ((this.flg & FLG_FNAME) == FLG_FNAME) {
            while (readByte(in, crc) != 0) {
                this.length++;
            }
        }   
        
        // Skip file comment.  It ends in null.
        final int FLG_FCOMMENT = 16;   // File comment
        if ((this.flg & FLG_FCOMMENT) == FLG_FCOMMENT) {
            while (readByte(in, crc) != 0) {
                this.length++;
            }
        }
        
        // Check optional CRC.
        final int FLG_FHCRC  = 2;
        if ((this.flg & FLG_FHCRC) == FLG_FHCRC) {
            int calcCrc = (int)(crc.getValue() & 0xffff);
            if (readShort(in, crc) != calcCrc) {
                throw new IOException("Bad header CRC");
            }
            this.length += 2;
        }
    }
    
    /**
     * Test gzip magic is next in the stream.
     * Reads two bytes.  Caller needs to manage resetting stream.
     * @param in InputStream to read.
     * @return true if found gzip magic.  False otherwise
     * or an IOException (including EOFException).
     * @throws IOException
     */
    public boolean testGzipMagic(InputStream in) throws IOException {
        return testGzipMagic(in, null);
    }
    
    /**
     * Test gzip magic is next in the stream.
     * Reads two bytes.  Caller needs to manage resetting stream.
     * @param in InputStream to read.
     * @param crc CRC to update.
     * @return true if found gzip magic.  False otherwise
     * or an IOException (including EOFException).
     * @throws IOException
     */
    public boolean testGzipMagic(InputStream in, CRC32 crc)
            throws IOException {
        return readShort(in, crc) == GZIPInputStream.GZIP_MAGIC;
    }
    
    /**
     * Read an int. 
     * 
     * We do not expect to get a -1 reading.  If we do, we throw exception.
     * Update the crc as we go.
     * 
     * @param in InputStream to read.
     * @param crc CRC to update.
     * @return int read.
     * 
     * @throws IOException
     */
    private int readInt(InputStream in, CRC32 crc) throws IOException {
        int s = readShort(in, crc);
        return ((readShort(in, crc) << 16) & 0xffff0000) | s;
    }
    
    /**
     * Read a short. 
     * 
     * We do not expect to get a -1 reading.  If we do, we throw exception.
     * Update the crc as we go.
     * 
     * @param in InputStream to read.
     * @param crc CRC to update.
     * @return Short read.
     * 
     * @throws IOException
     */
    private int readShort(InputStream in, CRC32 crc) throws IOException {
        int b = readByte(in, crc);
        return ((readByte(in, crc) << 8) & 0x00ff00) | b;
    }
    
    /**
     * Read a byte. 
     * 
     * We do not expect to get a -1 reading.  If we do, we throw exception.
     * Update the crc as we go.
     * 
     * @param in InputStream to read.
     * @return Byte read.
     * 
     * @throws IOException
     */
    protected int readByte(InputStream in) throws IOException {
            return readByte(in, null);
    }
    
    /**
     * Read a byte. 
     * 
     * We do not expect to get a -1 reading.  If we do, we throw exception.
     * Update the crc as we go.
     * 
     * @param in InputStream to read.
     * @param crc CRC to update.
     * @return Byte read.
     * 
     * @throws IOException
     */
    protected int readByte(InputStream in, CRC32 crc) throws IOException {
        int b = in.read();
        if (b == -1) {
            throw new EOFException();
        }
        if (crc != null) {
            crc.update(b);
        }
        return b & 0xff;
    }
    
    /**
     * Read a byte. 
     * 
     * We do not expect to get a -1 reading.  If we do, we throw exception.
     * Update the crc as we go.
     * 
     * @param in InputStream to read.
     * @param crc CRC to update.
     * @param buffer Buffer to read into.
     * @param offset Offset to start filling buffer at.
     * @param length How much to read.
     * @return Bytes read.
     * 
     * @throws IOException
     */
    protected int readByte(InputStream in, CRC32 crc, byte [] buffer,
                int offset, int length)
            throws IOException {
        for (int i = offset; i < length; i++) {
            buffer[offset + i] = (byte)readByte(in, crc);   
        }
        return length;
    }
    
    /**
     * @return Returns the fextra.
     */
    public byte[] getFextra() {
        return this.fextra;
    }
    
    /**
     * @return Returns the flg.
     */
    public int getFlg() {
        return this.flg;
    }
    
    /**
     * @return Returns the os.
     */
    public int getOs() {
        return this.os;
    }
    
    /**
     * @return Returns the xfl.
     */
    public int getXfl() {
        return this.xfl;
    }
    
    /**
     * @return Returns the mtime.
     */
    public int getMtime() {
        return this.mtime;
    }
    
    /**
     * @return Returns the length.
     */
    public int getLength() {
        return length;
    }
}
