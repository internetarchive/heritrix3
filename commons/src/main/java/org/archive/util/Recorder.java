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
package org.archive.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DeflaterInputStream;
import java.util.zip.GZIPInputStream;

import org.apache.commons.httpclient.ChunkedInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.io.GenericReplayCharSequence;
import org.archive.io.RecordingInputStream;
import org.archive.io.RecordingOutputStream;
import org.archive.io.ReplayCharSequence;
import org.archive.io.ReplayInputStream;

import com.google.common.base.Charsets;


/**
 * Pairs together a RecordingInputStream and RecordingOutputStream
 * to capture exactly a single HTTP transaction.
 *
 * Initially only supports HTTP/1.0 (one request, one response per stream)
 *
 * Call {@link #markContentBegin()} to demarc the transition between HTTP
 * header and body.
 *
 * @author gojomo
 */
public class Recorder {
    protected static Logger logger =
        Logger.getLogger("org.archive.util.HttpRecorder");

    private static final int DEFAULT_OUTPUT_BUFFER_SIZE = 16384;
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 524288;

    private RecordingInputStream ris = null;
    private RecordingOutputStream ros = null;

    /**
     * Backing file basename.
     *
     * Keep it around so can clean up backing files left on disk.
     */
    private String backingFileBasename = null;

    /**
     * Backing file output stream suffix.
     */
    private static final String RECORDING_OUTPUT_STREAM_SUFFIX = ".ros";

   /**
    * Backing file input stream suffix.
    */
    private static final String RECORDING_INPUT_STREAM_SUFFIX = ".ris";

    /**
     * recording-input (ris) content character encoding.
     */
    private String characterEncoding = null;
    
    /** whether recording-input (ris) message-body is chunked */
    protected boolean inputIsChunked = false; 

    /** recording-input (ris) entity content-encoding (eg gzip, deflate), if any */ 
    protected String contentEncoding = null; 
    
    private ReplayCharSequence replayCharSequence;

   
    /**
     * Create an HttpRecorder.
     *
     * @param tempDir Directory into which we drop backing files for
     * recorded input and output.
     * @param backingFilenameBase Backing filename base to which we'll append
     * suffices <code>ris</code> for recorded input stream and
     * <code>ros</code> for recorded output stream.
     * @param outBufferSize Size of output buffer to use.
     * @param inBufferSize Size of input buffer to use.
     */
    public Recorder(File tempDir, String backingFilenameBase, 
            int outBufferSize, int inBufferSize) {
        this(ensure(new File(tempDir, backingFilenameBase)), 
                outBufferSize, inBufferSize);
        tempDir.mkdirs();
    }
    
    
    private static File ensure(File tempDir) {
        tempDir.mkdirs();
        return tempDir;
    }
    
    public Recorder(File file, int outBufferSize, int inBufferSize) {
        super();
        this.backingFileBasename = file.getAbsolutePath();
        this.ris = new RecordingInputStream(inBufferSize,
            this.backingFileBasename + RECORDING_INPUT_STREAM_SUFFIX);
        this.ros = new RecordingOutputStream(outBufferSize,
            this.backingFileBasename + RECORDING_OUTPUT_STREAM_SUFFIX);
    }

    /**
     * Create an HttpRecorder.
     * 
     * @param tempDir
     *            Directory into which we drop backing files for recorded input
     *            and output.
     * @param backingFilenameBase
     *            Backing filename base to which we'll append suffices
     *            <code>ris</code> for recorded input stream and
     *            <code>ros</code> for recorded output stream.
     */
    public Recorder(File tempDir, String backingFilenameBase) {
        this(tempDir, backingFilenameBase, DEFAULT_INPUT_BUFFER_SIZE,
                DEFAULT_OUTPUT_BUFFER_SIZE);
    }

    
    /**
     * Wrap the provided stream with the internal RecordingInputStream
     *
     * open() throws an exception if RecordingInputStream is already open.
     *
     * @param is InputStream to wrap.
     *
     * @return The input stream wrapper which itself is an input stream.
     * Pass this in place of the passed stream so input can be recorded.
     *
     * @throws IOException
     */
    public InputStream inputWrap(InputStream is) 
    throws IOException {
        logger.fine(Thread.currentThread().getName() + " wrapping input");
        
        // discard any state from previously-recorded input
        this.characterEncoding = null;
        this.inputIsChunked = false;
        this.contentEncoding = null; 
        
        this.ris.open(is);
        return this.ris;
    }

