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
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.RecordingInputStream;
import org.archive.io.RecordingOutputStream;
import org.archive.io.ReplayCharSequence;
import org.archive.io.ReplayInputStream;


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

    private static final int DEFAULT_OUTPUT_BUFFER_SIZE = 4096;
    private static final int DEFAULT_INPUT_BUFFER_SIZE = 65536;

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
     * Response character encoding.
     */
    private String characterEncoding = null;

   
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
     * @param characterEncoding Character encoding of recording.
     */
    public void setCharacterEncoding(String characterEncoding) {
        this.characterEncoding = characterEncoding;
    }

    /**
     * @return Returns the characterEncoding.
     */
    public String getCharacterEncoding() {
        return this.characterEncoding;
    }

    /**
     * @return A ReplayCharSequence.  Call close on the RCS when done w/ it.
     * Will return indeterminate results if the underlying recording streams
     * have not been closed first.
     * @throws IOException
     * @throws IOException
     */
    public ReplayCharSequence getReplayCharSequence() throws IOException {
        return getRecordedInput().
            getReplayCharSequence(this.characterEncoding);
    }
    
    /**
     * @return A replay input stream.
     * @throws IOException
     */
    public ReplayInputStream getReplayInputStream() throws IOException {
        return getRecordedInput().getReplayInputStream();
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
}
