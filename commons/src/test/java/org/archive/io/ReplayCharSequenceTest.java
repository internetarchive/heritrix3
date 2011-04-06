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
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Random;
import java.util.logging.Logger;

import org.archive.util.FileUtils;
import org.archive.util.TmpDirTestCase;

import com.google.common.base.Charsets;

/**
 * Test ReplayCharSequences.
 *
 * @author stack, gojomo
 * @version $Revision$, $Date$
 */
public class ReplayCharSequenceTest extends TmpDirTestCase
{
    /**
     * Logger.
     */
    private static Logger logger =
        Logger.getLogger("org.archive.io.ReplayCharSequenceFactoryTest");


    private static final int SEQUENCE_LENGTH = 127;
    private static final int MULTIPLIER = 3;
    private static final int BUFFER_SIZE = SEQUENCE_LENGTH * MULTIPLIER;
    private static final int INCREMENT = 1;

    /**
     * Buffer of regular content.
     */
    private byte [] regularBuffer = null;

    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();
        this.regularBuffer =
            fillBufferWithRegularContent(new byte [BUFFER_SIZE]);
    }
    
    public void testShiftjis() throws IOException {

        // Here's the bytes for the JIS encoding of the Japanese form of Nihongo
        byte[] bytes_nihongo = {
            (byte) 0x1B, (byte) 0x24, (byte) 0x42, (byte) 0x46,
            (byte) 0x7C, (byte) 0x4B, (byte) 0x5C, (byte) 0x38,
            (byte) 0x6C, (byte) 0x1B, (byte) 0x28, (byte) 0x42,
            (byte) 0x1B, (byte) 0x28, (byte) 0x42 };
        final String ENCODING = "SJIS";
        // Here is nihongo converted to JVM encoding.
        String nihongo = new String(bytes_nihongo, ENCODING);

        RecordingOutputStream ros = writeTestStream(
                bytes_nihongo,MULTIPLIER,
                "testShiftjis",MULTIPLIER);
        // TODO: check for existence of overflow file?
        ReplayCharSequence rcs = getReplayCharSequence(ros,Charset.forName(ENCODING));
            
        // Now check that start of the rcs comes back in as nihongo string.
        String rcsStr = rcs.subSequence(0, nihongo.length()).toString();
        assertTrue("Nihongo " + nihongo + " does not equal converted string" +
                " from rcs " + rcsStr,
            nihongo.equals(rcsStr));
        // And assert next string is also properly nihongo.
        if (rcs.length() >= (nihongo.length() * 2)) {
            rcsStr = rcs.subSequence(nihongo.length(),
                nihongo.length() + nihongo.length()).toString();
            assertTrue("Nihongo " + nihongo + " does not equal converted " +
                " string from rcs (2nd time)" + rcsStr,
                nihongo.equals(rcsStr));
        }
    }

    public void testGetReplayCharSequenceByteZeroOffset() throws IOException {

        RecordingOutputStream ros = writeTestStream(
                regularBuffer,MULTIPLIER,
                "testGetReplayCharSequenceByteZeroOffset",MULTIPLIER);
        ReplayCharSequence rcs = getReplayCharSequence(ros);

        for (int i = 0; i < MULTIPLIER; i++) {
            accessingCharacters(rcs);
        }
    }

    private ReplayCharSequence getReplayCharSequence(RecordingOutputStream ros) throws IOException {
        return getReplayCharSequence(ros,null);
    }

    private ReplayCharSequence getReplayCharSequence(RecordingOutputStream ros, Charset charset) throws IOException {
        return new GenericReplayCharSequence(ros.getReplayInputStream(), 
                ros.getBufferLength()/2, ros.backingFilename, charset);
    }


    public void testGetReplayCharSequenceMultiByteZeroOffset()
        throws IOException {

        RecordingOutputStream ros = writeTestStream(
                regularBuffer,MULTIPLIER,
                "testGetReplayCharSequenceMultiByteZeroOffset",MULTIPLIER);
        ReplayCharSequence rcs = getReplayCharSequence(ros,Charsets.UTF_8);

        for (int i = 0; i < MULTIPLIER; i++) {
            accessingCharacters(rcs);
        }
    }
    
    public void testReplayCharSequenceByteToString() throws IOException {
        String fileContent = "Some file content";
        byte [] buffer = fileContent.getBytes();
        RecordingOutputStream ros = writeTestStream(
                buffer,1,
                "testReplayCharSequenceByteToString.txt",0);
        ReplayCharSequence rcs = getReplayCharSequence(ros);
        String result = rcs.toString();
        assertEquals("Strings don't match",result,fileContent);
    }
    
    private String toHexString(String str)
    {
        if (str != null) {
            StringBuilder buf = new StringBuilder("{ ");
            buf.append(Integer.toString(str.charAt(0), 16));
            for (int i = 1; i < str.length(); i++) {
                buf.append(", ");
                buf.append(Integer.toString(str.charAt(i), 16));
            }
            buf.append(" }");
            return buf.toString();
        }
        else 
            return "null";
    }
     
    public void testSingleByteEncodings() throws IOException {
        byte[] bytes = {
            (byte) 0x61, (byte) 0x62, (byte) 0x63, (byte) 0x64,
            (byte) 0x7d, (byte) 0x7e, (byte) 0x7f, (byte) 0x80,
            (byte) 0x81, (byte) 0x82, (byte) 0x83, (byte) 0x84,
            (byte) 0xfc, (byte) 0xfd, (byte) 0xfe, (byte) 0xff };

        String latin1String = new String(bytes, "latin1");
        RecordingOutputStream ros = writeTestStream(
                bytes, 1, "testSingleByteEncodings-latin1.txt", 0);
        ReplayCharSequence rcs = getReplayCharSequence(ros,Charsets.ISO_8859_1);
        String result = rcs.toString();
        logger.fine("latin1[0] " + toHexString(latin1String));
        logger.fine("latin1[1] " + toHexString(result));
        assertEquals("latin1 strings don't match", result, latin1String);
        
        String w1252String = new String(bytes, "windows-1252");
        ros = writeTestStream(
                bytes, 1, "testSingleByteEncodings-windows-1252.txt", 0);
        rcs = getReplayCharSequence(ros,Charset.forName("windows-1252"));
        result = rcs.toString();
        logger.fine("windows-1252[0] " + toHexString(w1252String));
        logger.fine("windows-1252[1] " + toHexString(result));
        assertEquals("windows-1252 strings don't match", result, w1252String);

        String asciiString = new String(bytes, "ascii");
        ros = writeTestStream(
                bytes, 1, "testSingleByteEncodings-ascii.txt", 0);
        rcs = getReplayCharSequence(ros,Charset.forName("ascii"));
        result = rcs.toString();
        logger.fine("ascii[0] " + toHexString(asciiString));
        logger.fine("ascii[1] " + toHexString(result));
        assertEquals("ascii strings don't match", result, asciiString);
    }
    
    public void testReplayCharSequenceByteToStringOverflow() throws IOException {
        String fileContent = "Some file content. "; // ascii
        byte [] buffer = fileContent.getBytes();
        RecordingOutputStream ros = writeTestStream(
                buffer,1,
                "testReplayCharSequenceByteToStringOverflow.txt",1);
        String expectedContent = fileContent+fileContent;
        
        // The string is ascii which is a subset of both these encodings. Use
        // both encodings because they exercise different code paths. UTF-8 is
        // decoded to UTF-16 while windows-1252 is memory mapped directly. See
        // GenericReplayCharSequence
        ReplayCharSequence rcsUtf8 = getReplayCharSequence(ros,Charsets.UTF_8);
        ReplayCharSequence rcs1252 = getReplayCharSequence(ros,Charset.forName("windows-1252"));

        String result = rcsUtf8.toString();
        assertEquals("Strings don't match", expectedContent, result);

        result = rcs1252.toString();
        assertEquals("Strings don't match", expectedContent, result);
    }
    
    public void testReplayCharSequenceByteToStringMulti() throws IOException {
        String fileContent = "Some file content";
        byte [] buffer = fileContent.getBytes("UTF-8");
        final int MULTIPLICAND = 10;
        StringBuilder sb =
            new StringBuilder(MULTIPLICAND * fileContent.length());
        for (int i = 0; i < MULTIPLICAND; i++) {
            sb.append(fileContent);
        }
        String expectedResult = sb.toString();
        RecordingOutputStream ros = writeTestStream(
                buffer,1,
                "testReplayCharSequenceByteToStringMulti.txt",MULTIPLICAND-1);
        for (int i = 0; i < 3; i++) {
            ReplayCharSequence rcs = getReplayCharSequence(ros,Charsets.UTF_8);
            String result = rcs.toString();
            assertEquals("Strings don't match", result, expectedResult);
            rcs.close();
            System.gc();
            System.runFinalization();
        }
    }
    
    public void xestHugeReplayCharSequence() throws IOException {
        String fileContent = "01234567890123456789";
        String characterEncoding = "ascii";
        byte[] buffer = fileContent.getBytes(characterEncoding);

        long reps = (long) Integer.MAX_VALUE / (long) buffer.length + 1000000l;

        logger.info("writing " + (reps * buffer.length)
                + " bytes to testHugeReplayCharSequence.txt");
        RecordingOutputStream ros = writeTestStream(buffer, 0,
                "testHugeReplayCharSequence.txt", reps);
        ReplayCharSequence rcs = getReplayCharSequence(ros,Charset.forName(characterEncoding));

        if (reps * fileContent.length() > (long) Integer.MAX_VALUE) {
            assertTrue("ReplayCharSequence has wrong length (length()="
                    + rcs.length() + ") (should be " + Integer.MAX_VALUE + ")",
                    rcs.length() == Integer.MAX_VALUE);
        } else {
            assertEquals("ReplayCharSequence has wrong length (length()="
                    + rcs.length() + ") (should be "
                    + (reps * fileContent.length()) + ")", (long) rcs.length(),
                    reps * (long) fileContent.length());
        }

        // boundary cases or something
        for (int index : new int[] { 0, rcs.length() / 4, rcs.length() / 2,
                rcs.length() - 1, rcs.length() / 4 }) {
            // logger.info("testing char at index=" +
            // NumberFormat.getInstance().format(index));
            assertEquals("Characters don't match (index="
                    + NumberFormat.getInstance().format(index) + ")",
                    fileContent.charAt(index % fileContent.length()), rcs
                            .charAt(index));
        }

        // check that out of bounds indices throw exception
        for (int n : new int[] { -1, Integer.MIN_VALUE, rcs.length() + 1 }) {
            try {
                String message = "rcs.charAt(" + n + ")=" + rcs.charAt(n)
                        + " ?!? -- expected IndexOutOfBoundsException";
                logger.severe(message);
                fail(message);
            } catch (IndexOutOfBoundsException e) {
                logger.info("got expected exception: " + e);
            }
        }

        // check some characters at random spots & kinda stress test the
        // system's memory mapping facility
        Random rand = new Random(0); // seed so we get the same ones each time
        for (int i = 0; i < 5000; i++) {
            int index = rand.nextInt(rcs.length());
            // logger.info(i + ". testing char at index=" +
            // NumberFormat.getInstance().format(index));
            assertEquals("Characters don't match (index="
                    + NumberFormat.getInstance().format(index) + ")",
                    fileContent.charAt(index % fileContent.length()), rcs
                            .charAt(index));
        }
    }
    
    /**
     * Accessing characters test.
     *
     * Checks that characters in the rcs are in sequence.
     *
     * @param rcs The ReplayCharSequence to try out.
     */
    private void accessingCharacters(CharSequence rcs) {
        long timestamp = (new Date()).getTime();
        int seeks = 0;
        for (int i = (INCREMENT * 2); (i + INCREMENT) < rcs.length();
                i += INCREMENT) {
            checkCharacter(rcs, i);
            seeks++;
            for (int j = i - INCREMENT; j < i; j++) {
                checkCharacter(rcs, j);
                seeks++;
            }
        }
        // Note that printing out below breaks cruisecontrols drawing
        // of the xml unit test results because it outputs disallowed
        // xml characters.
        logger.fine(rcs + " seeks count " + seeks + " in " +
            ((new Date().getTime()) - timestamp) + " milliseconds.");
    }

    /**
     * Check the character read.
     *
     * Throws assertion if not expected result.
     *
     * @param rcs ReplayCharSequence to read from.
     * @param i Character offset.
     */
    private void checkCharacter(CharSequence rcs, int i) {
        int c = rcs.charAt(i);
        assertTrue("Character " + Integer.toString(c) + " at offset " + i +
            " unexpected.", (c % SEQUENCE_LENGTH) == (i % SEQUENCE_LENGTH));
    }

    /**
     * @param baseName
     * @return RecordingOutputStream
     * @throws IOException
     */
    private RecordingOutputStream writeTestStream(byte[] content, 
            int memReps, String baseName, long fileReps) throws IOException {
        String backingFilename = FileUtils.maybeRelative(getTmpDir(),baseName).getAbsolutePath();
        RecordingOutputStream ros = new RecordingOutputStream(
                content.length * memReps,
                backingFilename);
        ros.open();
        ros.markMessageBodyBegin();
        for(long i = 0; i < (memReps+fileReps); i++) {
            // fill buffer (repeat MULTIPLIER times) and 
            // overflow to disk (also MULTIPLIER times)
            ros.write(content);
        }
        ros.close();
        return ros; 
    }


    /**
     * Fill a buffer w/ regular progression of single-byte 
     * (and <= 127) characters.
     * @param buffer Buffer to fill.
     * @return The buffer we filled.
     */
    private byte [] fillBufferWithRegularContent(byte [] buffer) {
        int index = 0;
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (index & 0x00ff);
            index++;
            if (index >= SEQUENCE_LENGTH) {
                // Reset the index.
                index = 0;
            }
        }
        return buffer;
    }

    public void testCheckParameters()
    {
        // TODO.
    }
}
