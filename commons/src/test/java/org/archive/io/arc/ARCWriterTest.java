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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.ReplayInputStream;
import org.archive.io.WriterPoolMember;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;
import org.archive.util.TmpDirTestCase;


/**
 * Test ARCWriter class.
 *
 * This code exercises ARCWriter AND ARCReader.  First it writes ARCs w/
 * ARCWriter.  Then it validates what was written w/ ARCReader.
 *
 * @author stack
 */
public class ARCWriterTest
extends TmpDirTestCase implements ARCConstants {
    /**
     * Utility class for writing bad ARCs (with trailing junk)
     */
    public class CorruptibleARCWriter extends ARCWriter {
        byte[] endJunk = null;

        public CorruptibleARCWriter(AtomicInteger serial_no, List<File> name,
                String name2, boolean compress, long default_max_arc_file_size) {
            super(serial_no, name, name2, compress, default_max_arc_file_size);
        }

        @Override
        protected void postWriteRecordTasks() throws IOException {
            if (endJunk != null) {
                this.write(endJunk);
            }
            super.postWriteRecordTasks();
        }

        public void setEndJunk(byte[] b) throws IOException {
            this.endJunk = b;
        }
    }

    /**
     * Prefix to use for ARC files made by JUNIT.
     */
    private static final String SUFFIX =
        /* TODO DEFAULT_ARC_FILE_PREFIX*/ "JUNIT";
    
    private static final String SOME_URL = "http://www.archive.org/test/";

    
    private static final AtomicInteger SERIAL_NO = new AtomicInteger();

    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    
    protected static String getContent() {
        return getContent(null);
    }
    
    protected static String getContent(String indexStr) {
        String page = (indexStr != null)? "Page #" + indexStr: "Some Page";
        return "HTTP/1.1 200 OK\r\n" +
        "Content-Type: text/html\r\n\r\n" +
        "<html><head><title>" + page +
        "</title></head>" +
        "<body>" + page +
        "</body></html>";
    }

    @SuppressWarnings("deprecation")
    protected int writeRandomHTTPRecord(ARCWriter arcWriter, int index)
    throws IOException {
        String indexStr = Integer.toString(index);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Start the record with an arbitrary 14-digit date per RFC2540
        String now = ArchiveUtils.get14DigitDate();
        int recordLength = 0;
        byte[] record = (getContent(indexStr)).getBytes();
        recordLength += record.length;
        baos.write(record);
        // Add the newline between records back in
        baos.write("\n".getBytes());
        recordLength += 1;
        arcWriter.write("http://www.one.net/id=" + indexStr, "text/html",
            "0.1.2.3", Long.parseLong(now), recordLength, baos);
        return recordLength;
    }

    private File writeRecords(String baseName, boolean compress,
        long maxSize, int recordCount)
    throws IOException {
        cleanUpOldFiles(baseName);
        File [] files = {getTmpDir()};
        ARCWriter arcWriter = new ARCWriter(SERIAL_NO, Arrays.asList(files),
            baseName + '-' + SUFFIX, compress, maxSize);
        assertNotNull(arcWriter);
        for (int i = 0; i < recordCount; i++) {
            writeRandomHTTPRecord(arcWriter, i);
        }
        arcWriter.close();
        assertTrue("Doesn't exist: " +
                arcWriter.getFile().getAbsolutePath(), 
            arcWriter.getFile().exists());
        return arcWriter.getFile();
    }

    private void validate(File arcFile, int recordCount)
    throws FileNotFoundException, IOException {
        ARCReader reader = ARCReaderFactory.get(arcFile);
        assertNotNull(reader);
        List<ArchiveRecordHeader> metaDatas = null;
        if (recordCount == -1) {
            metaDatas = reader.validate();
        } else {
            metaDatas = reader.validate(recordCount);
        }
        reader.close();
        // Now, run through each of the records doing absolute get going from
        // the end to start.  Reopen the arc so no context between this test
        // and the previous.
        reader = ARCReaderFactory.get(arcFile);
        for (int i = metaDatas.size() - 1; i >= 0; i--) {
            ARCRecordMetaData meta = (ARCRecordMetaData)metaDatas.get(i);
            ArchiveRecord r = reader.get(meta.getOffset());
            String mimeType = r.getHeader().getMimetype();
            assertTrue("Record is bogus",
                mimeType != null && mimeType.length() > 0);
        }
        reader.close();
        assertTrue("Metadatas not equal", metaDatas.size() == recordCount);
        for (Iterator<ArchiveRecordHeader> i = metaDatas.iterator(); i.hasNext();) {
                ARCRecordMetaData r = (ARCRecordMetaData)i.next();
                assertTrue("Record is empty", r.getLength() > 0);
        }
    }

    public void testCheckARCFileSize()
    throws IOException {
        runCheckARCFileSizeTest("checkARCFileSize", false);
    }

    public void testCheckARCFileSizeCompressed()
    throws IOException {
        runCheckARCFileSizeTest("checkARCFileSize", true);
    }

    public void testWriteRecord() throws IOException {
        final int recordCount = 2;
        File arcFile = writeRecords("writeRecord", false,
                DEFAULT_MAX_ARC_FILE_SIZE, recordCount);
        validate(arcFile, recordCount  + 1); // Header record.
    }
    
    public void testRandomAccess() throws IOException {
        final int recordCount = 3;
        File arcFile = writeRecords("writeRecord", true,
            DEFAULT_MAX_ARC_FILE_SIZE, recordCount);
        ARCReader reader = ARCReaderFactory.get(arcFile);
        // Get to second record.  Get its offset for later use.
        boolean readFirst = false;
        String url = null;
        long offset = -1;
        long totalRecords = 0;
        boolean readSecond = false;
        for (final Iterator<ArchiveRecord> i = reader.iterator(); i.hasNext(); totalRecords++) {
            ARCRecord ar = (ARCRecord)i.next();
            if (!readFirst) {
                readFirst = true;
                continue;
            }
            if (!readSecond) {
                url = ar.getMetaData().getUrl();
                offset = ar.getMetaData().getOffset();
                readSecond = true;
            }
        }
        
        reader = ARCReaderFactory.get(arcFile, offset);
        ArchiveRecord ar = reader.get();
        assertEquals(ar.getHeader().getUrl(), url);
        ar.close();
        
        // Get reader again.  See how iterator works with offset
        reader = ARCReaderFactory.get(arcFile, offset);
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
                DEFAULT_MAX_ARC_FILE_SIZE, recordCount);
        validate(arcFile, recordCount + 1 /*Header record*/);
    }
    
    public void testWriteGiantRecord() throws IOException {
        PrintStream dummyStream = new PrintStream(new NullOutputStream());
        ARCWriter arcWriter = new ARCWriter(SERIAL_NO, dummyStream,
                new File("dummy"),
                false, null, null);
        assertNotNull(arcWriter);

        // Start the record with an arbitrary 14-digit date per RFC2540
        long now = System.currentTimeMillis();
        long recordLength = org.apache.commons.io.FileUtils.ONE_GB * 3;
       
        arcWriter.write("dummy:uri", "application/octet-stream",
            "0.1.2.3", now, recordLength, new NullInputStream(recordLength));
        arcWriter.close();
        }
    
    private void runCheckARCFileSizeTest(String baseName, boolean compress)
    throws FileNotFoundException, IOException  {
        writeRecords(baseName, compress, 1024, 15);
        // Now validate all files just created.
        File [] files = FileUtils.getFilesWithPrefix(getTmpDir(), SUFFIX);
        for (int i = 0; i < files.length; i++) {
            validate(files[i], -1);
        }
    }
    
    protected CorruptibleARCWriter createARCWriter(String NAME, boolean compress) {
        File [] files = {getTmpDir()};
        return new CorruptibleARCWriter(SERIAL_NO, Arrays.asList(files), NAME,
            compress, DEFAULT_MAX_ARC_FILE_SIZE);
    }
    
    protected static ByteArrayInputStream getBais(String str)
    throws IOException {
        return new ByteArrayInputStream(str.getBytes());
    }
    
    /**
     * Writes a record, suppressing normal length-checks (so that 
     * intentionally malformed records may be written). 
     */
    protected static void writeRecord(ARCWriter writer, String url,
        String type, int len, ByteArrayInputStream bais)
    throws IOException {
        writer.write(url, type, "192.168.1.1", (new Date()).getTime(), len,
            bais, false);
    }
    
    protected int iterateRecords(ARCReader r)
    throws IOException {
        int count = 0;
        for (Iterator<ArchiveRecord> i = r.iterator(); i.hasNext();) {
            ARCRecord rec = (ARCRecord)i.next();
            rec.close();
            if (count != 0) {
                assertTrue("Unexpected URL " + rec.getMetaData().getUrl(),
                    rec.getMetaData().getUrl().equals(SOME_URL));
            }
            count++;
        }
        return count;
    }
    
    protected CorruptibleARCWriter createArcWithOneRecord(String name,
        boolean compressed)
    throws IOException {
    	CorruptibleARCWriter writer = createARCWriter(name, compressed);
        String content = getContent();
        writeRecord(writer, SOME_URL, "text/html",
            content.length(), getBais(content));
        return writer;
    }
    
    public void testSpaceInURL() {
        String eMessage = null;
        try {
            holeyUrl("testSpaceInURL-" + SUFFIX, false, " ");
        } catch (IOException e) {
            eMessage = e.getMessage();
        }
        assertTrue("Didn't get expected exception: " + eMessage,
            eMessage.startsWith("Metadata line doesn't match"));
    }

    public void testTabInURL() {        
        String eMessage = null;
        try {
            holeyUrl("testTabInURL-" + SUFFIX, false, "\t");
        } catch (IOException e) {
            eMessage = e.getMessage();
        }
        assertTrue("Didn't get expected exception: " + eMessage,
            eMessage.startsWith("Metadata line doesn't match"));
    }
    
    protected void holeyUrl(String name, boolean compress, String urlInsert)
    throws IOException {
    	ARCWriter writer = createArcWithOneRecord(name, compress);
        // Add some bytes on the end to mess up the record.
        String content = getContent();
        writeRecord(writer, SOME_URL + urlInsert + "/index.html", "text/html",
            content.length(), getBais(content));
        writer.close();
    }
    
