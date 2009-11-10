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
import java.io.PrintWriter;

import org.archive.util.TmpDirTestCase;

public class RepositionableInputStreamTest extends TmpDirTestCase {
    private File testFile;
    private static final String LINE = "0123456789abcdefghijklmnopqrstuv";
    protected void setUp() throws Exception {
        super.setUp();
        this.testFile = new File(getTmpDir(), this.getClass().getName());
        PrintWriter pw = new PrintWriter(new FileOutputStream(testFile));
        for (int i = 0; i < 100; i++) {
            pw.print(LINE);
        }
        pw.close();
    }
    protected void tearDown() throws Exception {
        super.tearDown();
    }
    public void testname() throws Exception {
        // Make buffer awkward size so we run into buffers spanning issues.
        RepositionableInputStream ris =
            new RepositionableInputStream(new FileInputStream(this.testFile),
                57);
        int c = ris.read();
        assertEquals(1, ris.position());
        ris.read();
        ris.position(0);
        assertEquals(0, ris.position());
        int c1 = ris.read();
        assertEquals(c, c1);
        ris.position(0);
        byte [] bytes = new byte[LINE.length()];
        long offset = 0;
        for (int i = 0; i < 10; i++) {
            ris.read(bytes, 0, LINE.length());
            assertEquals(LINE, new String(bytes));
            offset += LINE.length();
            assertEquals(offset, ris.position());
        }
        long p = ris.position();
        ris.position(p - LINE.length());
        assertEquals(p - LINE.length(), ris.position());
        c = ris.read();
        assertEquals(c, c1);
    }
}
