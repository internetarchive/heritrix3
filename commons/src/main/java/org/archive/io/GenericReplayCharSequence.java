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
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.logging.Logger;

/**
 * Provides a (Replay)CharSequence view on recorded streams (a prefix
 * buffer and overflow backing file) that can handle streams of multibyte
 * characters.
 *
 * If possible, use {@link Latin1ByteReplayCharSequence}.  It performs better even
 * for the single byte case (Decoding is an expensive process).
 *
 * <p>Call close on this class when done so can clean up resources.
 *
 * <p>Implementation currently works by checking to see if content to read
 * all fits the in-memory buffer.  If so, we decode into a CharBuffer and
 * keep this around for CharSequence operations.  This CharBuffer is
 * discarded on close.
 *
 * <p>If content length is greater than in-memory buffer, we decode the
 * buffer plus backing file into a new file named for the backing file w/
 * a suffix of the encoding we write the file as. We then run w/ a
 * memory-mapped CharBuffer against this file to implement CharSequence.
 * Reasons for this implemenation are that CharSequence wants to return the
 * length of the CharSequence.
 *
 * <p>Obvious optimizations would keep around decodings whether the
 * in-memory decoded buffer or the file of decodings written to disk but the
 * general usage pattern processing URIs is that the decoding is used by one
 * processor only.  Also of note, files usually fit into the in-memory
 * buffer.
 *
 * <p>We might also be able to keep up 3 windows that moved across the file
 * decoding a window at a time trying to keep one of the buffers just in
 * front of the regex processing returning it a length that would be only
 * the length of current position to end of current block or else the length
 * could be got by multipling the backing files length by the decoders'
 * estimate of average character size.  This would save us writing out the
 * decoded file.  We'd have to do the latter for files that are
 * > Integer.MAX_VALUE.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public class GenericReplayCharSequence implements ReplayCharSequence {

    protected static Logger logger =
        Logger.getLogger(GenericReplayCharSequence.class.getName());
    
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
    private static final String WRITE_ENCODING = "UTF-16BE";

    /**
     * CharBuffer of decoded content.
     *
     * Content of this buffer is unicode.
     */
    private CharBuffer content = null;

    /**
     * File that has decoded content.
     *
     * Keep it around so we can remove on close.
     */
    private File decodedFile = null;


    /**
     * Constructor for all in-memory operation.
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
     * @param encoding Encoding to use reading the passed prefix buffer and
     * backing file.  For now, should be java canonical name for the
     * encoding. (If null is passed, we will default to
     * ByteReplayCharSequence).
     *
     * @throws IOException
     */
    public GenericReplayCharSequence(byte[] buffer, long size,
            long responseBodyStart, String encoding)
        throws IOException {
        super();
        this.content = decodeInMemory(buffer, size, responseBodyStart, 
                encoding);
     }

    /**
     * Constructor for overflow-to-disk-file operation.
     *
     * @param contentReplayInputStream inputStream of content
     * @param backingFilename hint for name of temp file
     * @param characterEncoding Encoding to use reading the stream.
     * For now, should be java canonical name for the
     * encoding. 
     *
     * @throws IOException
     */
    public GenericReplayCharSequence(
            ReplayInputStream contentReplayInputStream,
            String backingFilename,
            String characterEncoding)
        throws IOException {
        super();
        this.content = decodeToFile(contentReplayInputStream, 
                backingFilename, characterEncoding);
    }

    /**
     * Decode passed buffer and backing file into a CharBuffer.
     *
     * This method writes a new file made of the decoded concatenation of
     * the in-memory prefix buffer and the backing file.  Returns a
     * charSequence view onto this new file.
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
     * @param encoding Encoding to use reading the passed prefix buffer and
     * backing file.  For now, should be java canonical name for the
     * encoding. (If null is passed, we will default to
     * ByteReplayCharSequence).
     *
     * @return A CharBuffer view on decodings of the contents of passed
     * buffer.
     * @throws IOException
     */
    private CharBuffer decodeToFile(ReplayInputStream inStream, 
            String backingFilename, String encoding)
        throws IOException {

        CharBuffer charBuffer = null;

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inStream,encoding));
        
        this.decodedFile = new File(backingFilename + "." + WRITE_ENCODING);
        FileOutputStream fos;
        try {
            fos = new FileOutputStream(this.decodedFile);
        } catch (FileNotFoundException e) {
            // Windows workaround attempt
            System.gc();
            System.runFinalization();
            logger.info("Windows 'file with a user-mapped section open' "+
                    "workaround gc-finalization performed.");
            // try again 
            fos = new FileOutputStream(this.decodedFile);
        }
        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(
                        fos, 
                        WRITE_ENCODING)); 

        int c;
        while((c = reader.read())>=0) {
            writer.write(c);
        }
        writer.close();
        
        charBuffer = getReadOnlyMemoryMappedBuffer(this.decodedFile).
            asCharBuffer();

        return charBuffer;
    }

    /**
     * Decode passed buffer into a CharBuffer.
     *
     * This method decodes a memory buffer returning a memory buffer.
     *
     * @param buffer In-memory buffer of recordings prefix.  We read from
     * here first and will only go to the backing file if <code>size</code>
     * requested is greater than <code>buffer.length</code>.
     * @param size Total size of stream to replay in bytes.  Used to find
     * EOS. This is total length of content including HTTP headers if
     * present.
     * @param responseBodyStart Where the response body starts in bytes.
     * Used to skip over the HTTP headers if present.
     * @param encoding Encoding to use reading the passed prefix buffer and
     * backing file.  For now, should be java canonical name for the
     * encoding. (If null is passed, we will default to
     * ByteReplayCharSequence).
     *
     * @return A CharBuffer view on decodings of the contents of passed
     * buffer.
     */
    private CharBuffer decodeInMemory(byte[] buffer, long size,
            long responseBodyStart, String encoding)
    {
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        // Move past the HTTP header if present.
        bb.position((int)responseBodyStart);
        // Set the end-of-buffer to be end-of-content.
        bb.limit((int)size);
        return (Charset.forName(encoding)).decode(bb).asReadOnlyBuffer();
    }

    /**
     * Create read-only memory-mapped buffer onto passed file.
     *
     * @param file File to get memory-mapped buffer on.
     * @return Read-only memory-mapped ByteBuffer view on to passed file.
     * @throws IOException
     */
    private ByteBuffer getReadOnlyMemoryMappedBuffer(File file)
        throws IOException {

        ByteBuffer bb = null;
        FileInputStream in = null;
        FileChannel c = null;
        assert file.exists(): "No file " + file.getAbsolutePath();

        try {
            in = new FileInputStream(file);
            c = in.getChannel();
            // TODO: Confirm the READ_ONLY works.  I recall it not working.
            // The buffers seem to always say that the buffer is writeable.
            bb = c.map(FileChannel.MapMode.READ_ONLY, 0, c.size()).
                asReadOnlyBuffer();
        }

        finally {
            if (c != null && c.isOpen()) {
                c.close();
            }
            if (in != null) {
                in.close();
            }
        }

        return bb;
    }

    private void deleteFile(File fileToDelete) {
        deleteFile(fileToDelete, null);        
    }

    private void deleteFile(File fileToDelete, final Exception e) {
        if (e != null) {
            // Log why the delete to help with debug of java.io.FileNotFoundException:
            // ....tt53http.ris.UTF-16BE.
            logger.severe("Deleting " + fileToDelete + " because of "
                + e.toString());
        }
        if (fileToDelete != null && fileToDelete.exists()) {
            fileToDelete.delete();
        }
    }

    public void close()
    {
        this.content = null;
        deleteFile(this.decodedFile);
        // clear decodedFile -- so that double-close (as in 
        // finalize()) won't delete a later instance with same name
        // see bug [ 1218961 ] "failed get of replay" in ExtractorHTML... usu: UTF-16BE
        this.decodedFile = null;
    }

    protected void finalize() throws Throwable
    {
        super.finalize();
        // Maybe TODO: eliminate close here, requiring explicit close instead
        close();
    }

    public int length()
    {
        return this.content.limit();
    }

    public char charAt(int index)
    {
        return this.content.get(index);
    }

    public CharSequence subSequence(int start, int end) {
        return new CharSubSequence(this, start, end);
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer(length());
        // could use StringBuffer.append(CharSequence) if willing to do 1.5 & up
        for (int i = 0;i<length();i++) {
            sb.append(charAt(i)); 
        }
        return sb.toString();
    }
}