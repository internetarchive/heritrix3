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

package org.archive.util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.math.LongRange;


/**
 * FileUtils tests. 
 * 
 * @contributor stack
 * @contributor gojomo
 * @version $Date$, $Revision$
 */
public class FileUtilsTest extends TmpDirTestCase {
    private String srcDirName = FileUtilsTest.class.getName() + ".srcdir";
    private File srcDirFile = null;
    private String tgtDirName = FileUtilsTest.class.getName() + ".tgtdir";
    private File tgtDirFile = null;
    
    protected File zeroLengthLinesUnix;
    protected File zeroLengthLinesWindows;

    protected File smallLinesUnix;
    protected File smallLinesWindows;
    protected File largeLinesUnix;
    protected File largeLinesWindows;
    protected File nakedLastLineUnix;
    protected File nakedLastLineWindows;
    
    
    protected void setUp() throws Exception {
        super.setUp();
        this.srcDirFile = new File(getTmpDir(), srcDirName);
        FileUtils.ensureWriteableDirectory(srcDirFile);
        this.tgtDirFile = new File(getTmpDir(), tgtDirName);
        FileUtils.ensureWriteableDirectory(tgtDirFile);
        addFiles();
        
        zeroLengthLinesUnix = setUpLinesFile("zeroLengthLinesUnix",0,0,400,IOUtils.LINE_SEPARATOR_UNIX);
        zeroLengthLinesWindows = setUpLinesFile("zeroLengthLinesUnix",0,0,400,IOUtils.LINE_SEPARATOR_WINDOWS);
        
        smallLinesUnix = setUpLinesFile("smallLinesUnix", 0, 25, 400, IOUtils.LINE_SEPARATOR_UNIX);
        smallLinesWindows = setUpLinesFile("smallLinesWindows", 0, 25, 400, IOUtils.LINE_SEPARATOR_WINDOWS);
        largeLinesUnix = setUpLinesFile("largeLinesUnix", 128, 256, 5, IOUtils.LINE_SEPARATOR_UNIX);
        largeLinesWindows = setUpLinesFile("largeLinesWindows", 128, 256, 4096, IOUtils.LINE_SEPARATOR_WINDOWS);
        
        nakedLastLineUnix = setUpLinesFile("nakedLastLineUnix", 0, 50, 401, IOUtils.LINE_SEPARATOR_UNIX);
        org.apache.commons.io.FileUtils.writeStringToFile(nakedLastLineUnix,"a");
        nakedLastLineWindows = setUpLinesFile("nakedLastLineWindows", 0, 50, 401, IOUtils.LINE_SEPARATOR_WINDOWS);
        org.apache.commons.io.FileUtils.writeStringToFile(nakedLastLineWindows,"a");
    }
 
    private void addFiles() throws IOException {
        addFiles(3, this.getName());
    }
    
    private void addFiles(final int howMany, final String baseName)
    throws IOException {
        for (int i = 0; i < howMany; i++) {
            File.createTempFile(baseName, null, this.srcDirFile);
        }
    }
    
    private File setUpLinesFile(String name, int minLineSize, int maxLineSize, int lineCount, String lineEnding) throws IOException {
        List<String> lines = new LinkedList<String>();
        StringBuilder sb = new StringBuilder(maxLineSize);
        for(int i = 0;  i<lineCount ; i++) {
            sb.setLength(0);
            int lineSize =  (maxLineSize == 0) 
                                ? 0 
                                : minLineSize + (i % (maxLineSize-minLineSize));
            for(int j = 0; j < lineSize; j++) {
                sb.append("-");
            }
            lines.add(sb.toString());
        }
        File file = File.createTempFile(name, null);
        org.apache.commons.io.FileUtils.writeLines(file, lines, lineEnding);
        return file; 
        
    }
    
    protected void tearDown() throws Exception {
        super.tearDown();
        org.apache.commons.io.FileUtils.deleteQuietly(this.srcDirFile);
        org.apache.commons.io.FileUtils.deleteQuietly(this.tgtDirFile);
        org.apache.commons.io.FileUtils.deleteQuietly(zeroLengthLinesUnix);
        org.apache.commons.io.FileUtils.deleteQuietly(zeroLengthLinesWindows);
        org.apache.commons.io.FileUtils.deleteQuietly(smallLinesUnix);
        org.apache.commons.io.FileUtils.deleteQuietly(smallLinesWindows);
        org.apache.commons.io.FileUtils.deleteQuietly(largeLinesUnix);
        org.apache.commons.io.FileUtils.deleteQuietly(largeLinesWindows);
        org.apache.commons.io.FileUtils.deleteQuietly(nakedLastLineUnix);
        org.apache.commons.io.FileUtils.deleteQuietly(nakedLastLineWindows);
        
    }
    
    public void testCopyFile() {
        // Test exception copying nonexistent file.
        File [] srcFiles = this.srcDirFile.listFiles();
        srcFiles[0].delete();
        IOException e = null;
        try {
        FileUtils.copyFile(srcFiles[0],
            new File(this.tgtDirFile, srcFiles[0].getName()));
        } catch (IOException ioe) {
            e = ioe;
        }
        assertNotNull("Didn't get expected IOE", e);
    }
    