// If uncompressed, length has to be right or parse will fail.
//
//    public void testLengthTooShort() throws IOException {
//        lengthTooShort("testLengthTooShort-" + PREFIX, false);
//    }
    
    public void testLengthTooShortCompressed() throws IOException {
        lengthTooShort("testLengthTooShortCompressed-" + SUFFIX, true, false);
    }
    
    public void testLengthTooShortCompressedStrict()
    throws IOException {      
        String eMessage = null;
        try {
            lengthTooShort("testLengthTooShortCompressedStrict-" + SUFFIX,
                true, true);
        } catch (RuntimeException e) {
            eMessage = e.getMessage();
        }
        assertTrue("Didn't get expected exception: " + eMessage,
            eMessage.startsWith("java.io.IOException: Record ENDING at"));
    }
     
    protected void lengthTooShort(String name, boolean compress, boolean strict)
    throws IOException {
    	CorruptibleARCWriter writer = createArcWithOneRecord(name, compress);
        // Add some bytes on the end to mess up the record.
        String content = getContent();
        ByteArrayInputStream bais = getBais(content+"SOME TRAILING BYTES");
        writeRecord(writer, SOME_URL, "text/html",
            content.length(), bais);
        writer.setEndJunk("SOME TRAILING BYTES".getBytes());
        writeRecord(writer, SOME_URL, "text/html",
            content.length(), getBais(content));
        writer.close();
        
        // Catch System.err into a byte stream.
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        System.setErr(new PrintStream(os));
        
        ARCReader r = ARCReaderFactory.get(writer.getFile());
        r.setStrict(strict);
        int count = iterateRecords(r);
        assertTrue("Count wrong " + count, count == 4);

        // Make sure we get the warning string which complains about the
        // trailing bytes.
        String err = os.toString();
        assertTrue("No message " + err, err.startsWith("WARNING") &&
            (err.indexOf("Record ENDING at") > 0));
    }
    
