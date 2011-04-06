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

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * An output stream that records all writes to wrapped output
 * stream.
 *
 * A RecordingOutputStream can be wrapped around any other
 * OutputStream to record all bytes written to it.  You can
 * then request a ReplayInputStream to read those bytes.
 *
 * <p>The RecordingOutputStream uses an in-memory buffer and
 * backing disk file to allow it to record streams of
 * arbitrary length limited only by available disk space.
 *
 * <p>As long as the stream recorded is smaller than the
 * in-memory buffer, no disk access will occur.
 *
 * <p>Recorded content can be recovered as a ReplayInputStream
 * (via getReplayInputStream() or, for only the content after
 * the content-begin-mark is set, getContentReplayInputStream() )
 * or as a ReplayCharSequence (via getReplayCharSequence()).
 *
 * <p>This class is also used as a straight output stream
 * by {@link RecordingInputStream} to which it records all reads.
 * {@link RecordingInputStream} is exploiting the file backed buffer
 * facility of this class passing <code>null</code> for the stream
 * to wrap.  TODO: Make a FileBackedOutputStream class that is
 * subclassed by RecordingInputStream.
 *
 * @author gojomo
 *
 */
public class RecordingOutputStream extends OutputStream {
    protected static Logger logger =
        Logger.getLogger(RecordingOutputStream.class.getName());
    
    /**
     * Size of recording.
     *
     * Later passed to ReplayInputStream on creation.  It uses it to know when
     * EOS.
     */
    protected long size = 0;

    protected String backingFilename;
    protected OutputStream diskStream = null;

    /**
     * Buffer we write recordings to.
     *
     * We write all recordings here first till its full.  Thereafter we
     * write the backing file.
     */
    private byte[] buffer;

    /** current virtual position in the recording */
    private long position;
    
    /** flag to disable recording */
    private boolean recording;
    
    /**
     * Reusable buffer for FastBufferedOutputStream
     */
    protected byte[] bufStreamBuf = 
        new byte [ FastBufferedOutputStream.DEFAULT_BUFFER_SIZE ];
    
    /**
     * True if we're to digest content.
     */
    private boolean shouldDigest = false;
 
    /**
     * Digest instance.
     */
    private MessageDigest digest = null;

    /**
     * Define for SHA1 algarithm.
     */
    private static final String SHA1 = "SHA1";

    /**
     * Maximum amount of header material to accept without the content
     * body beginning -- if more, throw a RecorderTooMuchHeaderException.
     * TODO: make configurable? make smaller?
     */
    protected static final long MAX_HEADER_MATERIAL = 1024*1024; // 1MB
    
    // configurable max length, max time limits
    /** maximum length of material to record before throwing exception */ 
    protected long maxLength = Long.MAX_VALUE;
    /** maximum time to record before throwing exception */ 
    protected long timeoutMs = Long.MAX_VALUE;
    /** maximum rate to record (adds delays to hit target rate) */ 
    protected long maxRateBytesPerMs = Long.MAX_VALUE;
    /** time recording begins for timeout, rate calculations */ 
    protected long startTime = Long.MAX_VALUE;
    
    /**
     * When recording HTTP, where the content-body starts.
     */
    protected long messageBodyBeginMark;

    /**
     * Stream to record.
     */
    private OutputStream out = null;

    // mark/reset support 
    /** furthest position reached before any reset()s */
    private long maxPosition = 0;
    /** remembered position to reset() to */ 
    private long markPosition = 0; 

    /**
     * Create a new RecordingOutputStream.
     *
     * @param bufferSize Buffer size to use.
     * @param backingFilename Name of backing file to use.
     */
    public RecordingOutputStream(int bufferSize, String backingFilename) {
        this.buffer = new byte[bufferSize];
        this.backingFilename = backingFilename;
        recording = true;
    }

    /**
     * Wrap the given stream, both recording and passing along any data written
     * to this RecordingOutputStream.
     *
     * @throws IOException If failed creation of backing file.
     */
    public void open() throws IOException {
        this.open(null);
    }

    /**
     * Wrap the given stream, both recording and passing along any data written
     * to this RecordingOutputStream.
     *
     * @param wrappedStream Stream to wrap.  May be null for case where we
     * want to write to a file backed stream only.
     *
     * @throws IOException If failed creation of backing file.
     */
    public void open(OutputStream wrappedStream) throws IOException {
        if(isOpen()) {
            // error; should not be opening/wrapping in an unclosed 
            // stream remains open
            throw new IOException("ROS already open for "
                    +Thread.currentThread().getName());
        }
        this.out = wrappedStream;
        this.position = 0;
        this.markPosition = 0;
        this.maxPosition = 0; 
        this.size = 0;
        this.messageBodyBeginMark = -1;
        // ensure recording turned on
        this.recording = true;
        // Always begins false; must use startDigest() to begin
        this.shouldDigest = false;
        if (this.diskStream != null) {
            closeDiskStream();
        }
        if (this.diskStream == null) {
            // TODO: Fix so we only make file when its actually needed.
            FileOutputStream fis = new FileOutputStream(this.backingFilename);
            
            this.diskStream = new RecyclingFastBufferedOutputStream(fis, bufStreamBuf);
        }
        startTime = System.currentTimeMillis();
    }

