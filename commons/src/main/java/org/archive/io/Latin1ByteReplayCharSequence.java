/* ByteReplayCharSequenceFactory
 *
 * (Re)Created on Dec 21, 2006
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
import java.io.RandomAccessFile;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.util.DevUtils;

/**
 * Provides a (Replay)CharSequence view on recorded stream bytes (a prefix
 * buffer and overflow backing file).
 *
 * Assumes the byte stream is ISO-8859-1 text, taking advantage of the fact 
 * that each byte in the stream corresponds to a single unicode character with
 * the same numerical value as the byte. 
 *
 * <p>Uses a wraparound rolling buffer of the last windowSize bytes read
 * from disk in memory; as long as the 'random access' of a CharSequence
 * user stays within this window, access should remain fairly efficient.
 * (So design any regexps pointed at these CharSequences to work within
 * that range!)
 *
 * <p>When rereading of a location is necessary, the whole window is
 * recentered around the location requested. (TODO: More research
 * into whether this is the best strategy.)
 *
 * <p>An implementation of a ReplayCharSequence done with ByteBuffers -- one
 * to wrap the passed prefix buffer and the second, a memory-mapped
 * ByteBuffer view into the backing file -- was consistently slower: ~10%.
 * My tests did the following. Made a buffer filled w/ regular content.
 * This buffer was used as the prefix buffer.  The buffer content was
 * written MULTIPLER times to a backing file.  I then did accesses w/ the
 * following pattern: Skip forward 32 bytes, then back 16 bytes, and then
 * read forward from byte 16-32.  Repeat.  Though I varied the size of the
 * buffer to the size of the backing file,from 3-10, the difference of 10%
 * or so seemed to persist.  Same if I tried to favor get() over get(index).
 * I used a profiler, JMP, to study times taken (St.Ack did above comment).
 *
 * <p>TODO determine in memory mapped files is better way to do this;
 * probably not -- they don't offer the level of control over
 * total memory used that this approach does.
 *
 * @author Gordon Mohr
 * @version $Revision$, $Date$
 */
class Latin1ByteReplayCharSequence implements ReplayCharSequence {

    protected static Logger logger =
        Logger.getLogger(Latin1ByteReplayCharSequence.class.getName());

    /**
     * Buffer that holds the first bit of content.
     *
     * Once this is exhausted we go to the backing file.
     */
    private byte[] prefixBuffer;

    /**
     * Total length of character stream to replay minus the HTTP headers
     * if present.
     *
     * Used to find EOS.
     */
    protected int length;

    /**
     * Absolute length of the stream.
     *
     * Includes HTTP headers.  Needed doing calc. in the below figuring
     * how much to load into buffer.
     */
    private int absoluteLength = -1;

    /**
     * Buffer window on to backing file.
     */
    private byte[] wraparoundBuffer;

    /**
     * Absolute index into underlying bytestream where wrap starts.
     */
    private int wrapOrigin;

    /**
     * Index in wraparoundBuffer that corresponds to wrapOrigin
     */
    private int wrapOffset;

    /**
     * Name of backing file we go to when we've exhausted content from the
     * prefix buffer.
     */
    private String backingFilename;

    /**
     * Random access to the backing file.
     */
    private RandomAccessFile raFile;

    /**
     * Offset into prefix buffer at which content beings.
     */
    private int contentOffset;

    /**
     * 8-bit encoding used reading single bytes from buffer and
     * stream.
     */
    @SuppressWarnings("unused")
    private static final String DEFAULT_SINGLE_BYTE_ENCODING =
        "ISO-8859-1";


    /**
     * Constructor.
     *
     * @param buffer In-memory buffer of recordings prefix.  We read from
     * here first and will only go to the backing file if <code>size</code>
     * requested is greater than <code>buffer.length</code>.
     * @param size Total size of stream to replay in bytes.  Used to find
     * EOS. This is total length of content including HTTP headers if
     * present.
     * @param responseBodyStart Where the response body starts in bytes.
     * Used to skip over the HTTP headers if present.
     * @param backingFilename Path to backing file with content in excess of
     * whats in <code>buffer</code>.
     *
     * @throws IOException
     */
    public Latin1ByteReplayCharSequence(byte[] buffer, long size,
            long responseBodyStart, String backingFilename)
        throws IOException {

        this.length = (int)(size - responseBodyStart);
        this.absoluteLength = (int)size;
        this.prefixBuffer = buffer;
        this.contentOffset = (int)responseBodyStart;

        // If amount to read is > than what is in our prefix buffer, then
        // open the backing file.
        if (size > buffer.length) {
            this.backingFilename = backingFilename;
            this.raFile = new RandomAccessFile(backingFilename, "r");
            this.wraparoundBuffer = new byte[this.prefixBuffer.length];
            this.wrapOrigin = this.prefixBuffer.length;
            this.wrapOffset = 0;
            loadBuffer();
        }
    }

    /**
     * @return Length of characters in stream to replay.  Starts counting
     * at the HTTP header/body boundary.
     */
    public int length() {
        return this.length;
    }

