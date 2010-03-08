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

package org.archive.io.arc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpParser;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.apache.commons.lang.StringUtils;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.RecoverableIOException;
import org.archive.util.InetAddressUtil;
import org.archive.util.TextUtils;

/**
 * An ARC file record.
 * Does not compass the ARCRecord metadata line, just the record content.
 * @author stack
 */
public class ARCRecord extends ArchiveRecord implements ARCConstants {
    /**
     * Http status line object.
     * 
     * May be null if record is not http.
     */
    private StatusLine httpStatus = null;

    /**
     * Http header bytes.
     * 
     * If non-null and bytes available, give out its contents before we
     * go back to the underlying stream.
     */
    private InputStream httpHeaderStream = null;
    
    /**
     * Http headers.
     * 
     * Only populated after reading of headers.
     */
    private Header [] httpHeaders = null;

    /**
     * Array of field names.
     * 
     * Used to initialize <code>headerFieldNameKeys</code>.
     */
    private final String [] headerFieldNameKeysArray = {
        URL_FIELD_KEY,
        IP_HEADER_FIELD_KEY,
        DATE_FIELD_KEY,
        MIMETYPE_FIELD_KEY,
        LENGTH_FIELD_KEY
    };
    
    /**
     * An array of the header field names found in the ARC file header on
     * the 3rd line.
     * 
     * We used to read these in from the arc file first record 3rd line but
     * now we hardcode them for sake of improved performance.
     */
    private final List<String> headerFieldNameKeys =
        Arrays.asList(this.headerFieldNameKeysArray);

    /**
     * Http header bytes read while trying to read http header
     */
    public long httpHeaderBytesRead = -1;
    
    /**
     * record length from metadata line
     */
    public long recordDeclaredLength;
    
    /**
     * null if source was not compressed
     */
    public long compressedBytes; 
    
    /**
     * actual payload data (not including trailing newline), 
     * should match record-declared-length 
     */
    public long uncompressedBytes;

    /**
     * content-length header, iff HTTP and present, null otherwise 
     */
    public long httpPayloadDeclaredLength;

    /**
     * actual http payload length, should match http-payload-declared-length 
     */
    public long httpPayloadActualLength;
    
    /**
     * errors encountered reading record
     */
    public List<ArcRecordErrors> errors = new ArrayList<ArcRecordErrors>();

    /**
     * verbatim ARC record header string
     */
    private String headerString;
    public String getHeaderString() {
        return this.headerString;
    }
    
    /**
     * Constructor.
     *
     * @param in Stream cue'd up to be at the start of the record this instance
     * is to represent.
     * @param metaData Meta data.
     * @throws IOException
     */
    public ARCRecord(InputStream in, ArchiveRecordHeader metaData)
                throws IOException {
        this(in, metaData, 0, true, false, true);
    }

    /**
     * Constructor.
     *
     * @param in Stream cue'd up to be at the start of the record this instance
     * is to represent.
     * @param metaData Meta data.
     * @param bodyOffset Offset into the body.  Usually 0.
     * @param digest True if we're to calculate digest for this record.  Not
     * digesting saves about ~15% of cpu during an ARC parse.
     * @param strict Be strict parsing (Parsing stops if ARC inproperly
     * formatted).
     * @param parseHttpHeaders True if we are to parse HTTP headers.  Costs
     * about ~20% of CPU during an ARC parse.
     * @throws IOException
     */
    public ARCRecord(InputStream in, ArchiveRecordHeader metaData,
        int bodyOffset, boolean digest, boolean strict,
        final boolean parseHttpHeaders) 
    throws IOException {
        super(in, metaData, bodyOffset, digest, strict);
        if (parseHttpHeaders) {
            this.httpHeaderStream = readHttpHeader();
        }
    }
    
