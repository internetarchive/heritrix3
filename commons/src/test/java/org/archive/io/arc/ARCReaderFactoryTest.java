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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import org.archive.io.ArchiveRecord;
import org.archive.util.TmpDirTestCase;

public class ARCReaderFactoryTest extends TmpDirTestCase {
//    public void testGetHttpURL() throws MalformedURLException, IOException {
//        ARCReader reader = null;
//        try {
//            // TODO: I can get a single ARCRecord but trying to iterate from
//            // a certain point is getting an EOR when I go to read GZIP header.
//            reader = ARCReaderFactory.
//                get(new URL("http://localhost/test.arc.gz"), 0);
//            for (final Iterator i = reader.iterator(); i.hasNext();) {
//                ARCRecord ar = (ARCRecord)i.next();
//                System.out.println(ar.getMetaData().getUrl());
//            }
//        } finally {
//            if (reader != null) {
//                reader.close();
//            }
//        }
//    }
    
    /**
     * Test File URL.
     * If a file url, we just use the pointed to file.  There is no
     * copying down to a file in tmp that gets cleaned up after close.
     * @throws MalformedURLException
     * @throws IOException
     */
    public void testGetFileURL() throws MalformedURLException, IOException {
        File arc = ARCWriterTest.createARCFile(getTmpDir(), true);
        doGetFileUrl(arc);
    }
    
    protected void doGetFileUrl(File arc)
    throws MalformedURLException, IOException {
        ARCReader reader = null;
        File tmpFile = null;
        try {
            reader = ARCReaderFactory.
                get(new URL("file:////" + arc.getAbsolutePath()));
            tmpFile = null;
            for (Iterator<ArchiveRecord> i = reader.iterator(); i.hasNext();) {
                ARCRecord r = (ARCRecord)i.next();
                if (tmpFile == null) {
                    tmpFile = new File(r.getMetaData().getArc());
                }
            }
            assertTrue(tmpFile.exists());
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        assertTrue(tmpFile.exists());
    }
    
    /**
     * Test path or url.
     * @throws MalformedURLException 
     * @throws IOException 
     */
    public void testGetPathOrURL() throws MalformedURLException, IOException {
        File arc = ARCWriterTest.createARCFile(getTmpDir(), true);
        ARCReader reader = ARCReaderFactory.get(arc.getAbsoluteFile());
        assertNotNull(reader);
        reader.close();
        doGetFileUrl(arc);
    }   
}