    /**
     * Wrap the provided stream with the internal RecordingOutputStream
     *
     * open() throws an exception if RecordingOutputStream is already open.
     * 
     * @param os The output stream to wrap.
     *
     * @return The output stream wrapper which is itself an output stream.
     * Pass this in place of the passed stream so output can be recorded.
     *
     * @throws IOException
     */
    public OutputStream outputWrap(OutputStream os) 
    throws IOException {
        this.ros.open(os);
        return this.ros;
    }

    /**
     * Close all streams.
     */
    public void close() {
        logger.fine(Thread.currentThread().getName() + " closing");
        try {
            this.ris.close();
        } catch (IOException e) {
            // TODO: Can we not let the exception out of here and report it
            // higher up in the caller?
            DevUtils.logger.log(Level.SEVERE, "close() ris" +
                DevUtils.extraInfo(), e);
        }
        try {
            this.ros.close();
        } catch (IOException e) {
            DevUtils.logger.log(Level.SEVERE, "close() ros" +
                DevUtils.extraInfo(), e);
        }
    }

    /**
     * Return the internal RecordingInputStream
     *
     * @return A RIS.
     */
    public RecordingInputStream getRecordedInput() {
        return this.ris;
    }

    /**
     * @return The RecordingOutputStream.
     */
    public RecordingOutputStream getRecordedOutput() {
        return this.ros;
    }

    /**
     * Mark current position as the point where the HTTP headers end.
     */
    public void markContentBegin() {
        this.ris.markContentBegin();
    }

    public long getResponseContentLength() {
        return this.ris.getResponseContentLength();
    }

    /**
     * Close both input and output recorders.
     *
     * Recorders are the output streams to which we are recording.
     * {@link #close()} closes the stream that is being recorded and the
     * recorder. This method explicitly closes the recorder only.
     */
    public void closeRecorders() {
        try {
            this.ris.closeRecorder();
            this.ros.closeRecorder();
        } catch (IOException e) {
            DevUtils.warnHandle(e, "Convert to runtime exception?");
        }
    }

    /**
     * Cleanup backing files.
     *
     * Call when completely done w/ recorder.  Removes any backing files that
     * may have been dropped.
     */
    public void cleanup() {
        this.close();
        this.delete(this.backingFileBasename + RECORDING_OUTPUT_STREAM_SUFFIX);
        this.delete(this.backingFileBasename + RECORDING_INPUT_STREAM_SUFFIX);
    }

    /**
     * Delete file if exists.
     *
     * @param name Filename to delete.
     */
    private void delete(String name) {
        File f = new File(name);
        if (f.exists()) {
            f.delete();
        }
    }

    
    static ThreadLocal<Recorder> currentRecorder = new ThreadLocal<Recorder>();
    
    public static void setHttpRecorder(Recorder httpRecorder) {
        currentRecorder.set(httpRecorder);
    } 
    
    /**
     * Get the current threads' HttpRecorder.
     *
     * @return This threads' HttpRecorder.  Returns null if can't find a
     * HttpRecorder in current instance.
     */
    public static Recorder getHttpRecorder() {
        return currentRecorder.get(); 
    }

