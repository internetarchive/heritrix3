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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.archive.util.DevUtils;

import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;

/**
 * (Replay)CharSequence view on recorded streams.
 *
 * For small streams, use {@link InMemoryReplayCharSequence}.
 *
 * <p>Call {@link close()} on this class when done to clean up resources.
 *
 * @contributor stack
 * @contributor nlevitt
 * @version $Revision$, $Date$
 */
public class GenericReplayCharSequence implements ReplayCharSequence {

    protected static Logger logger = Logger
            .getLogger(GenericReplayCharSequence.class.getName());

    /**
     * Name of the encoding we use writing out concatenated decoded prefix
     * buffer and decoded backing file.
     *
     * <p>This define is also used as suffix for the file that holds the
     * decodings.  The name of the file that holds the decoding is the name
     * of the backing file w/ this encoding for a suffix.
     *
     * <p>See <a ref="http://java.sun.com/j2se/1.4.2/docs/guide/intl/encoding.doc.html">Encoding</a>.
     */
    public static final Charset WRITE_ENCODING = Charsets.UTF_16BE;

    private static final long MAP_MAX_BYTES = 64 * 1024 * 1024; // 64M
    
    /**
     * When the memory map moves away from the beginning of the file 
     * (to the "right") in order to reach a certain index, it will
     * map up to this many bytes preceding (to the left of) the target character. 
     * Consequently it will map up to 
     * <code>MAP_MAX_BYTES - MAP_TARGET_LEFT_PADDING</code>
     * bytes to the right of the target.
     */
    private static final long MAP_TARGET_LEFT_PADDING_BYTES = (long) (MAP_MAX_BYTES * 0.01);

    /**
     * Total length of character stream to replay minus the HTTP headers
     * if present. 
     * 
     * If the backing file is larger than <code>Integer.MAX_VALUE</code> (i.e. 2gb),
     * only the first <code>Integer.MAX_VALUE</code> characters are available through this API. 
     * We're overriding <code>java.lang.CharSequence</code> so that we can use 
     * <code>java.util.regex</code> directly on the data, and the <code>CharSequence</code> 
     * API uses <code>int</code> for the length and index.
     */
    protected int length;
    
    /** counter of decoding exceptions for report at end */
    protected long decodingExceptions = 0; 
    protected CharacterCodingException codingException = null; 

    /**
     * Byte offset into the file where the memory mapped portion begins.
     */
    private long mapByteOffset;

    // XXX do we need to keep the input stream around?
    private FileInputStream backingFileIn = null;

    private FileChannel backingFileChannel = null;

    private long bytesPerChar;

    private CharBuffer mappedBuffer = null;

    /**
     * File that has decoded content.
     *
     * Keep it around so we can remove on close.
     */
    private File decodedFile = null;

    /*
     * This portion of the CharSequence precedes what's in the backing file. In
     * cases where we decodeToFile(), this is always empty, because we decode
     * the entire input stream. 
     */ 
    private CharBuffer prefixBuffer = null;

    private boolean isOpen = true;

    /**
     * Constructor.
     *
     * @param contentReplayInputStream inputStream of content
     * @param charset Encoding to use reading the passed prefix
     * buffer and backing file. Must not be null.
     * @param backingFilename Path to backing file with content in excess of
     * whats in <code>buffer</code>.
     *
     * @throws IOException
     */
    public GenericReplayCharSequence(InputStream contentReplayInputStream, 
                                     int prefixMax, 
                                     String backingFilename,
                                     Charset charset) throws IOException {
        super();
        logger.fine("new GenericReplayCharSequence() characterEncoding="
                + charset + " backingFilename=" + backingFilename);

        if(charset==null) {
            charset = ReplayCharSequence.FALLBACK_CHARSET;
        }
        // decodes only up to Integer.MAX_VALUE characters
        decode(contentReplayInputStream, prefixMax, backingFilename, charset);

        this.bytesPerChar = 2;
        
        if(length>prefixBuffer.position()) {
            this.backingFileIn = new FileInputStream(decodedFile);
            this.backingFileChannel = backingFileIn.getChannel();
            this.mapByteOffset = 0;
            updateMemoryMappedBuffer();
        }
    }