    /**
     * Get character at passed absolute position.
     *
     * Called by {@link #charAt(int)} which has a relative index into the
     * content, one that doesn't account for HTTP header if present.
     *
     * @param index Index into content adjusted to accomodate initial offset
     * to get us past the HTTP header if present (i.e.
     * {@link #contentOffset}).
     *
     * @return Characater at offset <code>index</code>.
     */
    public char charAt(int index) {
        int c = -1;
        // Add to index start-of-content offset to get us over HTTP header
        // if present.
        index += this.contentOffset;
        if (index < this.prefixBuffer.length) {
            // If index is into our prefix buffer.
            c = this.prefixBuffer[index];
        } else if (index >= this.wrapOrigin &&
            (index - this.wrapOrigin) < this.wraparoundBuffer.length) {
            // If index is into our buffer window on underlying backing file.
            c = this.wraparoundBuffer[
                    ((index - this.wrapOrigin) + this.wrapOffset) %
                        this.wraparoundBuffer.length];
        } else {
            // Index is outside of both prefix buffer and our buffer window
            // onto the underlying backing file.  Fix the buffer window
            // location.
            c = faultCharAt(index);
        }
        // Stream is treated as single byte.  Make sure characters returned
        // are not negative.
        return (char)(c & 0xff);
    }

    /**
     * Get a character that's outside the current buffers.
     *
     * will cause the wraparoundBuffer to be changed to
     * cover a region including the index
     *
     * if index is higher than the highest index in the
     * wraparound buffer, buffer is moved forward such
     * that requested char is last item in buffer
     *
     * if index is lower than lowest index in the
     * wraparound buffer, buffet is reset centered around
     * index
     *
     * @param index Index of character to fetch.
     * @return A character that's outside the current buffers
     */
    private int faultCharAt(int index) {
        if(Thread.interrupted()) {
            throw new RuntimeException("thread interrupted");
        }
        if(index >= this.wrapOrigin + this.wraparoundBuffer.length) {
            // Moving forward
            while (index >= this.wrapOrigin + this.wraparoundBuffer.length)
            {
                // TODO optimize this
                advanceBuffer();
            }
            return charAt(index - this.contentOffset);
        }
        // Moving backward
        recenterBuffer(index);
        return charAt(index - this.contentOffset);
    }

    /**
     * Move the buffer window on backing file back centering current access
     * position in middle of window.
     *
     * @param index Index of character to access.
     */
    private void recenterBuffer(int index) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Recentering around " + index + " in " +
                this.backingFilename);
        }
        this.wrapOrigin = index - (this.wraparoundBuffer.length / 2);
        if(this.wrapOrigin < this.prefixBuffer.length) {
            this.wrapOrigin = this.prefixBuffer.length;
        }
        this.wrapOffset = 0;
        loadBuffer();
    }

    /**
     * Load from backing file into the wrapper buffer.
     */
    private void loadBuffer()
    {
        long len = -1;
        try {
            len = this.raFile.length();
            this.raFile.seek(this.wrapOrigin - this.prefixBuffer.length);
            this.raFile.readFully(this.wraparoundBuffer, 0,
                Math.min(this.wraparoundBuffer.length,
                     this.absoluteLength - this.wrapOrigin));
        }

        catch (IOException e) {
            // TODO convert this to a runtime error?
            DevUtils.logger.log (
                Level.SEVERE,
                "raFile.seek(" +
                (this.wrapOrigin - this.prefixBuffer.length) +
                ")\n" +
                "raFile.readFully(wraparoundBuffer,0," +
                (Math.min(this.wraparoundBuffer.length,
                    this.length - this.wrapOrigin )) +
                ")\n"+
                "raFile.length()" + len + "\n" +
                DevUtils.extraInfo(),
                e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Roll the wraparound buffer forward one position
     */
    private void advanceBuffer() {
        try {
            this.wraparoundBuffer[this.wrapOffset] =
                (byte)this.raFile.read();
            this.wrapOffset++;
            this.wrapOffset %= this.wraparoundBuffer.length;
            this.wrapOrigin++;
        } catch (IOException e) {
            DevUtils.logger.log(Level.SEVERE, "advanceBuffer()" +
                DevUtils.extraInfo(), e);
            throw new RuntimeException(e);
        }
    }

    public CharSequence subSequence(int start, int end) {
        return new CharSubSequence(this, start, end);
    }

    /**
     * Cleanup resources.
     *
     * @exception IOException Failed close of random access file.
     */
    public void close() throws IOException
    {
        this.prefixBuffer = null;
        if (this.raFile != null) {
            this.raFile.close();
            this.raFile = null;
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#finalize()
     */
    protected void finalize() throws Throwable
    {
        super.finalize();
        close();
    }
    
    /**
     * Convenience method for getting a substring. 
     * @deprecated please use subSequence() and then toString() directly 
     */
    public String substring(int offset, int len) {
        return subSequence(offset, offset+len).toString();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        StringBuilder sb = new StringBuilder(this.length());
        sb.append(this);
        return sb.toString();
    }
}