    /**
     * Constructor.
     *
     * @param in Stream cue'd up to be at the start of the records metadata 
     * this instance is to represent. 
     * @param identifier Identifier for this the hosting Reader.
     * @param offset Current offset into <code>in</code> (Used to keep
     * <code>position</code> properly aligned).  Usually 0.
     * @param digest True if we're to calculate digest for this record.  Not
     * digesting saves about ~15% of cpu during an ARC parse.
     * @param strict Be strict parsing (Parsing stops if ARC inproperly
     * formatted).
     * @param parseHttpHeaders True if we are to parse HTTP headers.  Costs
     * about ~20% of CPU during an ARC parse.
     * @param isAllignedOnFirstRecord True if this is the first record to be
     * read from an archive
     * @param String version Version information to be returned to the
     * ARCReader constructing this record 
     * 
     * @throws IOException
     */
    public ARCRecord(InputStream in, final String identifier, 
                final long offset, boolean digest,      boolean strict, 
                final boolean parseHttpHeaders, 
                final boolean isAlignedOnFirstRecord, String version) 
    throws IOException {
        super(in, null, 0, digest, strict);
        setHeader(parseHeaders(in, identifier, offset, strict, isAlignedOnFirstRecord, version));
        if (parseHttpHeaders) {
            this.httpHeaderStream = readHttpHeader();
        }
    }
    
    /**
     * Constructor.
     *
     * @param in Stream cue'd up to be at the start of the records metadata 
     * this instance is to represent.
     * @param identifier Identifier for this the hosting Reader.
     * @param offset Current offset into <code>in</code> (Used to keep
     * <code>position</code> properly aligned).  Usually 0.
     * @param digest True if we're to calculate digest for this record.  Not
     * digesting saves about ~15% of cpu during an ARC parse.
     * @param strict Be strict parsing (Parsing stops if ARC inproperly
     * formatted).
     * @param parseHttpHeaders True if we are to parse HTTP headers.  Costs
     * about ~20% of CPU during an ARC parse.
     * 
     * @throws IOException
     */
    public ARCRecord(InputStream in, final String identifier, 
                final long offset, boolean digest,      boolean strict, 
                final boolean parseHttpHeaders) 
    throws IOException {
        this(in, identifier, offset, digest, strict, parseHttpHeaders, 
                false, null);
    }
    
    private ArchiveRecordHeader parseHeaders(final InputStream in,
        final String identifier, final long offset, final boolean strict, 
        final boolean isAlignedOnFirstRecord, String version)
    throws IOException {
        
        ArrayList<String> firstLineValues = new ArrayList<String>(20);
        getTokenizedHeaderLine(in, firstLineValues);
        
        int bodyOffset = 0;
        if (offset == 0 && isAlignedOnFirstRecord) {
            // If offset is zero and we were aligned at first record on
            // creation (See #alignedOnFirstRecord for more on this), then no
            // records have been read yet and we're reading our first one, the
            // record of ARC file meta info.  Its special.  In ARC versions
            // 1.x, first record has three lines of meta info. We've just read
            // the first line. There are two more.  The second line has misc.
            // info.  We're only interested in the first field, the version
            // number.  The third line is the list of field names. Here's what
            // ARC file version 1.x meta content looks like:
            //
            // filedesc://testIsBoundary-JunitIAH200401070157520.arc 0.0.0.0 \\
            //      20040107015752 text/plain 77
            // 1 0 InternetArchive
            // URL IP-address Archive-date Content-type Archive-length
            //
            ArrayList<String> secondLineValues = new ArrayList<String>(20);
            bodyOffset += getTokenizedHeaderLine(in, secondLineValues);
            version = ((String)secondLineValues.get(0) +
                "." + (String)secondLineValues.get(1));
            // Just read over the 3rd line.  We used to parse it and use
            // values found here but now we just hardcode them to avoid
            // having to read this 3rd line even for random arc file accesses.
            bodyOffset += getTokenizedHeaderLine(in, null);
            // this.position = bodyOffset;
        }
        setBodyOffset(bodyOffset);
        
        return computeMetaData(this.headerFieldNameKeys, firstLineValues, version, offset, identifier);
    }
    
