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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.NoSuchElementException;

import org.archive.io.WriterPoolMember;
import org.archive.io.WriterPool;
import org.archive.io.WriterPoolSettings;
import org.archive.util.TmpDirTestCase;


/**
 * Test ARCWriterPool
 */
@SuppressWarnings("deprecation")
public class ARCWriterPoolTest extends TmpDirTestCase {
    private static final String PREFIX = "TEST";
    
    public void testARCWriterPool()
    throws Exception {
        final int MAX_ACTIVE = 3;
        final int MAX_WAIT_MILLISECONDS = 100;
        cleanUpOldFiles(PREFIX);
        WriterPool pool = new ARCWriterPool(getSettings(true),
            MAX_ACTIVE, MAX_WAIT_MILLISECONDS);
        WriterPoolMember [] writers = new WriterPoolMember[MAX_ACTIVE];
        final String CONTENT = "Any old content";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(CONTENT.getBytes());
        for (int i = 0; i < MAX_ACTIVE; i++) {
            writers[i] = pool.borrowFile();
            assertEquals("Number active", i + 1, pool.getNumActive());
            ((ARCWriter)writers[i]).write("http://one.two.three", "no-type",
            	"0.0.0.0", 1234567890, CONTENT.length(), baos);
        }

        // Pool is maxed out.  Try and get a new ARCWriter.  We'll block for
        // MAX_WAIT_MILLISECONDS.  Should get exception.
        long start = (new Date()).getTime();
        boolean isException = false;
        try {
            pool.borrowFile();
        } catch(NoSuchElementException e) {
            isException = true;
            long end = (new Date()).getTime();
            // This test can fail on a loaded machine if the wait period is
            // only MAX_WAIT_MILLISECONDS.  Up the time to wait.
            final int WAIT = MAX_WAIT_MILLISECONDS * 100;
            if ((end - start) > (WAIT)) {
                fail("More than " + MAX_WAIT_MILLISECONDS + " elapsed: "
                    + WAIT);
            }
        }
        assertTrue("Did not get NoSuchElementException", isException);

        for (int i = (MAX_ACTIVE - 1); i >= 0; i--) {
            pool.returnFile(writers[i]);
            assertEquals("Number active", i, pool.getNumActive());
            assertEquals("Number idle", MAX_ACTIVE - pool.getNumActive(),
                    pool.getNumIdle());
        }
        pool.close();
    }
    
    public void testInvalidate() throws Exception {
        final int MAX_ACTIVE = 3;
        final int MAX_WAIT_MILLISECONDS = 100;
        cleanUpOldFiles(PREFIX);
        WriterPool pool = new ARCWriterPool(getSettings(true),
            MAX_ACTIVE, MAX_WAIT_MILLISECONDS);
        WriterPoolMember [] writers = new WriterPoolMember[MAX_ACTIVE];
        final String CONTENT = "Any old content";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(CONTENT.getBytes());
        for (int i = 0; i < MAX_ACTIVE; i++) {
            writers[i] = pool.borrowFile();
            assertEquals("Number active", i + 1, pool.getNumActive());
            ((ARCWriter)writers[i]).write("http://one.two.three", "no-type",
            	"0.0.0.0", 1234567890, CONTENT.length(), baos);
        }
     
        WriterPoolMember writer2Invalidate = writers[pool.getNumActive() - 1];
        writers[pool.getNumActive() - 1] = null;
        pool.invalidateFile(writer2Invalidate);
        for (int i = 0; i < (MAX_ACTIVE - 1); i++) {
            if (writers[i] == null) {
                continue;
            }
            pool.returnFile(writers[i]);
        }
        
        for (int i = 0; i < MAX_ACTIVE; i++) {
            writers[i] = pool.borrowFile();
            assertEquals("Number active", i + 1, pool.getNumActive());
            ((ARCWriter)writers[i]).write("http://one.two.three", "no-type",
            	"0.0.0.0", 1234567890, CONTENT.length(), baos);
        }
        for (int i = (MAX_ACTIVE - 1); i >= 0; i--) {
            pool.returnFile(writers[i]);
            assertEquals("Number active", i, pool.getNumActive());
            assertEquals("Number idle", MAX_ACTIVE - pool.getNumActive(),
                    pool.getNumIdle());
        }
        pool.close();
    }
    
    private WriterPoolSettings getSettings(final boolean isCompressed) {
        return new WriterPoolSettings() {
            public long getMaxSize() {
                return ARCConstants.DEFAULT_MAX_ARC_FILE_SIZE;
            }
            
            public String getPrefix() {
                return PREFIX;
            }
            
            public String getSuffix() {
                return "";
            }
            
            public List<File> getOutputDirs() {
                File [] files = {getTmpDir()};
                return Arrays.asList(files);
            }
            
            public boolean isCompressed() {
                return isCompressed;
            }
            
            public List<String> getMetadata() {
            	return null;
            }
        };
    }
}