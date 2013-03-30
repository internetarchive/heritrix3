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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.UTF8Bytes;
import org.archive.io.WriterPoolMember;
import org.archive.uid.RecordIDGenerator;
import org.archive.uid.UUIDGenerator;
import org.archive.util.ArchiveUtils;
import org.archive.util.TmpDirTestCase;
import org.archive.util.anvl.ANVLRecord;

/**
 * Test Writer and Reader.
 * @author stack
 * @version $Date: 2006-08-29 19:35:48 -0700 (Tue, 29 Aug 2006) $ $Version$
 */
public class WARCWriterTest
extends TmpDirTestCase implements WARCConstants {
    
    private static final AtomicInteger SERIAL_NO = new AtomicInteger();
    
    RecordIDGenerator generator = new UUIDGenerator();
    
    /**
     * Prefix to use for ARC files made by JUNIT.
     */
    private static final String SUFFIX = "JUNIT";
    
    private static final String SOME_URL = "http://www.archive.org/test/";
    
    @SuppressWarnings("unchecked")
    public void testCheckHeaderLineValue() throws Exception {
        WARCWriter writer = new WARCWriter(
                SERIAL_NO, 
                new WARCWriterPoolSettingsData(
                    "","test",1,false,Collections.EMPTY_LIST,Collections.EMPTY_LIST,generator));
        writer.checkHeaderValue("one");
        IllegalArgumentException exception = null;
        try {
            writer.checkHeaderValue("with space");
        } catch(IllegalArgumentException e) {
            exception = e;
        }
       assertNotNull(exception);
       exception = null;
       try {
           writer.checkHeaderValue("with\0x0000controlcharacter");
       } catch(IllegalArgumentException e) {
           exception = e;
       }
       writer.close(); 
      assertNotNull(exception);
    }

    @SuppressWarnings("unchecked")
    public void testMimetypes() throws IOException {
        WARCWriter writer = new WARCWriter(SERIAL_NO, 
            new WARCWriterPoolSettingsData(
                    "m","testM",1,false,Collections.EMPTY_LIST,Collections.EMPTY_LIST,generator));
        writer.checkHeaderLineMimetypeParameter("text/xml");
        writer.checkHeaderLineMimetypeParameter("text/xml+rdf");
        assertEquals(writer.checkHeaderLineMimetypeParameter(
        	"text/plain; charset=SHIFT-JIS"), "text/plain; charset=SHIFT-JIS");
        assertEquals(writer.checkHeaderLineMimetypeParameter(
    		"multipart/mixed; \r\n        boundary=\"simple boundary\""),
            "multipart/mixed; boundary=\"simple boundary\"");
    }
    
    public void testWriteRecord() throws IOException {
    	File [] files = {getTmpDir()};

    	// Write uncompressed.
        WARCWriter writer =
        	new WARCWriter(SERIAL_NO, new WARCWriterPoolSettingsData(
                    this.getClass().getName(), "templateWR1", -1, false, Arrays.asList(files), null, generator));

        writeFile(writer);
        writer.close();
        
        // Write compressed.
        writer = new WARCWriter(SERIAL_NO, new WARCWriterPoolSettingsData(
                this.getClass().getName(), "templateWR2", -1, true, Arrays.asList(files), null, generator));

        writeFile(writer);
        writer.close(); 
    }
    
    private void writeFile(final WARCWriter writer)
    throws IOException {
        try {
            writeWarcinfoRecord(writer);
            writeBasicRecords(writer);
        } finally {
            writer.close();
            writer.getFile().delete();
        }
    }
    
    private void writeWarcinfoRecord(WARCWriter writer)
    throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.warcinfo);
        recordInfo.setUrl(null);
        recordInfo.setCreate14DigitDate(ArchiveUtils.getLog14Date());
        recordInfo.setMimetype(ANVLRecord.MIMETYPE);
        recordInfo.setExtraHeaders(null);
        recordInfo.setEnforceLength(true);
        
    	ANVLRecord meta = new ANVLRecord();
    	meta.addLabelValue("size", "1G");
    	meta.addLabelValue("operator", "igor");
    	byte [] bytes = meta.getUTF8Bytes();
    	recordInfo.setContentStream(new ByteArrayInputStream(bytes));
    	recordInfo.setContentLength((long) bytes.length);
    	
        final URI recordid = writer.generateRecordId(WARCWriter.TYPE, WARCRecordType.warcinfo.toString());
        recordInfo.setRecordId(recordid);
        
        writer.writeRecord(recordInfo);
	}

	protected void writeBasicRecords(final WARCWriter writer)
    throws IOException {
	    WARCRecordInfo recordInfo = new WARCRecordInfo();
	    recordInfo.setType(WARCRecordType.metadata);
	    recordInfo.setUrl("http://www.archive.org/");
	    recordInfo.setCreate14DigitDate(ArchiveUtils.get14DigitDate());
	    recordInfo.setMimetype("no/type");
	    recordInfo.setEnforceLength(true);
	    
    	ANVLRecord headerFields = new ANVLRecord();
    	headerFields.addLabelValue("x", "y");
    	headerFields.addLabelValue("a", "b");
    	recordInfo.setExtraHeaders(headerFields);
    	
    	URI rid = (new UUIDGenerator()).getQualifiedRecordID(TYPE, WARCRecordType.metadata.toString());
    	recordInfo.setRecordId(rid);
    	
    	final String content = "Any old content.";
    	for (int i = 0; i < 10; i++) {
    		String body = i + ". " + content;
    		byte [] bodyBytes = body.getBytes(UTF8Bytes.UTF8);
    		recordInfo.setContentStream(new ByteArrayInputStream(bodyBytes));
    		recordInfo.setContentLength((long)bodyBytes.length);
            writer.writeRecord(recordInfo);
    	}
    }

    /**
     * @return Generic HTML Content.
     */
    protected static String getContent() {
        return getContent(null);
    }
    
    /**
     * @return Generic HTML Content with mention of passed <code>indexStr</code>
     * in title and body.
     */
    protected static String getContent(String indexStr) {
        String page = (indexStr != null)? "Page #" + indexStr: "Some Page";
        return "HTTP/1.1 200 OK\r\n" +
        "Content-Type: text/html\r\n\r\n" +
        "<html><head><title>" + page +
        "</title></head>" +
        "<body>" + page +
        "</body></html>";
    }

    /**
     * Write random HTML Record.
     * @param w Where to write.
     * @param index An index to put into content.
     * @return Length of record written.
     * @throws IOException
     */
    protected int writeRandomHTTPRecord(WARCWriter w, int index)
    throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.resource);
        recordInfo.setCreate14DigitDate(ArchiveUtils.get14DigitDate());
        recordInfo.setMimetype("text/html; charset=UTF-8");
        recordInfo.setRecordId(w.generateRecordId(null));
        recordInfo.setEnforceLength(true);
        
        String indexStr = Integer.toString(index);
        recordInfo.setUrl("http://www.one.net/id=" + indexStr);
        
        byte[] record = (getContent(indexStr)).getBytes();
        recordInfo.setContentLength((long) record.length);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(record);
        recordInfo.setContentStream(new ByteArrayInputStream(baos.toByteArray()));
        
        // Add named fields for ip, checksum, and relate the metadata
        // and request to the resource field.
        recordInfo.addExtraHeader(NAMED_FIELD_IP_LABEL, "127.0.0.1");
        
        w.writeRecord(recordInfo);
        return record.length;
    }

    /**
     * Fill a WARC with HTML Records.
     * @param baseName WARC basename.
     * @param compress Whether to compress or not.
     * @param maxSize Maximum WARC size.
     * @param recordCount How many records.
     * @return The written file.
     * @throws IOException
     */
    private File writeRecords(String baseName, boolean compress,
        int maxSize, int recordCount)
    throws IOException {
        cleanUpOldFiles(baseName);
        File [] files = {getTmpDir()};
        WARCWriter w = new WARCWriter(SERIAL_NO, new WARCWriterPoolSettingsData(
                baseName + '-' + SUFFIX, "${prefix}", maxSize, compress, Arrays.asList(files), null, generator));
            
        assertNotNull(w);
        for (int i = 0; i < recordCount; i++) {
            writeRandomHTTPRecord(w, i);
        }
        w.close();
        assertTrue("Doesn't exist: " +  w.getFile().getAbsolutePath(), 
            w.getFile().exists());
        return w.getFile();
    }

    /**
     * Run validation of passed file.
     * @param f File to validate.
     * @param recordCount Expected count of records.
     * @throws FileNotFoundException
     * @throws IOException
     */
    private void validate(File f, int recordCount)
    throws FileNotFoundException, IOException {
        WARCReader reader = WARCReaderFactory.get(f);
        assertNotNull(reader);
        List<ArchiveRecordHeader> headers = null;
        if (recordCount == -1) {
            headers = reader.validate();
        } else {
            headers = reader.validate(recordCount);
        }
        reader.close();
        
        // Now, run through each of the records doing absolute get going from
        // the end to start.  Reopen the arc so no context between this test
        // and the previous.
        
        for (int i = headers.size() - 1; i >= 0; i--) {
            reader = WARCReaderFactory.get(f);
            ArchiveRecordHeader h = (ArchiveRecordHeader)headers.get(i);
            ArchiveRecord r = reader.get(h.getOffset());
            String mimeType = r.getHeader().getMimetype();
            assertTrue("Record is bogus",
                mimeType != null && mimeType.length() > 0);
            reader.close();
        }
        
        assertTrue("Metadatas not equal", headers.size() == recordCount);
        for (Iterator<ArchiveRecordHeader> i = headers.iterator(); i.hasNext();) {
            ArchiveRecordHeader r = (ArchiveRecordHeader)i.next();
            assertTrue("Record is empty", r.getLength() > 0);
        }
    }

    public void testWriteRecords() throws IOException {
        final int recordCount = 2;
        File f = writeRecords("writeRecords", false, DEFAULT_MAX_WARC_FILE_SIZE,
            recordCount);
     	validate(f, recordCount  + 1); // Header record.
    }

    public void testRandomAccess() throws IOException {
        final int recordCount = 3;
        File f = writeRecords("randomAccess", true, DEFAULT_MAX_WARC_FILE_SIZE,
            recordCount);
        WARCReader reader = WARCReaderFactory.get(f);
        // Get to second record.  Get its offset for later use.
        boolean readFirst = false;
        String url = null;
        long offset = -1;
        long totalRecords = 0;
        boolean readSecond = false;
        for (final Iterator<ArchiveRecord> i = reader.iterator(); i.hasNext();
                totalRecords++) {
            WARCRecord ar = (WARCRecord)i.next();
            if (!readFirst) {
                readFirst = true;
                continue;
            }
            if (!readSecond) {
                url = ar.getHeader().getUrl();
                offset = ar.getHeader().getOffset();
                readSecond = true;
            }
        }
        reader.close(); 
        
        reader = WARCReaderFactory.get(f, offset);
        ArchiveRecord ar = reader.get();
        assertEquals(ar.getHeader().getUrl(), url);
        ar.close();
        reader.close(); 
        
        // Get reader again.  See how iterator works with offset
        reader = WARCReaderFactory.get(f, offset);
        int count = 0;
        for (final Iterator<ArchiveRecord> i = reader.iterator(); i.hasNext(); i.next()) {
            count++;
        }
        reader.close();
        assertEquals(totalRecords - 1, count);
    }
    
    public void testWriteRecordCompressed() throws IOException {
        final int recordCount = 2;
        File arcFile = writeRecords("writeRecordCompressed", true,
            DEFAULT_MAX_WARC_FILE_SIZE, recordCount);
        validate(arcFile, recordCount + 1 /*Header record*/);
    }
    
    protected WARCWriter createWARCWriter(String name,
            boolean compress) {
        File [] files = {getTmpDir()};
        return new WARCWriter(SERIAL_NO, 
                              new WARCWriterPoolSettingsData(
                                      name, 
                                      "${prefix}-"+SUFFIX, 
                                      DEFAULT_MAX_WARC_FILE_SIZE, 
                                      compress, 
                                      Arrays.asList(files), 
                                      null, 
                                      generator));
    }
    
    protected static ByteArrayOutputStream getBaos(String str)
    throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(str.getBytes());
        return baos;
    }
    
    protected static void writeRecord(WARCWriter w, String url,
        String mimetype, int len, ByteArrayOutputStream baos)
    throws IOException {
        WARCRecordInfo recordInfo = new WARCRecordInfo();
        recordInfo.setType(WARCRecordType.resource);
        recordInfo.setUrl(url);
        recordInfo.setCreate14DigitDate(ArchiveUtils.get14DigitDate());
        recordInfo.setMimetype(mimetype);
        recordInfo.setRecordId(w.generateRecordId(null));
        recordInfo.setExtraHeaders(null);
        recordInfo.setContentStream(new ByteArrayInputStream(baos.toByteArray()));
        recordInfo.setContentLength((long) len);
        recordInfo.setEnforceLength(true);
        
        w.writeRecord(recordInfo);
    }
    
    protected int iterateRecords(WARCReader r)
    throws IOException {
        int count = 0;
        for (Iterator<ArchiveRecord> i = r.iterator(); i.hasNext();) {
            ArchiveRecord ar = i.next();
            ar.close();
            if (count != 0) {
                assertTrue("Unexpected URL " + ar.getHeader().getUrl(),
                    ar.getHeader().getUrl().equals(SOME_URL));
            }
            count++;
        }
        return count;
    }
    
    protected WARCWriter createWithOneRecord(String name,
        boolean compressed)
    throws IOException {
        WARCWriter writer = createWARCWriter(name, compressed);
        String content = getContent();
        writeRecord(writer, SOME_URL, "text/html",
            content.length(), getBaos(content));
        return writer;
    }
    
    public void testSpaceInURL() throws IOException {
        long bytesWritten = holeyUrl("testSpaceInURL", false, " ");
        assertEquals("Unexpected successful writing occurred",0,bytesWritten);
    }

    public void testTabInURL() throws IOException {
        long bytesWritten = holeyUrl("testTabInURL", false, "\t");
        assertEquals("Unexpected successful writing occurred",0,bytesWritten);
    }
    
    protected long holeyUrl(String name, boolean compress, String urlInsert)
    throws IOException {
        WARCWriter writer = createWithOneRecord(name, compress);
        // Add some bytes on the end to mess up the record.
        long startPos = writer.getPosition();
        String content = getContent();
        ByteArrayOutputStream baos = getBaos(content);
        writeRecord(writer, SOME_URL + urlInsert + "/index.html", "text/html",
            content.length(), baos);
        long endPos = writer.getPosition();
        writer.close();
        return endPos-startPos;
    }
    
    /**
     * Write an arc file for other tests to use.
     * @param arcdir Directory to write to.
     * @param compress True if file should be compressed.
     * @return ARC written.
     * @throws IOException 
     */
    public static File createWARCFile(File arcdir, boolean compress)
    throws IOException {
        File [] files = {arcdir};
        WARCWriter writer =
            new WARCWriter(SERIAL_NO, 
                    new WARCWriterPoolSettingsData(
                            "", 
                            "test", 
                            DEFAULT_MAX_WARC_FILE_SIZE, 
                            compress, 
                            Arrays.asList(files), 
                            null, 
                            new UUIDGenerator()));
        String content = getContent();
        writeRecord(writer, SOME_URL, "text/html", content.length(),
            getBaos(content));
        writer.close();
        return writer.getFile();
    }
    
