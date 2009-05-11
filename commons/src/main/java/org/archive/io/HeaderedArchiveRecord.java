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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpParser;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.archive.io.arc.ARCConstants;

/**
 * An ArchiveRecord whose content has a preamble of RFC822-like headers: e.g.
 * The ArchiveRecord is a http response that leads off with http response
 * headers.  Use this ArchiveRecord Decorator to get at the content headers and
 * the header/content demarcation.
 * 
 * @author stack
 * @author Olaf Freyer
 */
public class HeaderedArchiveRecord extends ArchiveRecord {
    private int contentHeadersLength = -1;
    private int statusCode = -1;
    
    /**
     * Http header bytes.
     * 
     * If non-null and bytes available, give out its contents before we
     * go back to the underlying stream.
     */
    private InputStream contentHeaderStream = null;
    
    /**
     * Content headers.
     * 
     * Only available after the reading of headers.
     */
    private Header [] contentHeaders = null;
    

    public HeaderedArchiveRecord(final ArchiveRecord ar) throws IOException {
        super(ar);
    }
    
    public HeaderedArchiveRecord(final ArchiveRecord ar,
            final boolean readContentHeader) throws IOException {
        super(ar);
        if (readContentHeader) {
            this.contentHeaderStream = readContentHeaders();
        }
    }
    
    /**
     * Skip over the the content headers if present.
     * 
     * Subsequent reads will get the body.
     * 
     * <p>Calling this method in the midst of reading the header
     * will make for strange results.  Otherwise, safe to call
     * at any time though before reading any of the record
     * content is only time that it makes sense.
     * 
     * <p>After calling this method, you can call
     * {@link #getContentHeaders()} to get the read http header.
     * 
     * @throws IOException
     */
    public void skipHttpHeader() throws IOException {
        if (this.contentHeaderStream == null) {
            return;
        }
        // Empty the contentHeaderStream
        for (int available = this.contentHeaderStream.available();
            this.contentHeaderStream != null
                && (available = this.contentHeaderStream.available()) > 0;) {
            // We should be in this loop once only we should only do this
            // buffer allocation once.
            byte[] buffer = new byte[available];
            // The read nulls out httpHeaderStream when done with it so
            // need check for null in the loop control line.
            read(buffer, 0, available);
        }
    }
    
    public void dumpHttpHeader() throws IOException {
        dumpHttpHeader(System.out);
    }
        
    public void dumpHttpHeader(final PrintStream stream) throws IOException {
        if (this.contentHeaderStream == null) {
            return;
        }
        // Dump the httpHeaderStream to STDOUT
        for (int available = this.contentHeaderStream.available();
            this.contentHeaderStream != null
                && (available = this.contentHeaderStream.available()) > 0;) {
            // We should be in this loop only once and should do this
            // buffer allocation once.
            byte[] buffer = new byte[available];
            // The read nulls out httpHeaderStream when done with it so
            // need check for null in the loop control line.
            int read = read(buffer, 0, available);
            stream.write(buffer, 0, read);
        }
    }
    
