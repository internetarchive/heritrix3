/* RepositionableInputStreamTest.java
 *
 * $Id$
 *
 * Created Dec 20, 2005
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
