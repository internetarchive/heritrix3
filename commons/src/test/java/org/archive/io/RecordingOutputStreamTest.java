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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.archive.util.TmpDirTestCase;


/**
 * Test casesfor RecordingOutputStream.
 *
 * @author stack
 */
public class RecordingOutputStreamTest extends TmpDirTestCase
{
    /**
     * Size of buffer used in tests.
     */
    private static final int BUFFER_SIZE = 5;

    /**
     * How much to write total to testing RecordingOutputStream.
     */
    private static final int WRITE_TOTAL = 10;


    /*
     * @see TmpDirTestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();
    }

    /**
     * Test reusing instance of RecordingOutputStream.
     *
     * @throws IOException Failed open of backing file or opening of
     * input streams verifying recording.
     */
    public void testReuse()
        throws IOException
    {
        final String BASENAME = "testReuse";
        cleanUpOldFiles(BASENAME);
        RecordingOutputStream ros = new RecordingOutputStream(BUFFER_SIZE,
            (new File(getTmpDir(), BASENAME + "Bkg.txt")).getAbsolutePath());
        for (int i = 0; i < 3; i++)
        {
            reuse(BASENAME, ros, i);
        }
    }

    private void reuse(String baseName, RecordingOutputStream ros, int index)
        throws IOException
    {
        final String BASENAME = baseName + Integer.toString(index);
        File f = writeIntRecordedFile(ros, BASENAME, WRITE_TOTAL);
        verifyRecording(ros, f, WRITE_TOTAL);
        // Do again to test that I can get a new ReplayInputStream on same
        // RecordingOutputStream.
        verifyRecording(ros, f, WRITE_TOTAL);
    }

    /**
     * Method to test for void write(int).
     *
     * Uses small buffer size and small write size.  Test mark and reset too.
     *
     * @throws IOException Failed open of backing file or opening of
     * input streams verifying recording.
     */
    public void testWriteint()
        throws IOException
    {
        final String BASENAME = "testWriteint";
        cleanUpOldFiles(BASENAME);
        RecordingOutputStream ros = new RecordingOutputStream(BUFFER_SIZE,
           (new File(getTmpDir(), BASENAME + "Backing.txt")).getAbsolutePath());
        File f = writeIntRecordedFile(ros, BASENAME, WRITE_TOTAL);
        verifyRecording(ros, f, WRITE_TOTAL);
        // Do again to test that I can get a new ReplayInputStream on same
        // RecordingOutputStream.
        verifyRecording(ros, f, WRITE_TOTAL);
    }

    /**
     * Method to test for void write(byte []).
     *
     * Uses small buffer size and small write size.
     *
     * @throws IOException Failed open of backing file or opening of
     * input streams verifying recording.
     */
    public void testWritebytearray()
        throws IOException
    {
        final String BASENAME = "testWritebytearray";
        cleanUpOldFiles(BASENAME);
        RecordingOutputStream ros = new RecordingOutputStream(BUFFER_SIZE,
           (new File(getTmpDir(), BASENAME + "Backing.txt")).getAbsolutePath());
        File f = writeByteRecordedFile(ros, BASENAME, WRITE_TOTAL);
        verifyRecording(ros, f, WRITE_TOTAL);
        // Do again to test that I can get a new ReplayInputStream on same
        // RecordingOutputStream.
        verifyRecording(ros, f, WRITE_TOTAL);
    }

    /**
     * Test mark and reset.
     * @throws IOException
     */
    public void testMarkReset() throws IOException
    {
        final String BASENAME = "testMarkReset";
        cleanUpOldFiles(BASENAME);
        RecordingOutputStream ros = new RecordingOutputStream(BUFFER_SIZE,
                (new File(getTmpDir(), BASENAME + "Backing.txt")).getAbsolutePath());
        File f = writeByteRecordedFile(ros, BASENAME, WRITE_TOTAL);
        verifyRecording(ros, f, WRITE_TOTAL);
        ReplayInputStream ris = ros.getReplayInputStream();
        ris.mark(10 /*Arbitrary value*/);
        // Read from the stream.
        ris.read();
        ris.read();
        ris.read();
        // Reset it.  It should be back at zero.
        ris.reset();
        assertEquals("Reset to zero", ris.read(), 0);
        assertEquals("Reset to zero char 1", ris.read(), 1);
        assertEquals("Reset to zero char 2", ris.read(), 2);
        // Mark stream.  Here.  Next character should be '3'.
        ris.mark(10 /* Arbitrary value*/);
        ris.read();
        ris.read();
        ris.reset();
        assertEquals("Reset to zero char 3", ris.read(), 3);
    }

    /**
     * Record a file write.
     *
     * Write a file w/ characters that start at null and ascend to
     * <code>filesize</code>.  Record the writing w/ passed <code>ros</code>
     * recordingoutputstream. Return the file recorded as result of method.
     * The file  output stream that is recorded is named
     * <code>basename</code> + ".txt".
     *
     * <p>This method writes a character at a time.
     *
     * @param ros RecordingOutputStream to record with.
     * @param basename Basename of file.
     * @param size How many characters to write.
     * @return Recorded output stream.
     */
    private File writeIntRecordedFile(RecordingOutputStream ros,
            String basename, int size)
        throws IOException
    {
        File f = new File(getTmpDir(), basename + ".txt");
        FileOutputStream fos = new FileOutputStream(f);
        ros.open(fos);
        for (int i = 0; i < WRITE_TOTAL; i++)
        {
            ros.write(i);
        }
        ros.close();
        fos.close();
        assertEquals("Content-Length test", size,
            ros.getResponseContentLength());
        return f;
    }

    /**
     * Record a file byte array write.
     *
     * Write a file w/ characters that start at null and ascend to
     * <code>filesize</code>.  Record the writing w/ passed <code>ros</code>
     * recordingoutputstream. Return the file recorded as result of method.
     * The file  output stream that is recorded is named
     * <code>basename</code> + ".txt".
     *
     * <p>This method writes using a byte array.
     *
     * @param ros RecordingOutputStream to record with.
     * @param basename Basename of file.
     * @param size How many characters to write.
     * @return Recorded output stream.
     */
    private File writeByteRecordedFile(RecordingOutputStream ros,
            String basename, int size)
    throws IOException
    {
        File f = new File(getTmpDir(), basename + ".txt");
        FileOutputStream fos = new FileOutputStream(f);
        ros.open(fos);
        byte [] b = new byte[size];
        for (int i = 0; i < size; i++)
        {
            b[i] = (byte)i;
        }
        ros.write(b);
        ros.close();
        fos.close();
        assertEquals("Content-Length test", size,
                ros.getResponseContentLength());
        return f;
    }

    /**
     * Verify what was written is both in the file written to and in the
     * recording stream.
     *
     * @param ros Stream to check.
     * @param f File that was recorded.  Stream should have its content
     * exactly.
     * @param size Amount of bytes written.
     *
     * @exception IOException Failure reading streams.
     */
    private void verifyRecording(RecordingOutputStream ros, File f,
         int size) throws IOException
    {
        assertEquals("Recorded file size.", size, f.length());
        FileInputStream fis = new FileInputStream(f);
        assertNotNull("FileInputStream not null", fis);
        ReplayInputStream ris = ros.getReplayInputStream();
        assertNotNull("ReplayInputStream not null", ris);
        for (int i = 0; i < size; i++)
        {
            assertEquals("ReplayInputStream content verification", i,
                    ris.read());
            assertEquals("Recorded file content verification", i,
                    fis.read());
        }
        assertEquals("ReplayInputStream at EOF", -1, ris.read());
        fis.close();
        ris.close();
    }
}