//  If uncompressed, length has to be right or parse will fail.
//
//    public void testLengthTooLong()
//    throws IOException {
//        lengthTooLong("testLengthTooLongCompressed-" + PREFIX,
//            false, false);
//    }
    
    public void testLengthTooLongCompressed()
    throws IOException {
        lengthTooLong("testLengthTooLongCompressed-" + SUFFIX,
            true, false);
    }
    
    public void testLengthTooLongCompressedStrict() {
        String eMessage = null;
        try {
            lengthTooLong("testLengthTooLongCompressed-" + SUFFIX,
                true, true);
        } catch (IOException e) {
            eMessage = e.getMessage();
        }
        assertTrue("Didn't get expected exception: " + eMessage,
            eMessage.startsWith("Premature EOF before end-of-record"));
    }
    
    protected void lengthTooLong(String name, boolean compress,
            boolean strict)
    throws IOException {
    	ARCWriter writer = createArcWithOneRecord(name, compress);
        // Add a record with a length that is too long.
        String content = getContent();
        writeRecord(writer, SOME_URL, "text/html",
            content.length() + 10, getBais(content));
        writeRecord(writer, SOME_URL, "text/html",
            content.length(), getBais(content));
        writer.close();
        
        // Catch System.err.
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        System.setErr(new PrintStream(os));
        
        ARCReader r = ARCReaderFactory.get(writer.getFile());
        r.setStrict(strict);
        int count = iterateRecords(r);
        assertTrue("Count wrong " + count, count == 4);
        
        // Make sure we get the warning string which complains about the
        // trailing bytes.
        String err = os.toString();
        assertTrue("No message " + err, 
            err.startsWith("WARNING Premature EOF before end-of-record"));
    }
    
    public void testGapError() throws IOException {
    	ARCWriter writer = createArcWithOneRecord("testGapError", true);
        String content = getContent();
        // Make a 'weird' RIS that returns bad 'remaining' length
        // awhen remaining should be 0
        ReplayInputStream ris = new ReplayInputStream(content.getBytes(),
                content.length(), null) {
            public long remaining() {
                return (super.remaining()==0) ? -1 : super.remaining();
            }
        };
        String message = null;
        try {
        writer.write(SOME_URL, "text/html", "192.168.1.1",
            (new Date()).getTime(), content.length(), ris);
        } catch (IOException e) {
            message = e.getMessage();
        } finally {
            IOUtils.closeQuietly(ris);
        }
        writer.close();
        assertTrue("No gap when should be",
            message != null &&
            message.indexOf("Gap between expected and actual") >= 0);
    }
    
    /**
     * Write an arc file for other tests to use.
     * @param arcdir Directory to write to.
     * @param compress True if file should be compressed.
     * @return ARC written.
     * @throws IOException 
     */
    public static File createARCFile(File arcdir, boolean compress)
    throws IOException {
        File [] files = {arcdir};
        ARCWriter writer = new ARCWriter(SERIAL_NO, Arrays.asList(files),
            "test", compress, DEFAULT_MAX_ARC_FILE_SIZE);
        String content = getContent();
        writeRecord(writer, SOME_URL, "text/html", content.length(),
            getBais(content));
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
    
    
    public void testValidateMetaLine() throws Exception {
        final String line = "http://www.aandw.net/images/walden2.png " +
            "128.197.34.86 20060111174224 image/png 2160";
        ARCWriter w = createARCWriter("testValidateMetaLine", true);
        try {
            w.validateMetaLine(line);
            w.validateMetaLine(line + LINE_SEPARATOR);
            w.validateMetaLine(line + "\\r\\n");
        } finally {
            w.close();
        }
    }
    
    public void testArcRecordOffsetReads() throws Exception {
    	// Get an ARC with one record.
		WriterPoolMember w =
			createArcWithOneRecord("testArcRecordInBufferStream", true);
		w.close();
		// Get reader on said ARC.
		ARCReader r = ARCReaderFactory.get(w.getFile());
		final Iterator<ArchiveRecord> i = r.iterator();
		// Skip first ARC meta record.
		ARCRecord ar = (ARCRecord) i.next();
		i.hasNext();
		// Now we're at first and only record in ARC.
		ar = (ARCRecord) i.next();
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