    /**
     * Get a record header line as list of tokens.
     *
     * We keep reading till we find a LINE_SEPARATOR or we reach the end
     * of file w/o finding a LINE_SEPARATOR or the line length is crazy.
     *
     * @param stream InputStream to read from.
     * @param list Empty list that gets filled w/ string tokens.
     * @return Count of characters read.
     * @exception IOException If problem reading stream or no line separator
     * found or EOF before EOL or we didn't get minimum header fields.
     */
    private int getTokenizedHeaderLine(final InputStream stream,
            List<String> list) throws IOException {
        // Preallocate usual line size.
        StringBuilder buffer = new StringBuilder(2048 + 20);
        int read = 0;
        int previous = -1;
        for (int c = -1; true;) {
                previous = c;
            c = stream.read();
            if (c == -1) {
                throw new RecoverableIOException("Hit EOF before header EOL.");
            }
            c &= 0xff; 
            read++;
            if (read > MAX_HEADER_LINE_LENGTH) {
                throw new IOException("Header line longer than max allowed " +
                    " -- " + String.valueOf(MAX_HEADER_LINE_LENGTH) +
                    " -- or passed buffer doesn't contain a line (Read: " +
                    buffer.length() + ").  Here's" +
                    " some of what was read: " +
                    buffer.substring(0, Math.min(buffer.length(), 256)));
            }

            if (c == LINE_SEPARATOR) {
                if (buffer.length() == 0) {
                    // Empty line at start of buffer.  Skip it and try again.
                    continue;
                }

                if (list != null) {
                    list.add(buffer.toString());
                }
                // LOOP TERMINATION.
                break;
            } else if (c == HEADER_FIELD_SEPARATOR) {
                if (!isStrict() && previous == HEADER_FIELD_SEPARATOR) {
                        // Early ARCs sometimes had multiple spaces between fields.
                        continue;
                }
                if (list != null) {
                    list.add(buffer.toString());
                }
                // reset to empty
                buffer.setLength(0);
            } else {
                buffer.append((char)c);
            }
        }

        // List must have at least 3 elements in it and no more than 10.  If
        // it has other than this, then bogus parse.
        if (list != null && (list.size() < 3 || list.size() > 100)) {
            throw new IOException("Unparseable header line: " + list);
        }

        // save verbatim header String
        this.headerString = StringUtils.join(list," ");
        
        return read;
    }
    
    /**
     * Compute metadata fields.
     *
     * Here we check the meta field has right number of items in it.
     *
     * @param keys Keys to use composing headerFields map.
     * @param values Values to set into the headerFields map.
     * @param v The version of this ARC file.
     * @param offset Offset into arc file.
     *
     * @return Metadata structure for this record.
     *
     * @exception IOException  If no. of keys doesn't match no. of values.
     */
    private ARCRecordMetaData computeMetaData(List<String> keys,
                List<String> values, String v, long offset, final String identifier)
    throws IOException {
        if (keys.size() != values.size()) {
            List<String> originalValues = values;
            if (!isStrict()) {
                values = fixSpaceInURL(values, keys.size());
                // If values still doesn't match key size, try and do
                // further repair.
                    if (keys.size() != values.size()) {
                        // Early ARCs had a space in mimetype.
                        if (values.size() == (keys.size() + 1) &&
                                        values.get(4).toLowerCase().startsWith("charset=")) {
                                List<String> nuvalues =
                                        new ArrayList<String>(keys.size());
                                nuvalues.add(0, values.get(0));
                                nuvalues.add(1, values.get(1));
                                nuvalues.add(2, values.get(2));
                                nuvalues.add(3, values.get(3) + values.get(4));
                                nuvalues.add(4, values.get(5));
                                values = nuvalues;
                        } else if((values.size() + 1) == keys.size() &&
                            isLegitimateIPValue(values.get(1)) &&
                            isDate(values.get(2)) && isNumber(values.get(3))) {
                        // Mimetype is empty.
                        List<String> nuvalues =
                            new ArrayList<String>(keys.size());
                        nuvalues.add(0, values.get(0));
                        nuvalues.add(1, values.get(1));
                        nuvalues.add(2, values.get(2));
                        nuvalues.add(3, "-");
                        nuvalues.add(4, values.get(3));
                        values = nuvalues;
                    }
                    }
                }
            if (keys.size() != values.size()) {
                throw new IOException("Size of field name keys does" +
                    " not match count of field values: " + values);
            }
            // Note that field was fixed on stderr.
            System.err.println(Level.WARNING.toString() + "Fixed spaces in metadata line at " +
                "offset " + offset +
                " Original: " + originalValues + ", New: " + values);
        }
        
        Map<String, Object> headerFields =
                new HashMap<String, Object>(keys.size() + 2);
        for (int i = 0; i < keys.size(); i++) {
            headerFields.put(keys.get(i), values.get(i));
        }
        
        // Add a check for tabs in URLs.  If any, replace with '%09'.
        // See https://sourceforge.net/tracker/?group_id=73833&atid=539099&func=detail&aid=1010966,
        // [ 1010966 ] crawl.log has URIs with spaces in them.
        String url = (String)headerFields.get(URL_FIELD_KEY);
        if (url != null && url.indexOf('\t') >= 0) {
            headerFields.put(URL_FIELD_KEY,
                TextUtils.replaceAll("\t", url, "%09"));
        }

        headerFields.put(VERSION_FIELD_KEY, v);
        headerFields.put(ABSOLUTE_OFFSET_KEY, new  Long(offset));

        return new ARCRecordMetaData(identifier, headerFields);
    }

