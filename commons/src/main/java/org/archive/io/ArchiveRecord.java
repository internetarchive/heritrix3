/* $Id$
 *
 * Created on August 21st, 2006
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
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;

import org.archive.util.Base32;

/**
 * Archive file Record.
 * @author stack
 * @version $Date$ $Version$
 */
public abstract class ArchiveRecord extends InputStream {

    /**
     * Minimal http response or request header length.
     * 
     * I've seen in arcs content length of 1 with no header.
     */
    protected static final long MIN_HTTP_HEADER_LENGTH =
        Math.min("HTTP/1.1 200 OK\r\n".length(), "GET / HTTP/1.0\n\r".length());

    ArchiveRecordHeader header = null;

    /**
     * Stream to read this record from.
     *
     * Stream can only be read sequentially.  Will only return this records'
     * content returning a -1 if you try to read beyond the end of the current
     * record.
     *
     * <p>Streams can be markable or not.  If they are, we'll be able to roll
     * back when we've read too far.  If not markable, assumption is that
     * the underlying stream is managing our not reading too much (This pertains
     * to the skipping over the end of the ARCRecord.  See {@link #skip()}.
     */
    InputStream in = null;

    /**
     * Position w/i the Record content, within <code>in</code>.
     * This position is relative within this Record.  Its not same as the
     * Archive file position.
     */
    long position = 0;

    /**
     * Set flag when we've reached the end-of-record.
     */
    boolean eor = false;
    
    /**
     * Compute digest on what we read and add to metadata when done.
     * 
     * Currently hardcoded as sha-1. TODO: Remove when archive records
     * digest or else, add a facility that allows the arc reader to
     * compare the calculated digest to that which is recorded in
     * the arc.
     * 
     * <p>Protected instead of private so subclasses can update and complete
     * the digest.
     */
    protected MessageDigest digest = null;
    private String digestStr = null;

    boolean strict = false;
    

    /**
     * Constructor.
     *
     * @param in Stream cue'd up to be at the start of the record this instance
     * is to represent.
     * @throws IOException
     */
    public ArchiveRecord(InputStream in)
            throws IOException {
        this(in, null, 0, true, false);
    }
    
    /**
     * Constructor.
     *
     * @param in Stream cue'd up to be at the start of the record this instance
     * is to represent.
     * @param header Header data.
     * @throws IOException
     */
    public ArchiveRecord(InputStream in, ArchiveRecordHeader header)
            throws IOException {
        this(in, header, 0, true, false);
    }

    /**
     * Constructor.
     *
     * @param in Stream cue'd up to be at the start of the record this instance
     * is to represent.
     * @param header Header data.
     * @param bodyOffset Offset into the body.  Usually 0.
     * @param digest True if we're to calculate digest for this record.  Not
     * digesting saves about ~15% of cpu during an ARC parse.
     * @param strict Be strict parsing (Parsing stops if ARC inproperly
     * formatted).
     * @throws IOException
     */
    public ArchiveRecord(InputStream in, ArchiveRecordHeader header,
        int bodyOffset, boolean digest, boolean strict) 
    throws IOException {
        this.in = in;
        this.header = header;
        this.position = bodyOffset;
        if (digest) {
            try {
                this.digest = MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                // Convert to IOE because thats more amenable to callers
                // -- they are dealing with it anyways.
                throw new IOException(e.getMessage());
            }
        }
        this.strict = strict;
    }

    public boolean markSupported() {
        return false;
    }

    /**
     * @return Header data for this record.
     */
    public ArchiveRecordHeader getHeader() {
        return this.header;
    }
    
        protected void setHeader(ArchiveRecordHeader header) {
                this.header = header;
        }

    /**
     * Calling close on a record skips us past this record to the next record
     * in the stream.
     *
     * It does not actually close the stream.  The underlying steam is probably
     * being used by the next arc record.
     *
     * @throws IOException
     */
    public void close() throws IOException {
        if (this.in != null) {
            skip();
            this.in = null;
            if (this.digest != null) {
                this.digestStr = Base32.encode(this.digest.digest());
            }
        }
    }

    /**
         * @return Next character in this Record content else -1 if at EOR.
         * @throws IOException
         */
        public int read() throws IOException {
                int c = -1;
                if (available() > 0) {
                        c = this.in.read();
                        if (c == -1) {
                                throw new IOException("Premature EOF before end-of-record.");
                        }
                        if (this.digest != null) {
                                this.digest.update((byte) c);
                        }
                }
                incrementPosition();
                return c;
        }

    public int read(byte[] b, int offset, int length) throws IOException {
                int read = Math.min(length, available());
                if (read == -1 || read == 0) {
                        read = -1;
                } else {
                        read = this.in.read(b, offset, read);
                        if (read == -1) {
                                String msg = "Premature EOF before end-of-record: "
                                        + getHeader().getHeaderFields();
                                if (isStrict()) {
                                        throw new IOException(msg);
                                }
                                setEor(true);
                                System.err.println(Level.WARNING.toString() + " " + msg);
                        }
                        if (this.digest != null && read >= 0) {
                                this.digest.update(b, offset, read);
                        }
                }
                incrementPosition(read);
                return read;
        }

