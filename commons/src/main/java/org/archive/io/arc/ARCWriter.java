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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.archive.io.ReplayInputStream;
import org.archive.io.WriterPoolMember;
import org.archive.io.WriterPoolSettings;
import org.archive.util.ArchiveUtils;
import org.archive.util.DevUtils;
import org.archive.util.MimetypeUtils;


/**
 * Write ARC files.
 *
 * Assumption is that the caller is managing access to this ARCWriter ensuring
 * only one thread of control accessing this ARC file instance at any one time.
 *
 * <p>ARC files are described here:
 * <a href="http://www.archive.org/web/researcher/ArcFileFormat.php">Arc
 * File Format</a>.  This class does version 1 of the ARC file format.  It also
 * writes version 1.1 which is version 1 with data stuffed into the body of the
 * first arc record in the file, the arc file meta record itself.
 *
 * <p>An ARC file is three lines of meta data followed by an optional 'body' and
 * then a couple of '\n' and then: record, '\n', record, '\n', record, etc.
 * If we are writing compressed ARC files, then each of the ARC file records is
 * individually gzipped and concatenated together to make up a single ARC file.
 * In GZIP terms, each ARC record is a GZIP <i>member</i> of a total gzip'd
 * file.
 *
 * <p>The GZIPping of the ARC file meta data is exceptional.  It is GZIPped
 * w/ an extra GZIP header, a special Internet Archive (IA) extra header field
 * (e.g. FEXTRA is set in the GZIP header FLG field and an extra field is
 * appended to the GZIP header).  The extra field has little in it but its
 * presence denotes this GZIP as an Internet Archive gzipped ARC.  See RFC1952
 * to learn about the GZIP header structure.
 *
 * <p>This class then does its GZIPping in the following fashion.  Each GZIP
 * member is written w/ a new instance of GZIPOutputStream -- actually
 * ARCWriterGZIPOututStream so we can get access to the underlying stream.
 * The underlying stream stays open across GZIPoutputStream instantiations.
 * For the 'special' GZIPing of the ARC file meta data, we cheat by catching the
 * GZIPOutputStream output into a byte array, manipulating it adding the
 * IA GZIP header, before writing to the stream.
 *
 * <p>I tried writing a resettable GZIPOutputStream and could make it work w/
 * the SUN JDK but the IBM JDK threw NPE inside in the deflate.reset -- its zlib
 * native call doesn't seem to like the notion of resetting -- so I gave up on
 * it.
 *
 * <p>Because of such as the above and troubles with GZIPInputStream, we should
 * write our own GZIP*Streams, ones that resettable and consious of gzip
 * members.
 *
 * <p>This class will write until we hit >= maxSize.  The check is done at
 * record boundary.  Records do not span ARC files.  We will then close current
 * file and open another and then continue writing.
 *
 * <p><b>TESTING: </b>Here is how to test that produced ARC files are good
 * using the
 * <a href="http://www.archive.org/web/researcher/tool_documentation.php">alexa
 * ARC c-tools</a>:
 * <pre>
 * % av_procarc hx20040109230030-0.arc.gz | av_ziparc > \
 *     /tmp/hx20040109230030-0.dat.gz
 * % av_ripdat /tmp/hx20040109230030-0.dat.gz > /tmp/hx20040109230030-0.cdx
 * </pre>
 * Examine the produced cdx file to make sure it makes sense.  Search
 * for 'no-type 0'.  If found, then we're opening a gzip record w/o data to
 * write.  This is bad.
 *
 * <p>You can also do <code>gzip -t FILENAME</code> and it will tell you if the
 * ARC makes sense to GZIP.
 * 
 * <p>While being written, ARCs have a '.open' suffix appended.
 *
 * @author stack
 */
public class ARCWriter extends WriterPoolMember implements ARCConstants, Closeable {
    private static final Logger logger =
        Logger.getLogger(ARCWriter.class.getName());
    