    /**
     * Fix space in URLs.
     * The ARCWriter used to write into the ARC URLs with spaces in them.
     * See <a
     * href="https://sourceforge.net/tracker/?group_id=73833&atid=539099&func=detail&aid=1010966">[ 1010966 ]
     * crawl.log has URIs with spaces in them</a>.
     * This method does fix up on such headers converting all spaces found
     * to '%20'.
     * @param values List of metadata values.
     * @param requiredSize Expected size of resultant values list.
     * @return New list if we successfully fixed up values or original if
     * fixup failed.
     */
    private List<String> fixSpaceInURL(List<String> values, int requiredSize) {
        // Do validity check. 3rd from last is a date of 14 numeric
        // characters. The 4th from last is IP, all before the IP
        // should be concatenated together with a '%20' joiner.
        // In the below, '4' is 4th field from end which has the IP.
        if (!(values.size() > requiredSize) || values.size() < 4) {
            return values;
        }
        // Test 3rd field is valid date.
        if (!isDate((String) values.get(values.size() - 3))) {
            return values;
        }

        // Test 4th field is valid IP.
        if (!isLegitimateIPValue((String) values.get(values.size() - 4))) {
            return values;
        }

        List<String> newValues = new ArrayList<String>(requiredSize);
        StringBuffer url = new StringBuffer();
        for (int i = 0; i < (values.size() - 4); i++) {
            if (i > 0) {
                url.append("%20");
            }
            url.append(values.get(i));
        }
        newValues.add(url.toString());
        for (int i = values.size() - 4; i < values.size(); i++) {
            newValues.add(values.get(i));
        }
        return newValues;
    }
    
    private boolean isDate(final String date) {
        if (date.length() != 14) {
            return false;
        }
        return isNumber(date);
    }
    
    private boolean isNumber(final String n) {
        for (int i = 0; i < n.length(); i++) {
            if (!Character.isDigit(n.charAt(i))) {
                return false;
            }
        }
        return true;
    }
    
    private boolean isLegitimateIPValue(final String ip) {
        if ("-".equals(ip)) {
            return true;
        }
        Matcher m = InetAddressUtil.IPV4_QUADS.matcher(ip);
        return m != null && m.matches();
    }
    
    /**
     * Skip over the the http header if one present.
     * 
     * Subsequent reads will get the body.
     * 
     * <p>Calling this method in the midst of reading the header
     * will make for strange results.  Otherwise, safe to call
     * at any time though before reading any of the arc record
     * content is only time that it makes sense.
     * 
     * <p>After calling this method, you can call
     * {@link #getHttpHeaders()} to get the read http header.
     * 
     * @throws IOException
     */
    public void skipHttpHeader() throws IOException {
        if (this.httpHeaderStream != null) {
            // Empty the httpHeaderStream
            for (int available = this.httpHeaderStream.available();
                        this.httpHeaderStream != null &&
                                (available = this.httpHeaderStream.available()) > 0;) {
                // We should be in this loop once only we should only do this
                // buffer allocation once.
                byte [] buffer = new byte[available];
                // The read nulls out httpHeaderStream when done with it so
                // need check for null in the loop control line.
                read(buffer, 0, available);
            }
        }
    }
    
