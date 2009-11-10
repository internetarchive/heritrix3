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
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.archive.util.TmpDirTestCase;


/**
 * Test cases for RecordingInputStream.
 *
 * @author gojomo
 */
public class RecordingInputStreamTest extends TmpDirTestCase
{


    /*
     * @see TmpDirTestCase#setUp()
     */
    protected void setUp() throws Exception
    {
        super.setUp();
    }

    /**
     * Test readFullyOrUntil soft (no exception) and hard (exception) 
     * length cutoffs, timeout, and rate-throttling. 
     * 
     * @throws IOException
     * @throws InterruptedException
     * @throws RecorderTimeoutException
     */
    public void testReadFullyOrUntil() throws RecorderTimeoutException, IOException, InterruptedException
    {
        RecordingInputStream ris = new RecordingInputStream(16384, (new File(
                getTmpDir(), "testReadFullyOrUntil").getAbsolutePath()));
        ByteArrayInputStream bais = new ByteArrayInputStream(
                "abcdefghijklmnopqrstuvwxyz".getBytes());
        // test soft max
        ris.open(bais);
        ris.setLimits(10,0,0);
        ris.readFullyOrUntil(7);
        ris.close();
        ReplayInputStream res = ris.getReplayInputStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        res.readFullyTo(baos);
        assertEquals("soft max cutoff","abcdefg",new String(baos.toByteArray()));
        // test hard max
        bais.reset();
        baos.reset();
        ris.open(bais);
        boolean exceptionThrown = false; 
        try {
            ris.setLimits(10,0,0);
            ris.readFullyOrUntil(13);
        } catch (RecorderLengthExceededException ex) {
            exceptionThrown = true;
        }
        assertTrue("hard max exception",exceptionThrown);
        ris.close();
        res = ris.getReplayInputStream();
        res.readFullyTo(baos);
        assertEquals("hard max cutoff","abcdefghijk",
                new String(baos.toByteArray()));
        // test timeout
        PipedInputStream pin = new PipedInputStream(); 
        PipedOutputStream pout = new PipedOutputStream(pin); 
        ris.open(pin);
        exceptionThrown = false; 
        trickle("abcdefghijklmnopqrstuvwxyz".getBytes(),pout);
        try {
            ris.setLimits(0,5000,0);
            ris.readFullyOrUntil(0);
        } catch (RecorderTimeoutException ex) {
            exceptionThrown = true;
        }
        assertTrue("timeout exception",exceptionThrown);
        ris.close();
        // test rate limit
        bais = new ByteArrayInputStream(new byte[1024*2*5]);
        ris.open(bais);
        long startTime = System.currentTimeMillis();
        ris.setLimits(0,0,2);
        ris.readFullyOrUntil(0);
        long endTime = System.currentTimeMillis(); 
        long duration = endTime - startTime; 
        assertTrue("read too fast: "+duration,duration>=5000);
        ris.close();
    }

    protected void trickle(final byte[] bytes, final PipedOutputStream pout) {
        new Thread() {
            public void run() {
                try {
                    for (int i = 0; i < bytes.length; i++) {
                        Thread.sleep(1000);
                        pout.write(bytes[i]);
                    }
                    pout.close();
                } catch (IOException e) {
                    // do nothing
                } catch (Exception e) {
                    System.err.print(e); 
                }                
            }
        }.start();
        
    }
}