    /**
     * Metadata line pattern.
     */
    private static final Pattern METADATA_LINE_PATTERN =
        Pattern.compile("^\\S+ \\S+ \\S+ \\S+ \\S+(" + LINE_SEPARATOR + "?)$");
    
      
    /**
     * Constructor.
     * Takes a stream. Use with caution. There is no upperbound check on size.
     * Will just keep writing.
     * 
     * @param serialNo  used to generate unique file name sequences
     * @param out Where to write.
     * @param arc File the <code>out</code> is connected to.
     * @param cmprs Compress the content written.
     * @param metadata File meta data.  Can be null.  Is list of File and/or
     * String objects.
     * @param a14DigitDate If null, we'll write current time.
     * @throws IOException
     */
    public ARCWriter(final AtomicInteger serialNo, final PrintStream out,
    	final File arc, final WriterPoolSettings settings)
    throws IOException {
        super(serialNo, out, arc, settings);
        writeFirstRecord(ArchiveUtils.get14DigitDate());
    }
          
    /**
     * Constructor.
     *
     * @param serialNo  used to generate unique file name sequences
     * @param settings all creation parameters
     */
    public ARCWriter(final AtomicInteger serialNo, final WriterPoolSettings settings) {
        super(serialNo, settings, ARC_FILE_EXTENSION);

    }

    protected String createFile()
    throws IOException {
        String name = super.createFile();
        writeFirstRecord(currentTimestamp);
        return name;
    }
    
    private void writeFirstRecord(final String ts)
    throws IOException {
        write(generateARCFileMetaData(ts));
    }
        
	/**
     * Write out the ARCMetaData.
     *
     * <p>Generate ARC file meta data.  Currently we only do version 1 of the
     * ARC file formats or version 1.1 when metadata has been supplied (We
     * write it into the body of the first record in the arc file).
     *
     * <p>Version 1 metadata looks roughly like this:
     *
     * <pre>filedesc://testWriteRecord-JunitIAH20040110013326-2.arc 0.0.0.0 \\
     *  20040110013326 text/plain 77
     * 1 0 InternetArchive
     * URL IP-address Archive-date Content-type Archive-length
     * </pre>
     *
     * <p>If compress is set, then we generate a header that has been gzipped
     * in the Internet Archive manner.   Such a gzipping enables the FEXTRA
     * flag in the FLG field of the gzip header.  It then appends an extra
     * header field: '8', '0', 'L', 'X', '0', '0', '0', '0'.  The first two
     * bytes are the length of the field and the last 6 bytes the Internet
     * Archive header.  To learn about GZIP format, see RFC1952.  To learn
     * about the Internet Archive extra header field, read the source for
     * av_ziparc which can be found at
     * <code>alexa/vista/alexa-tools-1.2/src/av_ziparc.cc</code>.
     *
     * <p>We do things in this roundabout manner because the java
     * GZIPOutputStream does not give access to GZIP header fields.
     *
     * @param date Date to put into the ARC metadata; if 17-digit will be 
     * truncated to traditional 14-digits
     *
     * @return Byte array filled w/ the arc header.
	 * @throws IOException
     */
    private byte [] generateARCFileMetaData(String date)
    throws IOException {
        if(date!=null && date.length()>14) {
            date = date.substring(0,14);
        }
        int metadataBodyLength = getMetadataLength();
        // If metadata body, then the minor part of the version is '1' rather
        // than '0'.
        String metadataHeaderLinesTwoAndThree =
            getMetadataHeaderLinesTwoAndThree("1 " +
                ((metadataBodyLength > 0)? "1": "0"));
        int recordLength = metadataBodyLength +
            metadataHeaderLinesTwoAndThree.getBytes(DEFAULT_ENCODING).length;
        String metadataHeaderStr = ARC_MAGIC_NUMBER + getBaseFilename() +
            " 0.0.0.0 " + date + " text/plain " + recordLength +
            metadataHeaderLinesTwoAndThree;
        ByteArrayOutputStream metabaos =
            new ByteArrayOutputStream(recordLength);
        // Write the metadata header.
        metabaos.write(metadataHeaderStr.getBytes(DEFAULT_ENCODING));
        // Write the metadata body, if anything to write.
        if (metadataBodyLength > 0) {
            writeMetaData(metabaos);
        }
        
        // Write out a LINE_SEPARATORs to end this record.
        metabaos.write(LINE_SEPARATOR);
        
        // Now get bytes of all just written and compress if flag set.
        byte [] bytes = metabaos.toByteArray();
        
        if(isCompressed()) {
            // GZIP the header but catch the gzipping into a byte array so we
            // can add the special IA GZIP header to the product.  After
            // manipulations, write to the output stream (The JAVA GZIP
            // implementation does not give access to GZIP header. It
            // produces a 'default' header only).  We can get away w/ these
            // maniupulations because the GZIP 'default' header doesn't
            // do the 'optional' CRC'ing of the header.
            byte [] gzippedMetaData = ArchiveUtils.gzip(bytes);
            if (gzippedMetaData[3] != 0) {
                throw new IOException("The GZIP FLG header is unexpectedly " +
                    " non-zero.  Need to add smarter code that can deal " +
                    " when already extant extra GZIP header fields.");
            }
            // Set the GZIP FLG header to '4' which says that the GZIP header
            // has extra fields.  Then insert the alex {'L', 'X', '0', '0', '0,
            // '0'} 'extra' field.  The IA GZIP header will also set byte
            // 9 (zero-based), the OS byte, to 3 (Unix).  We'll do the same.
            gzippedMetaData[3] = 4;
            gzippedMetaData[9] = 3;
            byte [] assemblyBuffer = new byte[gzippedMetaData.length +
                ARC_GZIP_EXTRA_FIELD.length];
            // '10' in the below is a pointer past the following bytes of the
            // GZIP header: ID1 ID2 CM FLG + MTIME(4-bytes) XFL OS.  See
            // RFC1952 for explaination of the abbreviations just used.
            System.arraycopy(gzippedMetaData, 0, assemblyBuffer, 0, 10);
            System.arraycopy(ARC_GZIP_EXTRA_FIELD, 0, assemblyBuffer, 10,
                ARC_GZIP_EXTRA_FIELD.length);
            System.arraycopy(gzippedMetaData, 10, assemblyBuffer,
                10 + ARC_GZIP_EXTRA_FIELD.length, gzippedMetaData.length - 10);
            bytes = assemblyBuffer;
        }
        return bytes;
    }
    