    private void updateMemoryMappedBuffer() {
        long charLength = (long) this.length() - (long) prefixBuffer.limit(); // in characters
        long mapSize = Math.min((charLength * bytesPerChar) - mapByteOffset, MAP_MAX_BYTES);
        logger.fine("updateMemoryMappedBuffer: mapOffset="
                + NumberFormat.getInstance().format(mapByteOffset)
                + " mapSize=" + NumberFormat.getInstance().format(mapSize));
        try {
            // TODO: stress-test without these possibly-costly requests!
//            System.gc();
//            System.runFinalization();
            // TODO: Confirm the READ_ONLY works. I recall it not working.
            // The buffers seem to always say that the buffer is writable.
            mappedBuffer = backingFileChannel.map(
                    FileChannel.MapMode.READ_ONLY, mapByteOffset, mapSize)
                    .asReadOnlyBuffer().asCharBuffer();
        } catch (IOException e) {
            // TODO convert this to a runtime error?
            DevUtils.logger.log(Level.SEVERE,
                    " backingFileChannel.map() mapByteOffset=" + mapByteOffset
                            + " mapSize=" + mapSize + "\n" + "decodedFile="
                            + decodedFile + " length=" + length + "\n"
                            + DevUtils.extraInfo(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Converts the first <code>Integer.MAX_VALUE</code> characters from the
     * file <code>backingFilename</code> from encoding <code>encoding</code> to
     * encoding <code>WRITE_ENCODING</code> and saves as
     * <code>this.decodedFile</code>, which is named <code>backingFilename
     * + "." + WRITE_ENCODING</code>.
     * 
     * @throws IOException
     */
    protected void decode(InputStream inStream, int prefixMax, 
            String backingFilename, Charset charset) throws IOException {

        // TODO: consider if BufferedReader is helping any
        // TODO: consider adding TBW 'LimitReader' to stop reading at 
        // Integer.MAX_VALUE characters because of charAt(int) limit
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                inStream, charset));

        logger.fine("decodeToFile: backingFilename=" + backingFilename
                + " encoding=" + charset + " decodedFile=" + decodedFile);

        this.prefixBuffer = CharBuffer.allocate(prefixMax); 
        
        long count = 0;
        while(count < prefixMax) {
            int read = reader.read(prefixBuffer); 
            if(read<0) {
                break;
            }
            count += read; 
        }
        
        if(reader.ready()) {
            // more to decode to file overflow
            this.decodedFile = new File(backingFilename + "." + WRITE_ENCODING);

            FileOutputStream fos;
            try {
                fos = new FileOutputStream(this.decodedFile);
            } catch (FileNotFoundException e) {
                // Windows workaround attempt
                System.gc();
                System.runFinalization();
                this.decodedFile = new File(decodedFile.getAbsolutePath()+".win");
                logger.info("Windows 'file with a user-mapped section open' "
                        + "workaround gc/finalization/name-extension performed.");
                // try again
                fos = new FileOutputStream(this.decodedFile);
            }
            
            Writer writer = new OutputStreamWriter(fos,WRITE_ENCODING);
            count += IOUtils.copyLarge(reader, writer); 
            writer.close();
            reader.close();
        }
        
        this.length = Ints.saturatedCast(count); 
        if(count>Integer.MAX_VALUE) {
            logger.warning("input stream is longer than Integer.MAX_VALUE="
                    + NumberFormat.getInstance().format(Integer.MAX_VALUE)
                    + " characters -- only first "
                    + NumberFormat.getInstance().format(Integer.MAX_VALUE)
                    + " are accessible through this GenericReplayCharSequence");
        }

        logger.fine("decode: decoded " + count + " characters" +
            ((decodedFile==null) ? ""
                                 : " ("+(count-prefixBuffer.length())+" to "+decodedFile+")"));
    }

    /**
     * Get character at passed absolute position.
     * @param index Index into content 
     * @return Character at offset <code>index</code>.
     */
    public char charAt(int index) {
        if (index < 0 || index >= this.length()) {
            throw new IndexOutOfBoundsException("index=" + index
                    + " - should be between 0 and length()=" + this.length());
        }

        // is it in the buffer
        if (index < prefixBuffer.limit()) {
            return prefixBuffer.get(index);
        }

        // otherwise we gotta get it from disk via memory map
        long charFileIndex = (long) index - (long) prefixBuffer.limit();
        long charFileLength = (long) this.length() - (long) prefixBuffer.limit(); // in characters
        if (charFileIndex * bytesPerChar < mapByteOffset) {
            logger.log(Level.WARNING,"left-fault; probably don't want to use CharSequence that far backward");
        }
        if (charFileIndex * bytesPerChar < mapByteOffset
                || charFileIndex - (mapByteOffset / bytesPerChar) >= mappedBuffer.limit()) {
            // fault
            /*
             * mapByteOffset is bounded by 0 and file size +/- size of the map,
             * and starts as close to <code>fileIndex -
             * MAP_TARGET_LEFT_PADDING_BYTES</code> as it can while also not
             * being smaller than it needs to be.
             */
            mapByteOffset = Math.min(charFileIndex * bytesPerChar - MAP_TARGET_LEFT_PADDING_BYTES, 
                                     charFileLength * bytesPerChar - MAP_MAX_BYTES);
            mapByteOffset = Math.max(0, mapByteOffset);
            updateMemoryMappedBuffer();
        }

        return mappedBuffer.get((int)(charFileIndex-(mapByteOffset/bytesPerChar))); 
    }

    public CharSequence subSequence(int start, int end) {
        return new CharSubSequence(this, start, end);
    }

    private void deleteFile(File fileToDelete) {
        deleteFile(fileToDelete, null);
    }

    private void deleteFile(File fileToDelete, final Exception e) {
        if (e != null) {
            // Log why the delete to help with debug of
            // java.io.FileNotFoundException:
            // ....tt53http.ris.UTF-16BE.
            logger.severe("Deleting " + fileToDelete + " because of "
                    + e.toString());
        }
        if (fileToDelete != null && fileToDelete.exists()) {
            logger.fine("deleting file: " + fileToDelete);
            fileToDelete.delete();
        }
    }


    @Override
    public boolean isOpen() {
        return this.isOpen;
    }

    public void close() throws IOException {
        this.isOpen = false;

        logger.fine("closing");

        if (this.backingFileChannel != null && this.backingFileChannel.isOpen()) {
            this.backingFileChannel.close();
        }
        if (backingFileIn != null) {
            backingFileIn.close();
        }

        deleteFile(this.decodedFile);

        // clear decodedFile -- so that double-close (as in finalize()) won't
        // delete a later instance with same name see bug [ 1218961 ]
        // "failed get of replay" in ExtractorHTML... usu: UTF-16BE
        this.decodedFile = null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#finalize()
     */
    protected void finalize() throws Throwable {
        super.finalize();
        logger.fine("finalizing");
        close();
    }

    /**
     * Convenience method for getting a substring.
     * 
     * @deprecated please use subSequence() and then toString() directly
     */
    public String substring(int offset, int len) {
        return subSequence(offset, offset + len).toString();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder(this.length());
        sb.append(this);
        return sb.toString();
    }

    public int length() {
        return length;
    }

    /* (non-Javadoc)
     * @see org.archive.io.ReplayCharSequence#getDecodeExceptionCount()
     */
    @Override
    public long getDecodeExceptionCount() {
        return decodingExceptions;
    }
    

    /* (non-Javadoc)
     * @see org.archive.io.ReplayCharSequence#getCodingException()
     */
    @Override
    public CharacterCodingException getCodingException() {
        return codingException;
    }
}