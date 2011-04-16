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
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.zip.Inflater;
import java.util.zip.ZipException;

import org.archive.util.zip.OpenJDK7GZIPInputStream;

import com.google.common.io.ByteStreams;
import com.google.common.io.CountingInputStream;

/**
 * A replacement for GZIPInputStream; offers GZIP decompression, without any
 * artificial stop after the first member in a concatenated series (in 
 * pre-JDK6u23), and offers direct access to discovered GZIP member 
 * boundaries (in compressed offsets) via the getMemberNumber(), 
 * getCurrentMemberStart(), getCurrentMemberEnd() accessors, both pre- and 
 * post- JDK6u23 (but see below for caveat about getCurrentMemberEnd()). 
 * 
 * (This replaces our previous workaround, 'GzippedInputStream', for
 * pre-JDK6u23 GZIPInputStream behavior.)
 * 
 * By default, will read straight through members, returning all uncompressed
 * data from concatenated compressed members as one stream, per the 
 * JDK6u23-and-higher behavior. The data returned from a single 
 * read() will not straddle a member boundary, *but* only after reading
 * the first byte of the next member can certainty be offered as to 
 * whether the previous member ended. Thus, in this default mode, until
 * the end of all input, the getAtMemberEnd() method will always return 
 * false, and getCurrentMemberEnd() will always return -1, because any 
 * read that discovered a definitive member-end will have begun the next
 * member. In this mode, member-ends should be deduced by watching the 
 * increment of getMemberNumber(), and using the start of the current 
 * record as the (exclusive) end-position of the previous record. 
 * 
 * The setEofEachMember() method may be used to change behavior to mimic that 
 * of pre-6u23 GZIPInputStream: reaching the end of a GZIP member will result
 * in a returned EOF. When receiving this EOF, getAtMemberEnd() will return
 * true and getCurrentMemberEnd() will return the (exclusive) member-end
 * position. Calling nextMember() after receiving an EOF will allow reading
 * to proceed into the next member (if any). 
 * 
 *  @contributor gojomo
 */
public class GZIPMembersInputStream extends OpenJDK7GZIPInputStream {
    long memberNumber = 0; 
    long holdAtMemberNumber = Long.MAX_VALUE;
    long currentMemberStart = 0;
    long currentMemberEnd = -1; 
    InputStream originalIn;
    
    public GZIPMembersInputStream(InputStream in) throws IOException {
        this(in,512);
    }
    
    public GZIPMembersInputStream(InputStream in, int size)
            throws IOException {
        super(countingStream(in,size), size);
        originalIn = in;
    }

    /**
     * A CountingInputStream is inserted to read compressed-offsets. 
     * 
     * @param in stream to wrap
     * @param lookback tolerance of initial mark
     * @return original stream wrapped in CountingInputStream
     * @throws IOException
     */
    protected static InputStream countingStream(InputStream in, int lookback) throws IOException {
        CountingInputStream cin = new CountingInputStream(in);
        cin.mark(lookback); 
        return cin;
    }

    protected void updateInnerMark() {
        this.in.mark(buf.length);
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if(currentMemberEnd>0) {
            if(memberNumber>=holdAtMemberNumber) {
                // only advance if allowed
                return -1; 
            }
            // note read past member boundary
            memberNumber++; 
            currentMemberStart = currentMemberEnd; 
            currentMemberEnd = -1; 
        }
        return super.read(buf, off, len);
    }
    
    @Override
    protected boolean readTrailer() throws IOException {
        int c = inf.getRemaining();
        currentMemberEnd = ((CountingInputStream)in).getCount()-(c-8); 
//        return super.readTrailer();
// REIMPLEMENTED TO FIX MISUSE OF available()
        InputStream in = this.in;
        int n = inf.getRemaining();
        if (n > 0) {
            in = new SequenceInputStream(
                        new ByteArrayInputStream(buf, len - n, n), in);
        }
        // Uses left-to-right evaluation order
        if ((readUInt(in) != crc.getValue()) ||
            // rfc1952; ISIZE is the input size modulo 2^32
            (readUInt(in) != (inf.getBytesWritten() & 0xffffffffL)))
            throw new ZipException("Corrupt GZIP trailer");

        // always try concatenated case; EOF or other IOException
        // will let us know if we're wrong
        int m = 8;                  // this.trailer
        try {
            m += readHeader(in);    // next.header
        } catch (IOException ze) {
            return true;  // ignore any malformed, do nothing
        }
        inf.reset();
        if (n > m)
            inf.setInput(buf, len - n + m, n - m);
        return false;
    }

