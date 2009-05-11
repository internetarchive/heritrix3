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

import it.unimi.dsi.fastutil.io.RepositionableStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.ZipException;


/**
 * Subclass of GZIPInputStream that can handle a stream made of multiple
 * concatenated GZIP members/records.
 * 
 * This class is needed because GZIPInputStream only finds the first GZIP
 * member in the file even if the file is made up of multiple GZIP members.
 * 
 * <p>Takes an InputStream stream that implements
 * {@link RepositionableStream} interface so it can backup over-reads done
 * by the zlib Inflater class.
 * 
 * <p>Use the {@link #iterator()} method to get a gzip member iterator.
 * Calls to {@link Iterator#next()} returns the next gzip member in the
 * stream.  Cast return from {@link Iterator#next()} to InputStream.
 * 
 * <p>Use {@link #gzipMemberSeek(long)} to position stream before reading
 * a gzip member if doing random accessing of gzip members.  Pass it offset
 * at which gzip member starts.
 * 
 * <p>If you need to know position at which a gzip member starts, call
 * {@link #position()} just after a call to {@link Iterator#hasNext()}
 * and before you call {@link Iterator#next()}.
 * 
 * @author stack
 */
public class GzippedInputStream
extends GZIPInputStream
implements RepositionableStream {
    /**
     * Tail on gzip members (The CRC).
     */
    private static final int GZIP_TRAILER_LENGTH = 8;
    
    /**
     * Utility class used probing for gzip members in stream.
     * We need this instance to get at the readByte method.
     */
    private final GzipHeader gzipHeader = new GzipHeader();
    
    /**
     * Buffer size used skipping over gzip members.
     */
    private static final int LINUX_PAGE_SIZE = 4 * 1024;
    
    private final long initialOffset;
    
    public GzippedInputStream(InputStream is) throws IOException {
        // Have buffer match linux page size.
        this(is, LINUX_PAGE_SIZE);
    }
    
    /**
     * @param is An InputStream that implements RespositionableStream and
     * returns <code>true</code> when we call
     * {@link InputStream#markSupported()} (Latter is needed so can setup
     * an {@link Iterator} against the Gzip stream).
     * @param size Size of blocks to use reading.
     * @throws IOException
     */
    public GzippedInputStream(final InputStream is, final int size)
    throws IOException {
        super(checkStream(is), size);
        if (!is.markSupported()) {
        	throw new IllegalArgumentException("GzippedInputStream requires " +
        		"a markable stream");
        }
        if (!(is instanceof RepositionableStream)) {
        	throw new IllegalArgumentException("GzippedInputStream requires " +
    		"a stream that implements RepositionableStream");
        }
        // We need to calculate the absolute offset of the current
        // GZIP Member.  Its almost always going to be zero but not
        // always (We may have been passed a stream that is already part
        // ways through a stream of GZIP Members).  So, getting
        // absolute offset is not exactly straight-forward. The super
        // class, GZIPInputStream on construction reads in the GZIP Header
        // which is a pain because I then do not know the absolute offset
        // at which the GZIP record began.  So, the call above to checkStream()
        // marked the stream before passing it to the super calls.  Then
        // below we get current postion at just past the GZIP Header, call
        // reset so we go back to the absolute start of the GZIP Member in
        // the file, record the offset for later should we need to start
        // over again in this file -- i.e. we're asked to get an iterator
        // from Record zero on -- then we move the file position to just
        // after the GZIP Header again so we're again aligned for inflation
        // of the current record.
        long afterGZIPHeader = ((RepositionableStream)is).position();
        is.reset();
        this.initialOffset = ((RepositionableStream)is).position();
        ((RepositionableStream)is).position(afterGZIPHeader);
    }
    
    protected static InputStream checkStream(final InputStream is)
    throws IOException {
        if (is instanceof RepositionableStream) {
        	// See note above in constructor on why the mark here.
        	// Also minimal gzip header is 10.  IA GZIP Headers are 20 bytes.
        	// Multiply by 4 in case extra info in the header.
        	is.mark(GzipHeader.MINIMAL_GZIP_HEADER_LENGTH * 4);
        	return is;
        }
        throw new IOException("Passed stream does not" +
            " implement PositionableStream");
    }
    
    /**
     * Exhaust current GZIP member content.
     * Call this method when you think you're on the end of the
     * GZIP member.  It will clean out any dross.
     * @param ignore Character to ignore counting characters (Usually
     * trailing new lines).
     * @return Count of characters skipped over.
     * @throws IOException
     */
    public long gotoEOR(int ignore) throws IOException {
        long bytesSkipped = 0;
        if (this.inf.getTotalIn() <= 0) {
            return bytesSkipped;
        }
        if (!this.inf.finished()) {
            int read = 0;
            while ((read = read()) != -1) {
                if ((byte)read == (byte)ignore) {
                    continue;
                }
                bytesSkipped = gotoEOR() + 1;
                break;
            }
        }
        return bytesSkipped;
    }
    
    /**
     * Exhaust current GZIP member content.
     * Call this method when you think you're on the end of the
     * GZIP member.  It will clean out any dross.
     * @return Count of characters skipped over.
     * @throws IOException
     */
    public long gotoEOR() throws IOException {
        long bytesSkipped = 0;
        if (this.inf.getTotalIn() <= 0) {
            return bytesSkipped;
        }
        while(!this.inf.finished()) {
            bytesSkipped += skip(Long.MAX_VALUE);
        }
        return bytesSkipped;
    }
    
    /**
     * Returns a GZIP Member Iterator.
     * Has limitations. Can only get one Iterator per instance of this class;
     * you must get new instance if you want to get Iterator again.
     * @return Iterator over GZIP Members.
     */
    public Iterator<GzippedInputStream> iterator() {
        final Logger logger = Logger.getLogger(this.getClass().getName());
        
        try {
            // We know its a RepositionableStream else we'd have failed
        	// construction.  On iterator construction, set file back to
        	// initial position so we're ready to read GZIP Members
        	// (May not always work dependent on how the
        	// RepositionableStream was implemented).
            ((RepositionableStream)this.in).position(this.initialOffset);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new Iterator<GzippedInputStream>() {
            private GzippedInputStream compressedStream =
                GzippedInputStream.this;
            
            public boolean hasNext() {
                try {
                    gotoEOR();
                } catch (IOException e) {
                    if ((e instanceof ZipException) ||
                        (e.getMessage() != null &&
                         e.getMessage().startsWith("Corrupt GZIP trailer"))) {
                        // Try skipping end of bad record; try moving to next.
                        logger.info("Skipping exception " + e.getMessage());
                    } else {
                        throw new RuntimeException(e);
                    }
                }
                return moveToNextGzipMember();
            }
            
            /**
             * @return An InputStream onto a GZIP Member.
             */
            public GzippedInputStream next() {
                try {
                    gzipMemberSeek();
                } catch (IOException e) {
                    throw new RuntimeException("Failed move to EOR or " +
                        "failed header read: " + e.getMessage());
                }
                return this.compressedStream;
            }
            
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };   
    }
    
    /**
     * @return True if we found another record in the stream.
     */
    protected boolean moveToNextGzipMember() {
        boolean result = false;
        // Move to the next gzip member, if there is one, positioning
        // ourselves by backing up the stream so we reread any inflater
        // remaining bytes. Then add 8 bytes to get us past the GZIP
        // CRC trailer block that ends all gzip members.
        try {
            RepositionableStream ps = (RepositionableStream)getInputStream();
            // 8 is sizeof gzip CRC block thats on tail of gzipped
            // record. If remaining is < 8 then experience indicates
            // we're seeking past the gzip header -- don't backup the
            // stream.
            if (getInflater().getRemaining() > GZIP_TRAILER_LENGTH) {
                ps.position(position() - getInflater().getRemaining() +
                    GZIP_TRAILER_LENGTH);
            }
            for (int read = -1, headerRead = 0; true; headerRead = 0) {
                // Give a hint to underlying stream that we're going to want to
                // do some backing up.
                getInputStream().mark(3);
                if ((read = getInputStream().read()) == -1) {
                    break;
                }
                if(compareBytes(read, GZIPInputStream.GZIP_MAGIC)) {
                    headerRead++;
                    if ((read = getInputStream().read()) == -1) {
                    	break;
                    }
                    if(compareBytes(read, GZIPInputStream.GZIP_MAGIC >> 8)) {
                        headerRead++;
                        if ((read = getInputStream().read()) == -1) {
                        	break;
                        }
                        if (compareBytes(read, Deflater.DEFLATED)) {
                            headerRead++;
                            // Found gzip header. Backup the stream the
                            // bytes we just found and set result true.
                            getInputStream().reset();
                            result = true;
                            break;
                        }
                    }
                    // Didn't find gzip header.  Reset stream but one byte
                    // futher on then redo header tests.
                    ps.position(ps.position() - headerRead);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed i/o: " + e.getMessage());
        }
        return result;
    }
    
    protected boolean compareBytes(final int a, final int b) {
    	return ((byte)(a & 0xff)) == ((byte)(b & 0xff));
    }
  
    protected Inflater getInflater() {
        return this.inf;
    }
    
    protected InputStream getInputStream() {
        return this.in;
    }
    
    protected GzipHeader getGzipHeader() {
        return this.gzipHeader;
    }
    
    /**
     * Move to next gzip member in the file.
     */
    protected void resetInflater() {
        this.eos = false;
        this.inf.reset();
    }
    
    /**
     * Read in the gzip header.
     * @throws IOException
     */
    protected void readHeader() throws IOException {
        new GzipHeader(this.in);
        // Reset the crc for subsequent reads.
        this.crc.reset();
    }

    /**
     * Seek to passed offset.
     * 
     * After positioning the stream, it resets the inflater.
     * Assumption is that public use of this method is only
     * to position stream at start of a gzip member.
     * 
     * @param position Absolute position of a gzip member start.
     * @throws IOException
     */
    public void position(long position) throws IOException {
        ((RepositionableStream)this.in).position(position);
        resetInflater();
    }

    public long position() throws IOException {
       return  ((RepositionableStream)this.in).position();
    }
    
    /**
     * Seek to a gzip member.
     * 
     * Moves stream to new position, resets inflater and reads in the gzip
     * header ready for subsequent calls to read.
     * 
     * @param position Absolute position of a gzip member start.
     * @throws IOException
     */
    public void gzipMemberSeek(long position) throws IOException {
        position(position);
        readHeader();
    }
    
    public void gzipMemberSeek() throws IOException {
        gzipMemberSeek(position());
    }
    
    /**
     * Gzip passed bytes.
     * Use only when bytes is small.
     * @param bytes What to gzip.
     * @return A gzip member of bytes.
     * @throws IOException
     */
    public static byte [] gzip(byte [] bytes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOS = new GZIPOutputStream(baos);
        gzipOS.write(bytes, 0, bytes.length);
        gzipOS.close();
        return baos.toByteArray();
    }
    
    /**
     * Tests passed stream is GZIP stream by reading in the HEAD.
     * Does reposition of stream when done.
     * @param rs An InputStream that is Repositionable.
     * @return True if compressed stream.
     * @throws IOException
     */
    public static boolean isCompressedRepositionableStream(
            final RepositionableStream rs)
    throws IOException {
        boolean result = false;
        long p = rs.position();
        try {
            result = isCompressedStream((InputStream)rs);
        } finally {
            rs.position(p);
        }
        return result; 
    }
    
    /**
     * Tests passed stream is gzip stream by reading in the HEAD.
     * Does not reposition stream when done.
     * @param is An InputStream.
     * @return True if compressed stream.
     * @throws IOException
     */
    public static boolean isCompressedStream(final InputStream is)
    throws IOException {
        try {
            new GzipHeader(is);
        } catch (NoGzipMagicException e) {
            return false;
        }
        return true;
    }
}
