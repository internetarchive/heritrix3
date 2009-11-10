
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
package org.archive.util.ms;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.logging.Logger;

import org.apache.poi.hdf.extractor.WordDocument;

import junit.framework.TestCase;


public class DocTest extends TestCase {
    private final Logger logger = Logger.getLogger(this.getClass().getName());

    
    final private static File TEST_DIR;
    static {
        // handle case when unit test is run in either 'commons' or in 
        // enclosing project
        File f = new File("src/test/java/org/archive/util/ms");
        TEST_DIR = f.exists() 
            ? f 
            : new File("commons/src/test/java/org/archive/util/ms");
    }

    // Rename to testAgainstPOI to actually run the test.
    public void testAgainstPOI() throws IOException {
        int errors = 0;
        long start = System.currentTimeMillis();
        for (File f : TEST_DIR.listFiles()) {
            try {
                start = System.currentTimeMillis();
                if (f.getName().endsWith(".doc")) {
                    errors += runDoc(f);
                }
            } finally {
                long duration = System.currentTimeMillis() - start;
                logger.fine("Duration in milliseconds: " + duration);
            }
        }
        if (errors > 0) {
            throw new IOException(errors + " errors, see stdout.");
        }
    }

    
    private int runDoc(File doc) throws IOException {
        logger.fine("===== Now processing " + doc.getName());
        String name = doc.getName();
        int p = name.lastIndexOf('.');
        String expectedName = name.substring(0, p) + ".txt";
        File expectedFile = new File(TEST_DIR, expectedName);
        if (!expectedFile.exists()) {
            createExpectedOutput(doc, expectedFile);
        }
        return runFiles(doc, expectedFile);
    }
    
    
    private void createExpectedOutput(File doc, File output) 
    throws IOException {
        FileInputStream finp = new FileInputStream(doc);
        FileOutputStream fout = new FileOutputStream(output);

        try {
            WordDocument wd = new WordDocument(finp);        
            Writer writer = new OutputStreamWriter(fout, "UTF-16BE");
            wd.writeAllText(writer);
        } finally {
            close(finp);
            close(fout);
        }
    }
    
    
    private static void close(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    private int runFiles(File doc, File expected) 
    throws IOException {
        FileInputStream expectedIn = new FileInputStream(expected);
        Reader expectedReader = new InputStreamReader(expectedIn, "UTF-16BE");
        Reader docReader = Doc.getText(doc);
        try {
            return runReaders(docReader, expectedReader);
        } finally {
            close(docReader);
            close(expectedReader);
        }
    }
    
    
    private int runReaders(Reader doc, Reader expected) 
    throws IOException {
        int count = 0;
        int errors = 0;
        boolean go = true;
        while (go) {
            int ch = doc.read();
            int expectedCh = correctPOI(expected.read());
            if ((ch < 0) || (expectedCh < 0)) {
                go = false;
                if ((ch >= 0) || (expectedCh >= 0)) {
                    errors++;
                    logger.fine("File lengths differ.");
                }
            }
            if (ch != expectedCh) {
                errors += 1;
                report(count, expectedCh, ch);
            }
            count++;
        }
        return errors;
    }

    
    private void report(int count, int expected, int actual) {
        StringBuilder msg = new StringBuilder("#").append(count);
        msg.append(": Expected ");
        msg.append(expected).append(" (").append(toChar(expected));
        msg.append(") but got ").append(actual).append(" (");
        msg.append(toChar(actual)).append(").");
        logger.fine(msg.toString());
    }


    private static String toChar(int ch) {
        if (ch < 0) {
            return "EOF";
        } else {
            return Character.toString((char)ch);
        }
    }
    
    /**
     * Corrects POI's Cp1252 output.  There's a bug somewhere in POI that
     * makes it produce incorrect characters.  Not sure where and don't have
     * time to track it down.  But I have visually checked the input 
     * documents to verify that Doc is producing the right character, and
     * that POI is not.
     * 
     * @param ch  the POI-produced character to check
     * @return    the corrected character
     */
    private static int correctPOI(int ch) {
        switch (ch) {
            case 8734:
                // POI produced the infinity sign when it should have 
                // produced the degrees sign.
                return 176;
            case 214:
                // POI produced an umat O instead of an ellipses mark.
                return 8230;
            case 237:
                // POI produced an acute i instead of a fancy single quote
                return 8217;
            case 236:
                // POI produced a reverse acute i instead of fancy double quote
                return 8220;
            case 238:
                // POI produced a caret i instead of fancy double quote
                return 8221;
            default:
                return ch;
        }
    }

    
}
