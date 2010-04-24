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
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InMemoryReplayCharSequence implements ReplayCharSequence {

    protected static Logger logger =
        Logger.getLogger(InMemoryReplayCharSequence.class.getName());
    
    /**
     * CharBuffer of decoded content.
     *
     * Content of this buffer is unicode.
     */
    private CharBuffer charBuffer = null;

    protected long decodingExceptionsCount = 0;
    protected CharacterCodingException codingException = null; 
    
    /**
     * Constructor for all in-memory operation.
     *
     * @param buffer 
     * @param size Total size of stream to replay in bytes.  Used to find
     * EOS. This is total length of content including HTTP headers if
     * present.
     * @param responseBodyStart Where the response body starts in bytes.
     * Used to skip over the HTTP headers if present.
     * @param encoding Encoding to use reading the passed prefix buffer and
     * backing file.  For now, should be java canonical name for the
     * encoding. Must not be null.
     *
     * @throws IOException
     */
    public InMemoryReplayCharSequence(byte[] buffer, long size,
            long responseBodyStart, String encoding) throws IOException {
        super();
        this.charBuffer = decodeInMemory(buffer, size, responseBodyStart,
                encoding);
    }

    /**
     * Decode passed buffer into a CharBuffer.
     *
     * This method decodes a memory buffer returning a memory buffer.
     *
     * @param buffer 
     * @param size Total size of stream to replay in bytes.  Used to find
     * EOS. This is total length of content including HTTP headers if
     * present.
     * @param responseBodyStart Where the response body starts in bytes.
     * Used to skip over the HTTP headers if present.
     * @param encoding Encoding to use reading the passed prefix buffer and
     * backing file.  For now, should be java canonical name for the
     * encoding. Must not be null.
     *
     * @return A CharBuffer view on decodings of the contents of passed
     * buffer.
     */
    private CharBuffer decodeInMemory(byte[] buffer, long size,
            long responseBodyStart, String encoding) {
        ByteBuffer bb = ByteBuffer.wrap(buffer);
        // Move past the HTTP header if present.
        bb.position((int) responseBodyStart);
        bb.mark();
        // Set the end-of-buffer to be end-of-content.
        bb.limit((int) size);
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING,"charset problem: "+encoding,e);
            // TODO: better detection or default
            charset = Charset.forName(FALLBACK_CHARSET_NAME);
        }
        try {
            return charset.newDecoder()
                   .onMalformedInput(CodingErrorAction.REPORT)
                   .onUnmappableCharacter(CodingErrorAction.REPORT)
                   .decode(bb).asReadOnlyBuffer();
        } catch (CharacterCodingException cce) {
            bb.reset(); 
            decodingExceptionsCount++;
            codingException = cce; 
            return charset.decode(bb).asReadOnlyBuffer();
        }
    }

    public void close() {
        this.charBuffer = null;
    }

    protected void finalize() throws Throwable {
        super.finalize();
        // Maybe TODO: eliminate close here, requiring explicit close instead
        close();
    }

    public int length() {
        return this.charBuffer.limit();
    }

    public char charAt(int index) {
        return this.charBuffer.get(index);
    }

    public CharSequence subSequence(int start, int end) {
        return new CharSubSequence(this, start, end);
    }

    public String toString() {
        StringBuffer sb = new StringBuffer(length());
        sb.append(this);
        return sb.toString();
    }
    
    /**
     * Return 1 if there were decoding problems (a full count isn't possible).
     * 
     * @see org.archive.io.ReplayCharSequence#getDecodeExceptionCount()
     */
    @Override
    public long getDecodeExceptionCount() {
        return decodingExceptionsCount;
    }
    
    /* (non-Javadoc)
     * @see org.archive.io.ReplayCharSequence#getCodingException()
     */
    @Override
    public CharacterCodingException getCodingException() {
        return codingException;
    }
}