    public void write(int b) throws IOException {
        if(position<maxPosition) {
            // revisiting previous content; do nothing but advance position
            position++;
            return; 
        }
        if(recording) {
            record(b);
        }
        if (this.out != null) {
            this.out.write(b);
        }
        checkLimits();
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if(position < maxPosition) {
            if(position+len<=maxPosition) {
                // revisiting; do nothing but advance position
                position += len;
                return;
            }
            // consume part of the array doing nothing but advancing position
            long consumeRange = maxPosition - position; 
            position += consumeRange;
            off += consumeRange;
            len -= consumeRange; 
        }
        if(recording) {
            record(b, off, len);
        }
        if (this.out != null) {
            this.out.write(b, off, len);
        }
        checkLimits();
    }
    
    /**
     * Check any enforced limits. 
     */
    protected void checkLimits() throws RecorderIOException {
        // too much material before finding end of headers? 
        if (messageBodyBeginMark<0) {
            // no mark yet
            if(position>MAX_HEADER_MATERIAL) {
                throw new RecorderTooMuchHeaderException();
            }
        }
        // overlong?
        if(position>maxLength) {
            throw new RecorderLengthExceededException(); 
        }
        // taking too long? 
        long duration = System.currentTimeMillis() - startTime; 
        duration = Math.max(duration,1); // !divzero
        if(duration>timeoutMs) {
            throw new RecorderTimeoutException(); 
        }
        // need to throttle reading to hit max configured rate? 
        if(position/duration > maxRateBytesPerMs) {
            long desiredDuration = position / maxRateBytesPerMs;
            try {
                Thread.sleep(desiredDuration-duration);
            } catch (InterruptedException e) {
                logger.log(Level.WARNING,
                        "bandwidth throttling sleep interrupted", e);
            } 
        }
    }

    /**
     * Record the given byte for later recovery
     *
     * @param b Int to record.
     *
     * @exception IOException Failed write to backing file.
     */
    private void record(int b) throws IOException {
        if (this.shouldDigest) {
            this.digest.update((byte)b);
        }
        if (this.position >= this.buffer.length) {
            // TODO: Its possible to call write w/o having first opened a
            // stream.  Protect ourselves against this.
            assert this.diskStream != null: "Diskstream is null";
            this.diskStream.write(b);
        } else {
            this.buffer[(int) this.position] = (byte) b;
        }
        this.position++;
    }

    /**
     * Record the given byte-array range for recovery later
     *
     * @param b Buffer to record.
     * @param off Offset into buffer at which to start recording.
     * @param len Length of buffer to record.
     *
     * @exception IOException Failed write to backing file.
     */
    private void record(byte[] b, int off, int len) throws IOException {
        if(this.shouldDigest) {
            assert this.digest != null: "Digest is null.";
            this.digest.update(b, off, len);
        }
        tailRecord(b, off, len);
    }

    /**
     * Record without digesting.
     * 
     * @param b Buffer to record.
     * @param off Offset into buffer at which to start recording.
     * @param len Length of buffer to record.
     *
     * @exception IOException Failed write to backing file.
     */
    private void tailRecord(byte[] b, int off, int len) throws IOException {
        if(this.position >= this.buffer.length){
            // TODO: Its possible to call write w/o having first opened a
            // stream.  Lets protect ourselves against this.
            if (this.diskStream == null) {
                throw new IOException("diskstream is null");
            }
            this.diskStream.write(b, off, len);
            this.position += len;
        } else {
            assert this.buffer != null: "Buffer is null";
            int toCopy = (int)Math.min(this.buffer.length - this.position, len);
            assert b != null: "Passed buffer is null";
            System.arraycopy(b, off, this.buffer, (int)this.position, toCopy);
            this.position += toCopy;
            // TODO verify these are +1 -1 right
            if (toCopy < len) {
                tailRecord(b, off + toCopy, len - toCopy);
            }
        }
    }

    public void close() throws IOException {
        if(messageBodyBeginMark<0) {
            // if unset, consider 0 posn as content-start
            // (so that a -1 never survives to replay step)
            messageBodyBeginMark = 0;
        }
        if (this.out != null) {
            this.out.close();
            this.out = null;
        }
        closeRecorder();
    }
    
    protected synchronized void closeDiskStream()
    throws IOException {
        if (this.diskStream != null) {
            this.diskStream.close();
            this.diskStream = null;
        }
    }