    public String getMetadataHeaderLinesTwoAndThree(String version) {
        StringBuffer buffer = new StringBuffer();
        buffer.append(LINE_SEPARATOR);
        buffer.append(version);
        buffer.append(" InternetArchive");
        buffer.append(LINE_SEPARATOR);
        buffer.append("URL IP-address Archive-date Content-type Archive-length");
        buffer.append(LINE_SEPARATOR);
        return buffer.toString();
    }

    /**
     * Write all metadata to passed <code>baos</code>.
     *
     * @param baos Byte array to write to.
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    private void writeMetaData(ByteArrayOutputStream baos)
            throws UnsupportedEncodingException, IOException {
        if (settings.getMetadata() == null) {
            return;
        }

        for (Iterator<String> i = settings.getMetadata().iterator();
                i.hasNext();) {
            Object obj = i.next();
            if (obj instanceof String) {
                baos.write(((String)obj).getBytes(DEFAULT_ENCODING));
            } else if (obj instanceof File) {
                InputStream is = null;
                try {
                    is = new BufferedInputStream(
                        new FileInputStream((File)obj));
                    byte [] buffer = new byte[4096];
                    for (int read = -1; (read = is.read(buffer)) != -1;) {
                        baos.write(buffer, 0, read);
                    }
                } finally {
                    if (is != null) {
                        is.close();
                    }
                }
            } else if (obj != null) {
                logger.severe("Unsupported metadata type: " + obj);
            }
        }
        return;
    }

    /**
     * @return Total length of metadata.
     * @throws UnsupportedEncodingException
     */
    private int getMetadataLength()
    throws UnsupportedEncodingException {
        int result = -1;
        if (settings.getMetadata()  == null) {
            result = 0;
        } else {
            for (Iterator<String> i = settings.getMetadata().iterator();
                    i.hasNext();) {
                Object obj = i.next();
                if (obj instanceof String) {
                    result += ((String)obj).getBytes(DEFAULT_ENCODING).length;
                } else if (obj instanceof File) {
                    result += ((File)obj).length();
                } else {
                    logger.severe("Unsupported metadata type: " + obj);
                }
            }
        }
        return result;
    }

