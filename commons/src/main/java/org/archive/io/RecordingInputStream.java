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
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;


/**
 * Stream which records all data read from it, which it acquires from a wrapped
 * input stream.
 *
 * Makes use of a RecordingOutputStream for recording because of its being
 * file backed so we can write massive amounts of data w/o worrying about
 * overflowing memory.
 *
 * @author gojomo
 *
 */
public class RecordingInputStream
    extends InputStream {

    protected static Logger logger =
        Logger.getLogger("org.archive.io.RecordingInputStream");

    /**
     * Where we are recording to.
     */
    private RecordingOutputStream recordingOutputStream;

    /**
     * Stream to record.
     */
    private InputStream in = null;

    /**
     * Reusable buffer to avoid reallocation on each readFullyUntil
     */
    protected byte[] drainBuffer = new byte[16*1024];

    /**
     * Create a new RecordingInputStream.
     *
     * @param bufferSize Size of buffer to use.
     * @param backingFilename Name of backing file.
     */
    public RecordingInputStream(int bufferSize, String backingFilename)
    {
        this.recordingOutputStream = new RecordingOutputStream(bufferSize,
            backingFilename);
    }

    public void open(InputStream wrappedStream) throws IOException {
        logger.fine(Thread.currentThread().getName() + " opening " +
            wrappedStream + ", " + Thread.currentThread().getName());
        if(isOpen()) {
            // error; should not be opening/wrapping in an unclosed 
            // stream remains open
            throw new IOException("RIS already open for "
                    +Thread.currentThread().getName());
        }
        try {
            this.in = wrappedStream;
            this.recordingOutputStream.open();
        } catch (IOException ioe) {
            close(); // ...and rethrow...
            throw ioe;
        }
    }

    public int read() throws IOException {
        if (!isOpen()) {
            throw new IOException("Stream closed " +
                Thread.currentThread().getName());
        }
        int b = this.in.read();
        if (b != -1) {
            assert this.recordingOutputStream != null: "ROS is null " +
                Thread.currentThread().getName();
            this.recordingOutputStream.write(b);
        }
        return b;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        if (!isOpen()) {
            throw new IOException("Stream closed " +
                Thread.currentThread().getName());
        }
        int count = this.in.read(b,off,len);
        if (count > 0) {
            assert this.recordingOutputStream != null: "ROS is null " +
                Thread.currentThread().getName();
            this.recordingOutputStream.write(b,off,count);
        }
        return count;
    }

    public int read(byte[] b) throws IOException {
    	    if (!isOpen()) {
    	    	    throw new IOException("Stream closed " +
    			    Thread.currentThread().getName());
    	    }
    	    int count = this.in.read(b);
        if (count > 0) {
            assert this.recordingOutputStream != null: "ROS is null " +
                Thread.currentThread().getName();
            this.recordingOutputStream.write(b,0,count);
        }
        return count;
    }

    public void close() throws IOException {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(Thread.currentThread().getName() + " closing " +
                    this.in + ", " + Thread.currentThread().getName());
        }
        IOUtils.closeQuietly(this.in);
        this.in = null; 
        IOUtils.closeQuietly(this.recordingOutputStream);
    }

    public ReplayInputStream getReplayInputStream() throws IOException {
        return this.recordingOutputStream.getReplayInputStream();
    }

    public ReplayInputStream getMessageBodyReplayInputStream() throws IOException {
        return this.recordingOutputStream.getMessageBodyReplayInputStream();
    }

    public long readFully() throws IOException {
        while(read(drainBuffer) != -1) {
            // Empty out stream.
            continue;
        }
        return this.recordingOutputStream.getSize();
    }

    /**
     * Read all of a stream (Or read until we timeout or have read to the max).
     * @param softMaxLength Maximum length to read; if zero or < 0, then no 
     * limit. If met, return normally. 
     * @param hardMaxLength Maximum length to read; if zero or < 0, then no 
     * limit. If exceeded, throw RecorderLengthExceededException
     * @param timeout Timeout in milliseconds for total read; if zero or
     * negative, timeout is <code>Long.MAX_VALUE</code>. If exceeded, throw
     * RecorderTimeoutException
     * @param maxBytesPerMs How many bytes per millisecond.
     * @throws IOException failed read.
     * @throws RecorderLengthExceededException
     * @throws RecorderTimeoutException
     * @throws InterruptedException
     */
    public void readFullyOrUntil(long softMaxLength)
        throws IOException, RecorderLengthExceededException,
            RecorderTimeoutException, InterruptedException {
        // Check we're open before proceeding.
        if (!isOpen()) {
            // TODO: should this be a noisier exception-raising error? 
            return;
        } 

        long totalBytes = 0L;
        long bytesRead = -1L;
        long maxToRead = -1; 
        while (true) {
            try {
                // read no more than soft max
                maxToRead = (softMaxLength <= 0) 
                    ? drainBuffer.length 
                    : Math.min(drainBuffer.length, softMaxLength - totalBytes);
                // nor more than hard max
                maxToRead = Math.min(maxToRead, recordingOutputStream.getRemainingLength());
                // but always at least 1 (to trigger hard max exception
                maxToRead = Math.max(maxToRead, 1);
                
                bytesRead = read(drainBuffer,0,(int)maxToRead);
                if (bytesRead == -1) {
                    break;
                }
                totalBytes += bytesRead;

                if (Thread.interrupted()) {
                    throw new InterruptedException("Interrupted during IO");
                }
            } catch (SocketTimeoutException e) {
                // A socket timeout is just a transient problem, meaning
                // nothing was available in the configured  timeout period,
                // but something else might become available later.
                // Take this opportunity to check the overall 
                // timeout (below).  One reason for this timeout is 
                // servers that keep up the connection, 'keep-alive', even
                // though we asked them to not keep the connection open.
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "socket timeout", e); 
                }
                // check for interrupt
                if (Thread.interrupted()) {
                    throw new InterruptedException("Interrupted during IO");
                }
                // check for overall timeout
                recordingOutputStream.checkLimits();
            } catch (SocketException se) {
                throw se;
            } catch (NullPointerException e) {
                // [ 896757 ] NPEs in Andy's Th-Fri Crawl.
                // A crawl was showing NPE's in this part of the code but can
                // not reproduce.  Adding this rethrowing catch block w/
                // diagnostics to help should we come across the problem in the
                // future.
                throw new NullPointerException("Stream " + this.in + ", " +
                    e.getMessage() + " " + Thread.currentThread().getName());
            }
            
            // if have read 'enough', just finish
            if (softMaxLength > 0 && totalBytes >= softMaxLength) {
                break; // return
            }
        }
    }

    public long getSize() {
        return this.recordingOutputStream.getSize();
    }

    public void markContentBegin() {
        this.recordingOutputStream.markMessageBodyBegin();
    }
    
    public long getContentBegin() {
        return this.recordingOutputStream.getMessageBodyBegin();
    }
    
    public void startDigest() {
        this.recordingOutputStream.startDigest();
    }

    /**
     * Convenience method for setting SHA1 digest.
     */
    public void setSha1Digest() {
        this.recordingOutputStream.setSha1Digest();
    }
    
    /**
     * Sets a digest algorithm which may be applied to recorded data.
     * As usually only a subset of the recorded data should
     * be fed to the digest, you must also call startDigest()
     * to begin digesting.
     *
     * @param algorithm
     */
    public void setDigest(String algorithm) {
        this.recordingOutputStream.setDigest(algorithm);
    }
    
    /**
     * Sets a digest function which may be applied to recorded data.
     * As usually only a subset of the recorded data should
     * be fed to the digest, you must also call startDigest()
     * to begin digesting.
     *
     * @param md
     */
    public void setDigest(MessageDigest md) {
        this.recordingOutputStream.setDigest(md);
    }

    /**
     * Return the digest value for any recorded, digested data. Call
     * only after all data has been recorded; otherwise, the running
     * digest state is ruined.
     *
     * @return the digest final value
     */
    public byte[] getDigestValue() {
        return this.recordingOutputStream.getDigestValue();
    }

    public long getResponseContentLength() {
        return this.recordingOutputStream.getResponseContentLength();
    }

    public void closeRecorder() throws IOException {
        this.recordingOutputStream.closeRecorder();
    }

    /**
     * @return True if we've been opened.
     */
    public boolean isOpen()
    {
        return this.in != null;
    }

    @Override
    public synchronized void mark(int readlimit) {
        this.in.mark(readlimit); 
        this.recordingOutputStream.mark(); 
    }

    @Override
    public boolean markSupported() {
        return this.in.markSupported(); 
    }

    @Override
    public synchronized void reset() throws IOException {
        this.in.reset();
        this.recordingOutputStream.reset();
    }

    /**
     *  Set limits to be enforced by internal recording-out
     */
    public void setLimits(long hardMax, long timeoutMs, long maxRateKBps) {
        recordingOutputStream.setLimits(hardMax, timeoutMs, maxRateKBps);
    }

    /**
     * Expose the amount of in-memory buffering used by the internal 
     * recording stream. 
     * @return int buffer size
     */
    public int getRecordedBufferLength() {
        return recordingOutputStream.getBufferLength();
    }
}