//    public void testSpeed() throws IOException {
//        ARCWriter writer = createArcWithOneRecord("speed", true);
//        // Add a record with a length that is too long.
//        String content = getContent();
//        final int count = 100000;
//        logger.info("Starting speed write of " + count + " records.");
//        for (int i = 0; i < count; i++) {
//            writeRecord(writer, SOME_URL, "text/html", content.length(),
//                    getBaos(content));
//        }
//        writer.close();
//        logger.info("Finished speed write test.");
//    }
    
    public void testArcRecordOffsetReads() throws Exception {
    	// Get an ARC with one record.
		WriterPoolMember w =
			createWithOneRecord("testArcRecordInBufferStream", true);
		w.close();
		// Get reader on said ARC.
		WARCReader r = WARCReaderFactory.get(w.getFile());
		final Iterator<ArchiveRecord> i = r.iterator();
		// Skip first ARC meta record.
		ArchiveRecord ar = i.next();
		i.hasNext();
		// Now we're at first and only record in ARC.
		ar = (WARCRecord) i.next();
		// Now try getting some random set of bytes out of it 
		// at an odd offset (used to fail because we were
		// doing bad math to find where in buffer to read).
		final byte[] buffer = new byte[17];
		final int maxRead = 4;
		int totalRead = 0;
		while (totalRead < maxRead) {
			totalRead = totalRead
			    + ar.read(buffer, 13 + totalRead, maxRead - totalRead);
			assertTrue(totalRead > 0);
		}
	}
}