    /**
     * Read header if present. Technique borrowed from HttpClient HttpParse
     * class. Using http parser code for now. Later move to more generic header
     * parsing code if there proves a need.
     * 
     * @return ByteArrayInputStream with the http header in it or null if no
     *         http header.
     * @throws IOException
     */
    private InputStream readContentHeaders() throws IOException {
        // If judged a record that doesn't have an http header, return
        // immediately.
        if (!hasContentHeaders()) {
            return null;
        }
        byte [] statusBytes = HttpParser.readRawLine(getIn());
        int eolCharCount = getEolCharsCount(statusBytes);
        if (eolCharCount <= 0) {
            throw new IOException("Failed to read raw lie where one " +
                " was expected: " + new String(statusBytes));
        }
        String statusLine = EncodingUtil.getString(statusBytes, 0,
            statusBytes.length - eolCharCount, ARCConstants.DEFAULT_ENCODING);
        if (statusLine == null) {
            throw new NullPointerException("Expected status line is null");
        }
        // TODO: Tighten up this test.
        boolean isHttpResponse = StatusLine.startsWithHTTP(statusLine);
        boolean isHttpRequest = false;
        if (!isHttpResponse) {
            isHttpRequest = statusLine.toUpperCase().startsWith("GET") ||
                !statusLine.toUpperCase().startsWith("POST");
        }
        if (!isHttpResponse && !isHttpRequest) {
            throw new UnexpectedStartLineIOException("Failed parse of " +
                "status line: " + statusLine);
        }
        this.statusCode = isHttpResponse?
            (new StatusLine(statusLine)).getStatusCode(): -1;
        
        // Save off all bytes read.  Keep them as bytes rather than
        // convert to strings so we don't have to worry about encodings
        // though this should never be a problem doing http headers since
        // its all supposed to be ascii.
        ByteArrayOutputStream baos =
            new ByteArrayOutputStream(statusBytes.length + 4 * 1024);
        baos.write(statusBytes);
        
        // Now read rest of the header lines looking for the separation
        // between header and body.
        for (byte [] lineBytes = null; true;) {
            lineBytes = HttpParser.readRawLine(getIn());
            eolCharCount = getEolCharsCount(lineBytes);
            if (eolCharCount <= 0) {
                throw new IOException("Failed reading headers: " +
                    ((lineBytes != null)? new String(lineBytes): null));
            }
            // Save the bytes read.
            baos.write(lineBytes);
            if ((lineBytes.length - eolCharCount) <= 0) {
                // We've finished reading the http header.
                break;
            }
        }
        
        byte [] headerBytes = baos.toByteArray();
        // Save off where content body, post content headers, starts.
        this.contentHeadersLength = headerBytes.length;
        ByteArrayInputStream bais =
            new ByteArrayInputStream(headerBytes);
        if (!bais.markSupported()) {
            throw new IOException("ByteArrayInputStream does not support mark");
        }
        bais.mark(headerBytes.length);
        // Read the status line.  Don't let it into the parseHeaders function.
        // It doesn't know what to do with it.
        bais.read(statusBytes, 0, statusBytes.length);
        this.contentHeaders = HttpParser.parseHeaders(bais,
            ARCConstants.DEFAULT_ENCODING);
        bais.reset();
        return bais;
    }
    
    public static class UnexpectedStartLineIOException
    extends RecoverableIOException {
        private static final long serialVersionUID = 1L;

        public UnexpectedStartLineIOException(final String reason) {
            super(reason);
        }
    }
    
    /**
     * @param bytes Array of bytes to examine for an EOL.
     * @return Count of end-of-line characters or zero if none.
     */
    private int getEolCharsCount(byte [] bytes) {
        int count = 0;
        if (bytes != null && bytes.length >=1 &&
                bytes[bytes.length - 1] == '\n') {
            count++;
            if (bytes.length >=2 && bytes[bytes.length -2] == '\r') {
                count++;
            }
        }
        return count;
    }
    
    /**
     * @return If headers are for a http response AND the headers have been
     * read, return status code.  Else return -1.
     */
    public int getStatusCode() {
        return this.statusCode;
    }

    /**
     * @return Returns length of content headers or -1 if headers have
     * not yet been read.
     */
    public int getContentHeadersLength() {
        return this.contentHeadersLength;
    }

    public Header[] getContentHeaders() {
        return contentHeaders;
    }
    
    /**
     * @return Next character in this ARCRecord's content else -1 if at end of
     * this record.
     * @throws IOException
     */
    public int read() throws IOException {
        int c = -1;
        if (this.contentHeaderStream != null &&
                (this.contentHeaderStream.available() > 0)) {
            // If http header, return bytes from it before we go to underlying
            // stream.
            c = this.contentHeaderStream.read();
            // If done with the header stream, null it out.
            if (this.contentHeaderStream.available() <= 0) {
                this.contentHeaderStream = null;
            }
            // do not increment position - 
            // the underlying ArchiveRecord stream allready did this
            // incrementPosition();
        } else {
            c = super.read();
        }
        return c;
    }