    /**
     * Seek forward to a particular offset in the compressed stream. Note
     * that after any seek/skip the memberNumbers may not reflect a member's
     * true ordinal position from the beginning of the stream. 
     * 
     * @param position target position
     * @throws IOException
     */
    public void compressedSeek(long position) throws IOException {
        in.reset(); 
        long count = ((CountingInputStream)in).getCount();
        long delta = position - count;
        if(delta<0) {
            throw new IllegalArgumentException("can't seek backwards: seeked "+position+" already at "+count); 
        }
        compressedSkip(delta);
    }
    
    /**
     * Skip forward the given number of bytes in the compressed stream. Note
     * that after any seek/skip the memberNumbers may not reflect a member's
     * true ordinal position from the beginning of the stream. 
     * 
     * @param offset bytes to skip
     * @throws IOException
     * @throws EOFException 
     */
    public void compressedSkip(long offset) throws IOException {
        ByteStreams.skipFully(in, offset);
        updateInnerMark();
        currentMemberStart = ((CountingInputStream)in).getCount(); 
        currentMemberEnd = -1; 
        startNewMember();
    }
    
    protected void startNewMember() throws IOException {
        new GzipHeader(in); // consume header
        inf.reset(); 
        crc.reset(); 
        eos = false;
    }

    /**
     * Test whether last read resulted in reaching the exact end of one GZIP
     * member. 
     * 
     * @return true if exactly at member end
     */
    public boolean getAtMemberEnd() {
        return currentMemberEnd>0;
    }
    
    /**
     * Get the ordinal number, starting at zero, of the currently-being-read
     * GZIP member, counting from the creation of this stream. If reading
     * straight through, this will be an accurate index relative to all 
     * members in the underlying stream. If any seeks/skips have been used, 
     * the number will only be relative to the members actually read.
     * 
     * @return ordinal number of member-in-progres
     */
    public long getMemberNumber() {
        return memberNumber;
    }
    
    /**
     * Get the compressed offset where the current member began.
     * 
     * @return position in compressed stream where current member began
     */
    public long getCurrentMemberStart() {
        return currentMemberStart;
    }
    
    
    /**
     * Get the compressed offset where the current, just-completed member
     * ends. Only accurate after the read which finishes a member (when 
     * getAtMemberEnd returns true). Otherwise, returns -1 to indicate 
     * not-yet-found.
     * 
     * @return position in compressed stream where member just finished, or -1
     * if member end not yet reached
     */
    public long getCurrentMemberEnd() {
        return currentMemberEnd;
    }
    
    /**
     * Set stream behavior to match JDK 6u22-and-earlier behavior, where 
     * reaching the end of any one GZIP member results in EOFs from all 
     * read()s as if no more data is available. (However, nextMember() may
     * be used to advance to the next member.)
     * 
     * @param eofPerMember true to set EOF-each-member behavior
     */
    public void setEofEachMember(boolean eofPerMember) {
        holdAtMemberNumber = eofPerMember ? memberNumber : Long.MAX_VALUE;
    }
    
    /**
     * Advance to next member (if the stream has been set to return EOF at the 
     * end of each member). Each call before reaching the end of a member will 
     * cause one additional member boundary to be passed. (Has no effect if not
     * in EOF-each-member mode.)
     */
    public void nextMember() {
        if(holdAtMemberNumber<Long.MAX_VALUE) {
            holdAtMemberNumber++;
        }
    }
    
    /**
     * Helpful for testing/debugging
     * 
     * @return Inflater
     */
    public Inflater getInflater() {
        return inf;
    }

    /**
     * Get an Iterator-ish interface to each member in turn. Has the effect of
     * putting stream in EOF-each-member mode; thereafter reading should occur
     * through the stream returned by the iterator's next(). Reading of one 
     * stream from next() should finish (reaching EOF) before the iterator's
     * hasNext() or next() is called.  
     * 
     * @return Iterator<GZIPMembersInputStream> of 
     * @deprecated for backward compatibility; better to use direct facilities in future
     */
    public Iterator<GZIPMembersInputStream> memberIterator() {
        return new GZIPEnvelopeIterator(); 
    }
       
    /**
     * Provides iterator-ish interface to members in a concatenated multi-member
     * GZIP stream for backward compatibility with our prior workaround. Not 
     * exactly like a real iterator: hasNext() will only return an accurate 
     * result when the stream returned by the previous next() is read until EOF. 
     * Previous next() values can not be retained/reused (they are in fact the
     * same object as subsequent next() returns.)
     */
    public class GZIPEnvelopeIterator implements
    Iterator<GZIPMembersInputStream> {
        {
            setEofEachMember(true);
        }
        
        @Override
        public boolean hasNext() {
            // because readTrailer also reads into next header 
            // resetting inflater when there's more content, this works
            return !inf.finished();
        }
    
        @Override
        public GZIPMembersInputStream next() {
            if(getAtMemberEnd()) {
                nextMember();
            }
            if(hasNext()) {
                return GZIPMembersInputStream.this;
            } else {
                throw new NoSuchElementException(); 
            }
        }
    
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}