    /**
     * @deprecated use input-stream version directly instead
     */
    public void write(String uri, String contentType, String hostIP,
            long fetchBeginTimeStamp, long recordLength,
            ByteArrayOutputStream baos)
    throws IOException {
        write(uri, contentType, hostIP, fetchBeginTimeStamp, recordLength, 
                new ByteArrayInputStream(baos.toByteArray()), false);
    }
    
    public void write(String uri, String contentType, String hostIP,
            long fetchBeginTimeStamp, long recordLength, InputStream in)
    throws IOException {
        write(uri,contentType,hostIP,fetchBeginTimeStamp,recordLength,in,true);
    }
    
    /**
     * Write a record with the given metadata/content.
     * 
     * @param uri
     *            URI for metadata-line
     * @param contentType
     *            MIME content-type for metadata-line
     * @param hostIP
     *            IP for metadata-line
     * @param fetchBeginTimeStamp
     *            timestamp for metadata-line
     * @param recordLength
     *            length for metadata-line; also may be enforced
     * @param in
     *            source InputStream for record content
     * @param enforceLength
     *            whether to enforce the declared length; should be true
     *            unless intentionally writing bad records for testing
     * @throws IOException
     */
    public void write(String uri, String contentType, String hostIP,
            long fetchBeginTimeStamp, long recordLength, InputStream in,
            boolean enforceLength) throws IOException {
        preWriteRecordTasks();
        try {
            write(getMetaLine(uri, contentType, hostIP, fetchBeginTimeStamp,
                    recordLength).getBytes(UTF8));
            copyFrom(in, recordLength, enforceLength);
            if (in instanceof ReplayInputStream) {
                // check for consumption of entire recorded material
                long remaining = ((ReplayInputStream) in).remaining();
                // Should be zero at this stage. If not, something is
                // wrong.
                if (remaining != 0) {
                    String message = "Gap between expected and actual: "
                            + remaining + LINE_SEPARATOR + DevUtils.extraInfo()
                            + " writing arc "
                            + this.getFile().getAbsolutePath();
                    DevUtils.warnHandle(new Throwable(message), message);
                    throw new IOException(message);
                }
            }
            write(LINE_SEPARATOR);
        } finally {
            postWriteRecordTasks();
        }
    }
    
    /**
     * @param uri
     * @param contentType
     * @param hostIP
     * @param fetchBeginTimeStamp
     * @param recordLength
     * @return Metadata line for an ARCRecord made of passed components.
     * @exception IOException
     */
    protected String getMetaLine(String uri, String contentType, String hostIP,
        long fetchBeginTimeStamp, long recordLength)
    throws IOException {
        if (fetchBeginTimeStamp <= 0) {
            throw new IOException("Bogus fetchBeginTimestamp: " +
                Long.toString(fetchBeginTimeStamp));
        }

        return validateMetaLine(createMetaline(uri, hostIP, 
            ArchiveUtils.get14DigitDate(fetchBeginTimeStamp),
            MimetypeUtils.truncate(contentType),
            Long.toString(recordLength)));
    }
    
    public String createMetaline(String uri, String hostIP,
            String timeStamp, String mimetype, String recordLength) {
        return uri + HEADER_FIELD_SEPARATOR + hostIP +
            HEADER_FIELD_SEPARATOR + timeStamp +
            HEADER_FIELD_SEPARATOR + mimetype +
            HEADER_FIELD_SEPARATOR + recordLength + LINE_SEPARATOR;
    }
    
    /**
     * Test that the metadata line is valid before writing.
     * @param metaLineStr
     * @throws IOException
     * @return The passed in metaline.
     */
    protected String validateMetaLine(String metaLineStr)
    throws IOException {
        if (metaLineStr.length() > MAX_METADATA_LINE_LENGTH) {
            throw new IOException("Metadata line too long ("
                + metaLineStr.length() + ">" + MAX_METADATA_LINE_LENGTH 
                + "): " + metaLineStr);
        }
     	Matcher m = METADATA_LINE_PATTERN.matcher(metaLineStr);
        if (!m.matches()) {
            throw new IOException("Metadata line doesn't match expected" +
                " pattern: " + metaLineStr);
        }
        return metaLineStr;
    }
}