    public int read(byte [] b, int offset, int length) throws IOException {
        int read = -1;
        if (this.contentHeaderStream != null &&
                (this.contentHeaderStream.available() > 0)) {
            // If http header, return bytes from it before we go to underlying
            // stream.
            read = Math.min(length, this.contentHeaderStream.available());
            if (read == 0) {
                read = -1;
            } else {
                read = this.contentHeaderStream.read(b, offset, read);
            }
            // If done with the header stream, null it out.
            if (this.contentHeaderStream.available() <= 0) {
                this.contentHeaderStream = null;
            }
            // do not increment position - 
            // the underlying ArchiveRecord stream allready did this
            //incrementPosition();
        } else {
            read = super.read(b, offset, length);
        }
        return read;
    }
    
    @Override
    public int available() {
        return ((ArchiveRecord)this.in).available();
    }

    @Override
    public void close() throws IOException {
        ((ArchiveRecord)this.in).close();
    }

    @Override
    public void dump() throws IOException {
        ((ArchiveRecord)this.in).dump();
    }

    @Override
    public void dump(OutputStream os) throws IOException {
        ((ArchiveRecord)this.in).dump(os);
    }

    @Override
    protected String getDigest4Cdx(ArchiveRecordHeader h) {
        return ((ArchiveRecord)this.in).getDigest4Cdx(h);
    }

    @Override
    public String getDigestStr() {
        return ((ArchiveRecord)this.in).getDigestStr();
    }

    @Override
    public ArchiveRecordHeader getHeader() {
        return ((ArchiveRecord)this.in).getHeader();
    }

    @Override
    protected String getIp4Cdx(ArchiveRecordHeader h) {
        return ((ArchiveRecord)this.in).getIp4Cdx(h);
    }

    @Override
    protected String getMimetype4Cdx(ArchiveRecordHeader h) {
        return ((ArchiveRecord)this.in).getMimetype4Cdx(h);
    }

    @Override
    protected long getPosition() {
        return ((ArchiveRecord)this.in).getPosition();
    }

    @Override
    protected String getStatusCode4Cdx(ArchiveRecordHeader h) {
        return ((ArchiveRecord)this.in).getStatusCode4Cdx(h);
    }

    @Override
    public boolean hasContentHeaders() {
        return ((ArchiveRecord)this.in).hasContentHeaders();
    }

    @Override
    protected void incrementPosition() {
        ((ArchiveRecord)this.in).incrementPosition();
    }

    @Override
    protected void incrementPosition(long incr) {
        ((ArchiveRecord)this.in).incrementPosition(incr);
    }

    @Override
    protected boolean isEor() {
        return ((ArchiveRecord)this.in).isEor();
    }

    @Override
    public boolean isStrict() {
        return ((ArchiveRecord)this.in).isStrict();
    }

    @Override
    public boolean markSupported() {
        return ((ArchiveRecord)this.in).markSupported();
    }

    @Override
    protected String outputCdx(String strippedFileName) throws IOException {
        return ((ArchiveRecord)this.in).outputCdx(strippedFileName);
    }

    @Override
    protected void setEor(boolean eor) {
        ((ArchiveRecord)this.in).setEor(eor);
    }

    @Override
    protected void setHeader(ArchiveRecordHeader header) {
        ((ArchiveRecord)this.in).setHeader(header);
    }

    @Override
    public void setStrict(boolean strict) {
        ((ArchiveRecord)this.in).setStrict(strict);
    }

    @Override
    void skip() throws IOException {
        ((ArchiveRecord)this.in).skip();
    }

    @Override
    public long skip(long n) throws IOException {
        return ((ArchiveRecord)this.in).skip(n);
    }
}