    public void testTailLinesZeroLengthUnix() throws IOException {
        verifyTailLines(zeroLengthLinesUnix);
    }
    
    public void testTailLinesZeroLengthWindows() throws IOException {
        verifyTailLines(zeroLengthLinesWindows);
    }
    
    public void testTailLinesSmallUnix() throws IOException {
        verifyTailLines(smallLinesUnix);
    }

    public void testTailLinesLargeUnix() throws IOException {
        verifyTailLines(largeLinesUnix);
    }

    public void testTailLinesSmallWindows() throws IOException {
        verifyTailLines(smallLinesWindows);
    }

    public void testTailLinesLargeWindows() throws IOException {
        verifyTailLines(largeLinesWindows);
    }

    public void testTailLinesNakedUnix() throws IOException {
        verifyTailLines(nakedLastLineUnix);
    }

    public void testTailLinesNakedWindows() throws IOException {
        verifyTailLines(nakedLastLineWindows);
    }
    
    @SuppressWarnings("unchecked")
    private void verifyTailLines(File file) throws IOException {
        List<String> lines = org.apache.commons.io.FileUtils.readLines(file);
        verifyTailLines(file, lines, 1, 80);
        verifyTailLines(file, lines, 5, 80);
        verifyTailLines(file, lines, 10, 80);
        verifyTailLines(file, lines, 20, 80);
        verifyTailLines(file, lines, 100, 80);
        verifyTailLines(file, lines, 1, 1);
        verifyTailLines(file, lines, 5, 1);
        verifyTailLines(file, lines, 10, 1);
        verifyTailLines(file, lines, 20, 1);
        verifyTailLines(file, lines, 100, 1);
    }
    
    
    private void verifyTailLines(File file, List<String> lines, int count, int estimate) throws IOException {
        List<String> testLines; 
        testLines = getTestTailLines(file,count,estimate); 
        assertEquals("line counts not equal:"+file.getName()+" "+count+" "+estimate,lines.size(),testLines.size()); 
        assertEquals("lines not equal: "+file.getName()+" "+count+" "+estimate,lines,testLines); 
    }

    private List<String> getTestTailLines(File file, int count, int estimate) throws IOException {
        long pos = -1;
        List<String> testLines = new LinkedList<String>();
        do {
            List<String> returnedLines = new LinkedList<String>();
            LongRange range = FileUtils.pagedLines(file,pos,-count,returnedLines,estimate);
            Collections.reverse(returnedLines); 
            testLines.addAll(returnedLines);
            pos = range.getMinimumLong()-1;
        } while (pos>=0);
        Collections.reverse(testLines); 
        return testLines;
    }
    
    public void testHeadLinesZeroLengthUnix() throws IOException {
        verifyHeadLines(zeroLengthLinesUnix);
    }
    
    public void testHeadLinesZeroLengthWindows() throws IOException {
        verifyHeadLines(zeroLengthLinesWindows);
    }
    
    public void testHeadLinesSmallUnix() throws IOException {
        verifyHeadLines(smallLinesUnix);
    }

    public void testHeadLinesLargeUnix() throws IOException {
        verifyHeadLines(largeLinesUnix);
    }

    public void testHeadLinesSmallWindows() throws IOException {
        verifyHeadLines(smallLinesWindows);
    }

    public void testHeadLinesLargeWindows() throws IOException {
        verifyHeadLines(largeLinesWindows);
    }

    public void testHeadLinesNakedUnix() throws IOException {
        verifyHeadLines(nakedLastLineUnix);
    }

    public void testHeadLinesNakedWindows() throws IOException {
        verifyHeadLines(nakedLastLineWindows);
    }
    
    
    @SuppressWarnings("unchecked")
    private void verifyHeadLines(File file) throws IOException {
        List<String> lines = org.apache.commons.io.FileUtils.readLines(file);
        verifyHeadLines(file, lines, 1, 80);
        verifyHeadLines(file, lines, 5, 80);
        verifyHeadLines(file, lines, 10, 80);
        verifyHeadLines(file, lines, 20, 80);
        verifyHeadLines(file, lines, 100, 80);
        verifyHeadLines(file, lines, 1, 1);
        verifyHeadLines(file, lines, 5, 1);
        verifyHeadLines(file, lines, 10, 1);
        verifyHeadLines(file, lines, 20, 1);
        verifyHeadLines(file, lines, 100, 1);
    }
    
    
    private void verifyHeadLines(File file, List<String> lines, int count, int estimate) throws IOException {
        List<String> testLines; 
        testLines = getTestHeadLines(file,count,estimate); 
        assertEquals("line counts not equal:"+file.getName()+" "+count+" "+estimate,lines.size(),testLines.size()); 
        assertEquals("lines not equal: "+file.getName()+" "+count+" "+estimate,lines,testLines); 
    }

    private List<String> getTestHeadLines(File file, int count, int estimate) throws IOException {
        long pos = 0;
        List<String> testLines = new LinkedList<String>();
        do {
            LongRange range = FileUtils.pagedLines(file,pos,count,testLines,estimate);
            pos = range.getMaximumLong();
        } while (pos<file.length());
        return testLines;
    }
}