    /**
     * Sets character encoding. If new encoding is different from old encoding,
     * close replayCharSequence and set to null, which will trigger recreation
     * on next retrieval.
     * 
     * @param characterEncoding
     *            Character encoding of recording.
     */
    public void setCharacterEncoding(String characterEncoding) {
        boolean needsReset = false;
        
        if (replayCharSequence != null && replayCharSequence.isOpen()
                && this.characterEncoding != characterEncoding)
        {
            Charset newCharset = null;
            if (characterEncoding != null) {
                newCharset = Charset.forName(characterEncoding);
            }

            Charset oldCharset = null;
            if (this.characterEncoding != null) {
                oldCharset = Charset.forName(this.characterEncoding);
            } else {
                oldCharset = Charsets.ISO_8859_1;
            }

            needsReset = !oldCharset.equals(newCharset);
        }

        this.characterEncoding = characterEncoding;

        if (needsReset) {
            ArchiveUtils.closeQuietly(replayCharSequence);
            replayCharSequence = null;
        }
    }

    /**
     * @return Returns the characterEncoding of input recording.
     */
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }
    
    /**
     * @param characterEncoding Character encoding of input recording.
     */
    public void setInputIsChunked(boolean chunked) {
        this.inputIsChunked = chunked;
    }
    
    /**
     * @param contentEncoding declared content-encoding of input recording.
     */
    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    /**
     * @return Returns the characterEncoding.
     */
    public String getContentEncoding() {
        return this.contentEncoding;
    }

    
    /**
     * @return
     * @throws IOException
     * @deprecated use getContentReplayCharSequence
     */
    public ReplayCharSequence getReplayCharSequence() throws IOException {
        return getContentReplayCharSequence();
    }
    
    /**
     * @return A ReplayCharSequence. Caller may call
     *         {@link ReplayCharSequence#close()} when finished. However, in
     *         heritrix, the ReplayCharSequence is closed automatically when url
     *         processing has finished; in that context it's preferable not
     *         to close, so that processors can reuse the same instance.
     * @throws IOException
     * @see {@link #endReplays()}
     */
    public ReplayCharSequence getContentReplayCharSequence() throws IOException {
        if (replayCharSequence == null || !replayCharSequence.isOpen()) {
            replayCharSequence = getContentReplayCharSequence(this.characterEncoding);
        }
        
        return replayCharSequence;
    }
    
    
    /**
     * @param characterEncoding Encoding of recorded stream.
     * @return A ReplayCharSequence  Will return null if an IOException.  Call
     * close on returned RCS when done.
     * @throws IOException
     */
    public ReplayCharSequence getContentReplayCharSequence(String encoding) throws IOException {
        Charset charset = Charsets.UTF_8;
        if (encoding != null) {
            try {
                charset = Charset.forName(encoding);
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING,"charset problem: "+encoding,e);
                // TODO: better detection or default
                charset = ReplayCharSequence.FALLBACK_CHARSET;
            }
        }

        logger.fine("using GenericReplayCharSequence");
        // raw data overflows to disk; use temp file
        InputStream ris = getContentReplayInputStream();
        ReplayCharSequence rcs =  new GenericReplayCharSequence(
                ris,
                this.ros.getBufferLength()/2, 
                this.backingFileBasename + RECORDING_OUTPUT_STREAM_SUFFIX,
                charset);
        ris.close();
        return rcs;
        
    }
    
    /**
     * Get a raw replay of all recorded data (including, for example, HTTP 
     * protocol headers)
     * 
     * @return A replay input stream.
     * @throws IOException
     */
    public ReplayInputStream getReplayInputStream() throws IOException {
        return getRecordedInput().getReplayInputStream();
    }
    
    /**
     * Get a raw replay of the 'message-body'. For the common case of 
     * HTTP, this is the raw, possibly chunked-transfer-encoded message 
     * contents not including the leading headers. 
     * 
     * @return A replay input stream.
     * @throws IOException
     */
    public ReplayInputStream getMessageBodyReplayInputStream() throws IOException {
        return getRecordedInput().getMessageBodyReplayInputStream();
    }
    
    /**
     * Get a raw replay of the 'entity'. For the common case of 
     * HTTP, this is the message-body after any (usually-unnecessary)
     * transfer-decoding but before any content-encoding (eg gzip) decoding
     * 
     * @return A replay input stream.
     * @throws IOException
     */
    public InputStream getEntityReplayInputStream() throws IOException {
        if(inputIsChunked) {
            return new ChunkedInputStream(getRecordedInput().getMessageBodyReplayInputStream());
        } else {
            return getRecordedInput().getMessageBodyReplayInputStream();
        }
    }
    
    /**
     * Get a replay cued up for the 'content' (after all leading headers)
     * 
     * TODO: handle chunking
     * TODO: handle decompression, either here or in a parallel method
     * 
     * @return A replay input stream.
     * @throws IOException
     */
    public InputStream getContentReplayInputStream() throws IOException {
        InputStream entityStream = getEntityReplayInputStream();
        if(StringUtils.isEmpty(contentEncoding)) {
            return entityStream;
        } else if ("gzip".equalsIgnoreCase(contentEncoding) || "x-gzip".equalsIgnoreCase(contentEncoding)) {
            try {
                return new GZIPInputStream(entityStream);
            } catch (IOException ioe) {
                logger.log(Level.WARNING,"gzip problem; using raw entity instead",ioe);
                IOUtils.closeQuietly(entityStream); // close partially-read stream
                return getEntityReplayInputStream(); 
            }
        } else if ("deflate".equalsIgnoreCase(contentEncoding)) {
            return new DeflaterInputStream(entityStream);
        } else if ("identity".equalsIgnoreCase(contentEncoding)) {
            return entityStream;
        } else {
            logger.log(Level.WARNING,"Unknown content-encoding '"+contentEncoding+"' declared; using raw entity instead");
            return entityStream; 
        }
    }
    
    /**
     * Return a short prefix of the presumed-textual content as a String.
     * 
     * @param size max length of String to return 
     * @return String prefix, or empty String (with logged exception) on any error
     */
    public String getContentReplayPrefixString(int size) {
        try {
            InputStreamReader isr =  (characterEncoding == null) 
                ? new InputStreamReader(getContentReplayInputStream(), Charsets.ISO_8859_1)
                : new InputStreamReader(getContentReplayInputStream(), characterEncoding); 
            char[] chars = new char[size];
            int count = isr.read(chars);
            isr.close(); 
            return new String(chars,0,count);
        } catch (IOException e) {
            logger.log(Level.SEVERE,"unable to get replay prefix string", e);
            return ""; 
        } 
    }
    
    /**
     * @param tempFile
     * @throws IOException
     */
    public void copyContentBodyTo(File tempFile) throws IOException {
        InputStream inStream = null;
        OutputStream outStream = null;
        try {
            inStream = getContentReplayInputStream();
            outStream = FileUtils.openOutputStream(tempFile); 
            IOUtils.copy(inStream, outStream); 
        } finally {
            IOUtils.closeQuietly(inStream); 
            IOUtils.closeQuietly(outStream); 
        }
    }
    
    /**
     * Record the input stream for later playback by an extractor, etc.
     * This is convenience method used to setup an artificial HttpRecorder
     * scenario used in unit tests, etc.
     * @param dir Directory to write backing file to.
     * @param basename of what we're recording.
     * @param in Stream to read.
     * @param encoding Stream encoding.
     * @throws IOException
     * @return An {@link org.archive.util.Recorder}.
     */
    public static Recorder wrapInputStreamWithHttpRecord(File dir,
        String basename, InputStream in, String encoding)
    throws IOException {
        Recorder rec = new Recorder(dir, basename);
        if (encoding != null && encoding.length() > 0) {
            rec.setCharacterEncoding(encoding);
        }
        // Do not use FastBufferedInputStream here.  It does not
        // support mark.
        InputStream is = rec.inputWrap(new BufferedInputStream(in));
        final int BUFFER_SIZE = 1024 * 4;
        byte [] buffer = new byte[BUFFER_SIZE];
        while(true) {
            // Just read it all down.
            int x = is.read(buffer);
            if (x == -1) {
                break;
            }
        }
        is.close();
        return rec;
    }

    public void endReplays() {
        ArchiveUtils.closeQuietly(replayCharSequence);
        replayCharSequence = null;
    }
}