    public void closeRecorder() throws IOException {
        recording = false;
        closeDiskStream(); // if any
        // This setting of size is important.  Its passed to ReplayInputStream
        // on creation.  It uses it to know EOS.
        if (this.size == 0) {
            this.size = this.position;
        }
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#flush()
     */
    public void flush() throws IOException {
        if (this.out != null) {
            this.out.flush();
        }
        if (this.diskStream != null) {
            this.diskStream.flush();
        }
    }

    public ReplayInputStream getReplayInputStream() throws IOException {
        return getReplayInputStream(0);
    }
    
    public ReplayInputStream getReplayInputStream(long skip) throws IOException {
        // If this method is being called, then assumption must be that the
        // stream is closed. If it ain't, then the stream gotten won't work
        // -- the size will zero so any attempt at a read will get back EOF.
        assert this.out == null: "Stream is still open.";
        ReplayInputStream replay = new ReplayInputStream(this.buffer, 
                this.size, this.messageBodyBeginMark, this.backingFilename);
        replay.skip(skip);
        return replay; 
    }

    /**
     * Return a replay stream, cued up to begining of content
     *
     * @throws IOException
     * @return An RIS.
     */
    public ReplayInputStream getMessageBodyReplayInputStream() throws IOException {
        return getReplayInputStream(this.messageBodyBeginMark);
    }

    public long getSize() {
        return this.size;
    }

    /**
     * Remember the current position as the start of the "message
     * body". Useful when recording HTTP traffic as a way to start
     * replays after the headers.
     */
    public void markMessageBodyBegin() {
        this.messageBodyBeginMark = this.position;
        startDigest();
    }

    /**
     * Return stored message-body-begin-mark (which is also end-of-headers)
     */
    public long getMessageBodyBegin() {
        return this.messageBodyBeginMark;
    }
    
    /**
     * Starts digesting recorded data, if a MessageDigest has been
     * set.
     */
    public void startDigest() {
        if (this.digest != null) {
            this.digest.reset();
            this.shouldDigest = true;
        }
    }

    /**
     * Convenience method for setting SHA1 digest.
     * @see #setDigest(String)
     */
    public void setSha1Digest() {
        setDigest(SHA1);
    }
    

    /**
     * Sets a digest function which may be applied to recorded data.
     * The difference between calling this method and {@link #setDigest(MessageDigest)}
     * is that this method tries to reuse MethodDigest instance if already allocated
     * and of appropriate algorithm.
     * @param algorithm Message digest algorithm to use.
     * @see #setDigest(MessageDigest)
     */
    public void setDigest(String algorithm) {
        try {
            // Reuse extant digest if its sha1 algorithm.
            if (this.digest == null ||
                    !this.digest.getAlgorithm().equals(algorithm)) {
                setDigest(MessageDigest.getInstance(algorithm));
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    /**
     * Sets a digest function which may be applied to recorded data.
     *
     * As usually only a subset of the recorded data should
     * be fed to the digest, you must also call startDigest()
     * to begin digesting.
     *
     * @param md Message digest function to use.
     */
    public void setDigest(MessageDigest md) {
        this.digest = md;
    }

    /**
     * Return the digest value for any recorded, digested data. Call
     * only after all data has been recorded; otherwise, the running
     * digest state is ruined.
     *
     * @return the digest final value
     */
    public byte[] getDigestValue() {
        if(this.digest == null) {
            return null;
        }
        return this.digest.digest();
    }

    public long getResponseContentLength() {
        return this.size - this.messageBodyBeginMark;
    }

    /**
     * @return True if this ROS is open.
     */
    public boolean isOpen() {
        return this.out != null;
    }
    
    public int getBufferLength() {
        return this.buffer.length;
    }
    
    /**
     * When used alongside a mark-supporting RecordingInputStream, remember
     * a position reachable by a future reset().
     */
    public void mark() {
        // remember this position for subsequent reset()
        this.markPosition = position; 
    }
    
    /**
     * When used alongside a mark-supporting RecordingInputStream, reset 
     * the position to that saved by previous mark(). Until the position 
     * again reached "new" material, none of the bytes pushed to this 
     * stream will be digested or recorded. 
     */
    public void reset() {
        // take note of furthest-position-reached to avoid double-recording
        maxPosition = Math.max(maxPosition, position); 
        // reset to previous position
        position = markPosition;
    }
    
    /**
     * Set limits on length, time, and rate to enforce.
     * 
     * @param length
     * @param milliseconds
     * @param rateKBps
     */
    public void setLimits(long length, long milliseconds, long rateKBps) {
        maxLength = (length>0) ? length : Long.MAX_VALUE;
        timeoutMs = (milliseconds>0) ? milliseconds : Long.MAX_VALUE;
        maxRateBytesPerMs = (rateKBps>0) ? rateKBps*1024/1000 : Long.MAX_VALUE;
    }
    
    /**
     * Reset limits to effectively-unlimited defaults
     */
    public void resetLimits() {
        maxLength = Long.MAX_VALUE;
        timeoutMs = Long.MAX_VALUE;
        maxRateBytesPerMs = Long.MAX_VALUE;
    }
    
    /**
     * Return number of bytes that could be recorded without hitting 
     * length limit
     * 
     * @return long byte count
     */
    public long getRemainingLength() {
        return maxLength - position; 
    }
}

