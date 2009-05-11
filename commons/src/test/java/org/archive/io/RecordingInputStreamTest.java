/* RecordingInputStreamTest
 *
 * $Id$
 *
 * Created on Aug 1, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