    public void dumpHttpHeader() throws IOException {
                if (this.httpHeaderStream == null) {
                        return;
                }
                // Dump the httpHeaderStream to STDOUT
                for (int available = this.httpHeaderStream.available();
                        this.httpHeaderStream != null
                                && (available = this.httpHeaderStream.available()) > 0;) {
                        // We should be in this loop only once and should do this
                        // buffer allocation once.
                        byte[] buffer = new byte[available];
                        // The read nulls out httpHeaderStream when done with it so
                        // need check for null in the loop control line.
                        int read = read(buffer, 0, available);
                        System.out.write(buffer, 0, read);
                }
        }
    
    /**
         * Read http header if present. Technique borrowed from HttpClient HttpParse
         * class. set errors when found.
         * 
         * @return ByteArrayInputStream with the http header in it or null if no
         *         http header.
         * @throws IOException
         */
    private InputStream readHttpHeader() throws IOException {
    	
    	// this can be helpful when simply iterating over records, 
    	// looking for problems.
        Logger logger = Logger.getLogger(this.getClass().getName());
    	ArchiveRecordHeader h = this.getHeader();
    	
        // If judged a record that doesn't have an http header, return
        // immediately.
        String url = getHeader().getUrl();
        if(!url.startsWith("http") ||
            getHeader().getLength() <= MIN_HTTP_HEADER_LENGTH) {
            return null;
        }
        byte [] statusBytes = HttpParser.readRawLine(getIn());
        int eolCharCount = getEolCharsCount(statusBytes);
        if (eolCharCount <= 0) {
            throw new RecoverableIOException(
                "Failed to read http status where one was expected: " 
                + ((statusBytes == null) ? "" : new String(statusBytes)));
        }
        String statusLine = EncodingUtil.getString(statusBytes, 0,
            statusBytes.length - eolCharCount, ARCConstants.DEFAULT_ENCODING);
        if ((statusLine == null) ||
                !StatusLine.startsWithHTTP(statusLine)) {
            if (statusLine.startsWith("DELETED")) {
                // Some old ARCs have deleted records like following:
                // http://vireo.gatech.edu:80/ebt-bin/nph-dweb/dynaweb/SGI_Developer/SGITCL_PG/@Generic__BookTocView/11108%3Btd%3D2 130.207.168.42 19991010131803 text/html 29202
                // DELETED_TIME=20000425001133_DELETER=Kurt_REASON=alexalist
                // (follows ~29K spaces)
                // For now, throw a RecoverableIOException so if iterating over
                // records, we keep going.  TODO: Later make a legitimate
                // ARCRecord from the deleted record rather than throw
                // exception.
                throw new DeletedARCRecordIOException(statusLine);
            } else {
            	this.errors.add(ArcRecordErrors.HTTP_STATUS_LINE_INVALID);
            }
        }

        try {
        	this.httpStatus = new StatusLine(statusLine);
        } catch(IOException e) {
        	logger.warning(e.getMessage() + " at offset: " + h.getOffset());
        	this.errors.add(ArcRecordErrors.HTTP_STATUS_LINE_EXCEPTION);
        }
        
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
            	if (getIn().available() == 0) {
            		httpHeaderBytesRead += statusBytes.length;
                	logger.warning("HTTP header truncated at offset: " + h.getOffset());
            		this.errors.add(ArcRecordErrors.HTTP_HEADER_TRUNCATED);
            		this.setEor(true);
            		break;
            	} else {
            		throw new IOException("Failed reading http headers: " +
            				((lineBytes != null)? new String(lineBytes): null));
            	}
            } else {
            	httpHeaderBytesRead += lineBytes.length;
            }
            // Save the bytes read.
            baos.write(lineBytes);
            if ((lineBytes.length - eolCharCount) <= 0) {
                // We've finished reading the http header.
                break;
            }
        }
        
        byte [] headerBytes = baos.toByteArray();
        // Save off where body starts.
        this.getMetaData().setContentBegin(headerBytes.length);
        ByteArrayInputStream bais =
            new ByteArrayInputStream(headerBytes);
        if (!bais.markSupported()) {
            throw new IOException("ByteArrayInputStream does not support mark");
        }
        bais.mark(headerBytes.length);
        // Read the status line.  Don't let it into the parseHeaders function.
        // It doesn't know what to do with it.
        bais.read(statusBytes, 0, statusBytes.length);
        this.httpHeaders = HttpParser.parseHeaders(bais,
            ARCConstants.DEFAULT_ENCODING);
        this.getMetaData().setStatusCode(Integer.toString(getStatusCode()));
        bais.reset();
        return bais;
    }
    
    private static class DeletedARCRecordIOException
    extends RecoverableIOException {
        private static final long serialVersionUID = 1L;

        public DeletedARCRecordIOException(final String reason) {
            super(reason);
        }
    }
    
    /**
     * Return status code for this record.
     * 
     * This method will return -1 until the http header has been read.
     * @return Status code.
     */
    public int getStatusCode() {
        return (this.httpStatus == null)? -1: this.httpStatus.getStatusCode();
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
     * @return Meta data for this record.
     */
    public ARCRecordMetaData getMetaData() {
        return (ARCRecordMetaData)getHeader();
    }
    
    /**
     * @return http headers (Only available after header has been read).
     */
    public Header [] getHttpHeaders() {
        return this.httpHeaders;
    }
    
    /**
     * @return ArcRecordErrors encountered when reading 
     */
    public List<ArcRecordErrors> getErrors() {
    	return this.errors;
    }
    
    /**
     * @return true if ARC record errors found 
     */
    public boolean hasErrors() {
    	return !this.errors.isEmpty();
    }
    
    /**
     * @return Next character in this ARCRecord's content else -1 if at end of
     * this record.
     * @throws IOException
     */
    public int read() throws IOException {
        int c = -1;
        if (this.httpHeaderStream != null &&
                (this.httpHeaderStream.available() > 0)) {
            // If http header, return bytes from it before we go to underlying
            // stream.
            c = this.httpHeaderStream.read();
            // If done with the header stream, null it out.
            if (this.httpHeaderStream.available() <= 0) {
                this.httpHeaderStream = null;
            }
            incrementPosition();
        } else {
            c = super.read();
        }
        return c;
    }

    public int read(byte [] b, int offset, int length) throws IOException {
        int read = -1;
        if (this.httpHeaderStream != null &&
                (this.httpHeaderStream.available() > 0)) {
            // If http header, return bytes from it before we go to underlying
            // stream.
            read = Math.min(length, this.httpHeaderStream.available());
            if (read == 0) {
                read = -1;
            } else {
                read = this.httpHeaderStream.read(b, offset, read);
            }
            // If done with the header stream, null it out.
            if (this.httpHeaderStream.available() <= 0) {
                this.httpHeaderStream = null;
            }
            incrementPosition(read);
        } else {
            read = super.read(b, offset, length);
        }
        return read;
    }

    /**
     * @return Offset at which the body begins (Only known after
     * header has been read) or -1 if none or if we haven't read
     * headers yet.  Usually length of HTTP headers (does not include ARC
     * metadata line length).
     */
    public int getBodyOffset() {
        return this.getMetaData().getContentBegin();
    }
    
    @Override
    protected String getIp4Cdx(ArchiveRecordHeader h) {
        String result = null;
        if (h instanceof ARCRecordMetaData) {
                result = ((ARCRecordMetaData)h).getIp();
        }
        return (result != null)? result: super.getIp4Cdx(h);
    }
    
    @Override
        protected String getStatusCode4Cdx(ArchiveRecordHeader h) {
                String result = null;
                if (h instanceof ARCRecordMetaData) {
                        result = ((ARCRecordMetaData) h).getStatusCode();
                }
                return (result != null) ? result: super.getStatusCode4Cdx(h);
        }
    
    @Override
        protected String getDigest4Cdx(ArchiveRecordHeader h) {
                String result = null;
                if (h instanceof ARCRecordMetaData) {
                        result = ((ARCRecordMetaData) h).getDigest();
                }
                return (result != null) ? result: super.getDigest4Cdx(h);
        }
}