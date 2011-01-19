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

package org.archive.io.warc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.UTF8Bytes;
import org.archive.io.WriterPoolMember;
import org.archive.util.ArchiveUtils;
import org.archive.util.anvl.ANVLRecord;
import org.archive.util.anvl.Element;


/**
 * WARC implementation.
 *
 * <p>Assumption is that the caller is managing access to this
 * WARCWriter ensuring only one thread accessing this WARC instance
 * at any one time.
 * 
 * <p>While being written, WARCs have a '.open' suffix appended.
 *
 * @contributor stack
 * @version $Revision: 4604 $ $Date: 2006-09-05 22:38:18 -0700 (Tue, 05 Sep 2006) $
 */
public class WARCWriter extends WriterPoolMember
implements WARCConstants {
    public static final String TOTALS = "totals";
    public static final String SIZE_ON_DISK = "sizeOnDisk";
    public static final String TOTAL_BYTES = "totalBytes";
    public static final String CONTENT_BYTES = "contentBytes";
    public static final String NUM_RECORDS = "numRecords";

    private static final Logger logger = 
        Logger.getLogger(WARCWriter.class.getName());
    
    /**
     * NEWLINE as bytes.
     */
    public static byte [] CRLF_BYTES;
    static {
        try {
            CRLF_BYTES = CRLF.getBytes(DEFAULT_ENCODING);
        } catch(Exception e) {
            e.printStackTrace();
        }
    };

    /**
     * Temporarily accumulates stats managed externally by
     * {@link WARCWriterProcessor}. WARCWriterProcessor will call
     * {@link #resetTmpStats()}, write some records, then add
     * {@link #getTmpStats()} into its long-term running totals.
     */
    private Map<String,Map<String,Long>> tmpStats; 
    
    /**
     * Constructor.
     * Takes a stream. Use with caution. There is no upperbound check on size.
     * Will just keep writing.  Only pass Streams that are bounded. 
     * @param serialNo  used to generate unique file name sequences
     * @param out Where to write.
     * @param f File the <code>out</code> is connected to.
     * @param cmprs Compress the content written.
     * @param a14DigitDate If null, we'll write current time.
     * @throws IOException
     */
    public WARCWriter(final AtomicInteger serialNo,
    		final OutputStream out, final File f,
    		final WARCWriterPoolSettings settings)
    throws IOException {
        super(serialNo, out, f, settings);
    }
            
    /**
     * Constructor.
     *
     * @param dirs Where to drop files.
     * @param prefix File prefix to use.
     * @param cmprs Compress the records written. 
     * @param maxSize Maximum size for ARC files written.
     * @param suffix File tail to use.  If null, unused.
     * @param warcinfoData File metadata for warcinfo record.
     */
    public WARCWriter(final AtomicInteger serialNo,
            final WARCWriterPoolSettings settings) {
        super(serialNo, settings, WARC_FILE_EXTENSION);
    }
    
    @Override
    protected String createFile(File file) throws IOException {
    	String filename = super.createFile(file);
    	writeWarcinfoRecord(filename);
        return filename;
    }
    
    protected void baseCharacterCheck(final char c, final String parameter)
    throws IllegalArgumentException {
        // TODO: Too strict?  UNICODE control characters?
        if (Character.isISOControl(c) || !Character.isValidCodePoint(c)) {
            throw new IllegalArgumentException("Contains illegal character 0x" +
                Integer.toHexString(c) + ": " + parameter);
        }
    }
    
    protected String checkHeaderValue(final String value)
    throws IllegalArgumentException {
        for (int i = 0; i < value.length(); i++) {
        	final char c = value.charAt(i);
        	baseCharacterCheck(c, value);
        	if (Character.isWhitespace(c)) {
                throw new IllegalArgumentException("Contains disallowed white space 0x" +
                    Integer.toHexString(c) + ": " + value);
        	}
        }
        return value;
    }
    
    protected String checkHeaderLineMimetypeParameter(final String parameter)
    throws IllegalArgumentException {
    	StringBuilder sb = new StringBuilder(parameter.length());
    	boolean wasWhitespace = false;
        for (int i = 0; i < parameter.length(); i++) {
        	char c = parameter.charAt(i);
        	if (Character.isWhitespace(c)) {
        		// Map all to ' ' and collapse multiples into one.
        		// TODO: Make sure white space occurs in legal location --
        		// before parameter or inside quoted-string.
        		if (wasWhitespace) {
        			continue;
        		}
        		wasWhitespace = true;
        		c = ' ';
        	} else {
        		wasWhitespace = false;
        		baseCharacterCheck(c, parameter);
        	}
        	sb.append(c);
        }
        
        return sb.toString();
    }

    protected String createRecordHeader(final String type,
    		final String url, final String create14DigitDate,
    		final String mimetype, final URI recordId,
    		final ANVLRecord xtraHeaders, final long contentLength)
    throws IllegalArgumentException {
    	final StringBuilder sb =
    		new StringBuilder(2048/*A SWAG: TODO: Do analysis.*/);
    	sb.append(WARC_ID).append(CRLF);
        sb.append(HEADER_KEY_TYPE).append(COLON_SPACE).append(type).
            append(CRLF);
        // Do not write a subject-uri if not one present.
        if (url != null && url.length() > 0) {
            sb.append(HEADER_KEY_URI).append(COLON_SPACE).
                append(checkHeaderValue(url)).append(CRLF);
        }
        sb.append(HEADER_KEY_DATE).append(COLON_SPACE).
            append(create14DigitDate).append(CRLF);
        if (xtraHeaders != null) {
            for (final Iterator<Element> i = xtraHeaders.iterator(); i.hasNext();) {
                sb.append(i.next()).append(CRLF);
            }
        }

        sb.append(HEADER_KEY_ID).append(COLON_SPACE).append('<').
            append(recordId.toString()).append('>').append(CRLF);
        if (contentLength > 0) {
            sb.append(CONTENT_TYPE).append(COLON_SPACE).append(
                checkHeaderLineMimetypeParameter(mimetype)).append(CRLF);
        }
        sb.append(CONTENT_LENGTH).append(COLON_SPACE).
            append(Long.toString(contentLength)).append(CRLF);
    	
    	return sb.toString();
    }

    /**
     * @deprecated Use {@link #writeRecord(String,String,String,String,URI,ANVLRecord,InputStream,long,boolean)} instead
     */
    protected void writeRecord(final String type, final String url,
    		final String create14DigitDate, final String mimetype,
    		final URI recordId, ANVLRecord xtraHeaders,
            final InputStream contentStream, final long contentLength)
    throws IOException {
        writeRecord(type, url, create14DigitDate, mimetype, recordId, xtraHeaders, contentStream, contentLength, true);
    }

    protected void writeRecord(final String type, final String url,
            final String create14DigitDate, final String mimetype,
            final URI recordId, ANVLRecord xtraHeaders,
            final InputStream contentStream, final long contentLength, 
            boolean enforceLength)
    throws IOException {
        if (!TYPES_LIST.contains(type)) {
            throw new IllegalArgumentException("Unknown record type: " + type);
        }
        if (contentLength == 0 &&
                (xtraHeaders == null || xtraHeaders.size() <= 0)) {
            throw new IllegalArgumentException("Cannot write record " +
            "of content-length zero and base headers only.");
        }

        String header;
        try {
            header = createRecordHeader(type, url,
                    create14DigitDate, mimetype, recordId, xtraHeaders,
                    contentLength);

        } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE,"could not write record type: " + type 
                    + "for URL: " + url, e);
            return;
        }

        long contentBytes = 0;
        long totalBytes = 0;
        long startPosition;

    	try {
    	    startPosition = getPosition();
            preWriteRecordTasks();

            // TODO: Revisit encoding of header.
            byte[] bytes = header.getBytes(WARC_HEADER_ENCODING);
            write(bytes);
            totalBytes += bytes.length;

            
            if (contentStream != null && contentLength > 0) {
                // Write out the header/body separator.
                write(CRLF_BYTES); // TODO: should this be written even for zero-length?
                totalBytes += CRLF_BYTES.length;
                contentBytes += copyFrom(contentStream, contentLength, enforceLength);
                totalBytes += contentBytes;
            }

            // Write out the two blank lines at end of all records.
            write(CRLF_BYTES);
            write(CRLF_BYTES);
            totalBytes += 2 * CRLF_BYTES.length;
        } finally {
            postWriteRecordTasks();
        }
        
        // TODO: should this be in the finally block?
        tally(type, contentBytes, totalBytes, getPosition() - startPosition);
    }
    
    // if compression is enabled, sizeOnDisk means compressed bytes; if not, it
    // should be the same as totalBytes (right?)
    protected void tally(String recordType, long contentBytes, long totalBytes, long sizeOnDisk) {
        if (tmpStats == null) {
            tmpStats = new HashMap<String, Map<String,Long>>();
        }

        // add to stats for this record type
        Map<String, Long> substats = tmpStats.get(recordType);
        if (substats == null) {
            substats = new HashMap<String, Long>();
            tmpStats.put(recordType, substats);
        }
        subtally(substats, contentBytes, totalBytes, sizeOnDisk);
        
        // add to totals
        substats = tmpStats.get(TOTALS);
        if (substats == null) {
            substats = new HashMap<String, Long>();
            tmpStats.put(TOTALS, substats);
        }
        subtally(substats, contentBytes, totalBytes, sizeOnDisk);
    }

    protected void subtally(Map<String, Long> substats, long contentBytes,
            long totalBytes, long sizeOnDisk) {
        
        if (substats.get(NUM_RECORDS) == null) {
            substats.put(NUM_RECORDS, 1l);
        } else {
            substats.put(NUM_RECORDS, substats.get(NUM_RECORDS) + 1);
        }
        
        if (substats.get(CONTENT_BYTES) == null) {
            substats.put(CONTENT_BYTES, contentBytes);
        } else {
            substats.put(CONTENT_BYTES, substats.get(CONTENT_BYTES) + contentBytes);
        }
        
        if (substats.get(TOTAL_BYTES) == null) {
            substats.put(TOTAL_BYTES, totalBytes);
        } else {
            substats.put(TOTAL_BYTES, substats.get(TOTAL_BYTES) + totalBytes);
        }
        
        if (substats.get(SIZE_ON_DISK) == null) {
            substats.put(SIZE_ON_DISK, sizeOnDisk);
        } else {
            substats.put(SIZE_ON_DISK, substats.get(SIZE_ON_DISK) + sizeOnDisk);
        }
    }

    protected URI generateRecordId(final Map<String, String> qualifiers)
    throws IOException {
        return ((WARCWriterPoolSettings)settings).getRecordIDGenerator().getQualifiedRecordID(qualifiers);
    }
    
    protected URI generateRecordId(final String key, final String value)
    throws IOException {
    	return ((WARCWriterPoolSettings)settings).getRecordIDGenerator().getQualifiedRecordID(key, value);
    }
    
    public URI writeWarcinfoRecord(String filename)
	throws IOException {
    	return writeWarcinfoRecord(filename, null);
    }
    
    public URI writeWarcinfoRecord(String filename, final String description)
        	throws IOException {
        // Strip .open suffix if present.
        if (filename.endsWith(WriterPoolMember.OCCUPIED_SUFFIX)) {
        	filename = filename.substring(0,
        		filename.length() - WriterPoolMember.OCCUPIED_SUFFIX.length());
        }
        ANVLRecord record = new ANVLRecord(2);
        record.addLabelValue(HEADER_KEY_FILENAME, filename);
        if (description != null && description.length() > 0) {
        	record.addLabelValue(CONTENT_DESCRIPTION, description);
        }
        // Add warcinfo body.
        byte [] warcinfoBody = null;
        if (settings.getMetadata() == null) {
        	// TODO: What to write into a warcinfo?  What to associate?
        	warcinfoBody = "TODO: Unimplemented".getBytes();
        } else {
        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
        	for (final Iterator<String> i = settings.getMetadata().iterator();
        			i.hasNext();) {
        		baos.write(i.next().toString().getBytes(UTF8Bytes.UTF8));
        	}
        	warcinfoBody = baos.toByteArray();
        }
        URI uri = writeWarcinfoRecord("application/warc-fields", record,
            new ByteArrayInputStream(warcinfoBody), warcinfoBody.length);
        // TODO: If at start of file, and we're writing compressed,
        // write out our distinctive GZIP extensions.
        return uri;
    }
    
    /**
     * Write a warcinfo to current file.
     * TODO: Write crawl metadata or pointers to crawl description.
     * @param mimetype Mimetype of the <code>fileMetadata</code> block.
     * @param namedFields Named fields. Pass <code>null</code> if none.
     * @param fileMetadata Metadata about this WARC as RDF, ANVL, etc.
     * @param fileMetadataLength Length of <code>fileMetadata</code>.
     * @throws IOException
     * @return Generated record-id made with
     * <a href="http://en.wikipedia.org/wiki/Data:_URL">data: scheme</a> and
     * the current filename.
     */
    public URI writeWarcinfoRecord(final String mimetype,
    	final ANVLRecord namedFields, final InputStream fileMetadata,
    	final long fileMetadataLength)
    throws IOException {
    	final URI recordid = generateRecordId(TYPE, WARCINFO);
    	writeWarcinfoRecord(ArchiveUtils.getLog14Date(), mimetype, recordid,
            namedFields, fileMetadata, fileMetadataLength);
    	return recordid;
    }
    
    /**
     * Write a <code>warcinfo</code> to current file.
     * The <code>warcinfo</code> type uses its <code>recordId</code> as its URL.
     * @param recordId URI to use for this warcinfo.
     * @param create14DigitDate Record creation date as 14 digit date.
     * @param mimetype Mimetype of the <code>fileMetadata</code>.
     * @param namedFields Named fields.
     * @param fileMetadata Metadata about this WARC as RDF, ANVL, etc.
     * @param fileMetadataLength Length of <code>fileMetadata</code>.
     * @throws IOException
     */
    public void writeWarcinfoRecord(final String create14DigitDate,
        final String mimetype, final URI recordId, final ANVLRecord namedFields,
    	final InputStream fileMetadata, final long fileMetadataLength)
    throws IOException {
    	writeRecord(WARCINFO, null, create14DigitDate, mimetype,
        	recordId, namedFields, fileMetadata, fileMetadataLength, true);
    }
    
    public void writeRequestRecord(final String url,
        final String create14DigitDate, final String mimetype,
        final URI recordId,
        final ANVLRecord namedFields, final InputStream request,
        final long requestLength)
    throws IOException {
        writeRecord(REQUEST, url, create14DigitDate,
            mimetype, recordId, namedFields, request,
            requestLength, true);
    }
    
    public void writeResourceRecord(final String url,
            final String create14DigitDate, final String mimetype,
            final ANVLRecord namedFields, final InputStream response,
            final long responseLength)
    throws IOException {
    	writeResourceRecord(url, create14DigitDate, mimetype, 
    	        ((WARCWriterPoolSettings)settings).getRecordIDGenerator().getRecordID(),
    			namedFields, response, responseLength);
    }
    
    public void writeResourceRecord(final String url,
            final String create14DigitDate, final String mimetype,
            final URI recordId,
            final ANVLRecord namedFields, final InputStream response,
            final long responseLength)
    throws IOException {
        writeRecord(RESOURCE, url, create14DigitDate,
            mimetype, recordId, namedFields, response,
            responseLength, true);
    }

    public void writeResponseRecord(final String url,
            final String create14DigitDate, final String mimetype,
            final URI recordId,
            final ANVLRecord namedFields, final InputStream response,
            final long responseLength)
    throws IOException {
        writeRecord(RESPONSE, url, create14DigitDate,
            mimetype, recordId, namedFields, response,
            responseLength, true);
    }
    
    public void writeRevisitRecord(final String url,
            final String create14DigitDate, final String mimetype,
            final URI recordId,
            final ANVLRecord namedFields, final InputStream response,
            final long responseLength)
    throws IOException {
        writeRecord(REVISIT, url, create14DigitDate,
            mimetype, recordId, namedFields, response,
            responseLength, false);
    }
    
    public void writeMetadataRecord(final String url,
            final String create14DigitDate, final String mimetype,
            final URI recordId,
            final ANVLRecord namedFields, final InputStream metadata,
            final long metadataLength)
    throws IOException {
        writeRecord(METADATA, url, create14DigitDate,
            mimetype, recordId, namedFields, metadata,
            metadataLength, true);
    }

    /**
     * @see WARCWriter#tmpStats for usage model
     */
    public void resetTmpStats() {
        if (tmpStats != null) {
            for (Map<String, Long> substats : tmpStats.values()) {
                for (Entry<String, Long> entry : substats.entrySet()) {
                    entry.setValue(0l);
                }
            }
        }
    }

    public Map<String, Map<String, Long>> getTmpStats() {
        return tmpStats;
    }

    public static long getStat(Map<String, Map<String, Long>> map, String key,
            String subkey) {
        if (map != null && map.get(key) != null
                && map.get(key).get(subkey) != null) {
            return map.get(key).get(subkey);
        } else {
            return 0l;
        }
    }

    public static long getStat(
            ConcurrentMap<String, ConcurrentMap<String, AtomicLong>> map,
            String key, String subkey) {
        if (map != null && map.get(key) != null
                && map.get(key).get(subkey) != null) {
            return map.get(key).get(subkey).get();
        } else {
            return 0l;
        }
    }
}