    /**
         * This available is not the stream's available. Its an available based on
         * what the stated Archive record length is minus what we've read to date.
         * 
         * @return True if bytes remaining in record content.
         */
    public int available() {
        long amount = getHeader().getLength() - getPosition();
        return (amount > Integer.MAX_VALUE? Integer.MAX_VALUE: (int)amount);
    }

    /**
     * Skip over this records content.
     *
     * @throws IOException
     */
    void skip() throws IOException {
        if (this.eor) {
            return;
        }
        
        // Read to the end of the body of the record.  Exhaust the stream.
        // Can't skip direct to end because underlying stream may be compressed
        // and we're calculating the digest for the record.
        int r = available();
        while (r > 0 && !this.eor) {
            skip(r);
            r = available();
        }
    }
    
    public long skip(long n) throws IOException {
        final int SKIP_BUFFERSIZE = 1024 * 4;
        byte[] b = new byte[SKIP_BUFFERSIZE];
        long total = 0;
        for (int read = 0; (total < n) && (read != -1);) {
            read = Math.min(SKIP_BUFFERSIZE, (int) (n - total));
            // TODO: Interesting is that reading from compressed stream, we only
            // read about 500 characters at a time though we ask for 4k.
            // Look at this sometime.
            read = read(b, 0, read);
            if (read <= 0) {
                read = -1;
            } else {
                total += read;
            }
        }
        return total;
    }

    /**
     * @return Returns the strict.
     */
    public boolean isStrict() {
        return this.strict;
    }

    /**
     * @param strict The strict to set.
     */
    public void setStrict(boolean strict) {
        this.strict = strict;
    }

        protected InputStream getIn() {
                return this.in;
        }

        public String getDigestStr() {
                return this.digestStr;
        }
        
        protected void incrementPosition() {
                this.position++;
        }
        
        protected void incrementPosition(final long incr) {
                this.position += incr;
        }
        
        protected long getPosition() {
                return this.position;
        }

        protected boolean isEor() {
                return eor;
        }

        protected void setEor(boolean eor) {
                this.eor = eor;
        }
        
        protected String getStatusCode4Cdx(final ArchiveRecordHeader h) {
                return "-";
        }
        
        protected String getIp4Cdx(final ArchiveRecordHeader h) {
                return "-";
        }
        
        protected String getDigest4Cdx(final ArchiveRecordHeader h) {
                return getDigestStr() == null? "-": getDigestStr();
        }
    
    protected String getMimetype4Cdx(final ArchiveRecordHeader h) {
        return h.getMimetype();
    }

    protected String outputCdx(final String strippedFileName)
    throws IOException {
        // Read the whole record so we get out a hash. Should be safe calling
        // close on already closed Record.
        close();
        ArchiveRecordHeader h = getHeader();
        StringBuilder buffer =
                new StringBuilder(ArchiveFileConstants.CDX_LINE_BUFFER_SIZE);
        buffer.append(h.getDate());
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(getIp4Cdx(h));
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(h.getUrl());
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(getMimetype4Cdx(h));
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(getStatusCode4Cdx(h));
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(getDigest4Cdx(h));
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(h.getOffset());
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(h.getLength());
        buffer.append(ArchiveFileConstants.SINGLE_SPACE);
        buffer.append(strippedFileName != null? strippedFileName: '-');
        return buffer.toString();
    }
    
    /**
     * Writes output on STDOUT.
     * @throws IOException
     */
    public void dump()
    throws IOException {
        dump(System.out);
    }
    
    /**
     * Writes output on passed <code>os</code>.
     * @throws IOException
     */
    public void dump(final OutputStream os)
    throws IOException {
        final byte [] outputBuffer = new byte [16*1024];
        int read = outputBuffer.length;
        while ((read = read(outputBuffer, 0, outputBuffer.length)) != -1) {
            os.write(outputBuffer, 0, read);
        }
        os.flush();
    }
    
    /**
     * Is it likely that this record contains headers?
     * This method will return true if the body is a http response that includes
     * http response headers or the body is a http request that includes request
     * headers, etc. Be aware that headers in content are distinct from
     * {@link ArchiveRecordHeader} 'headers'.
     * @return True if this Record's content has headers: 
     */
    public boolean hasContentHeaders() {
        final String url = getHeader().getUrl();
        if (url == null) {
            return false;
        }
        
        if (!url.toLowerCase().startsWith("http")) {
            return false;
        }
        
        if (getHeader().getLength() <= MIN_HTTP_HEADER_LENGTH) {
            return false;
        }
        
        return true;
    }
    
    protected void setBodyOffset(int bodyOffset) {
        this.position = bodyOffset;
    }
}
