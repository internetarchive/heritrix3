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
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import org.apache.commons.lang.StringUtils;
import org.archive.io.ArchiveRecord;
import org.archive.io.arc.ARCWriterTest;
import org.archive.util.TmpDirTestCase;

public class ArchiveReaderFactoryTest extends TmpDirTestCase { 
    /**
     * Test local file as URL
     * @throws IOException
     */
    public void testGetFileURL() throws IOException {
        File arc = ARCWriterTest.createARCFile(getTmpDir(), true);
        ArchiveReader reader = null;
        try {
            reader = ArchiveReaderFactory.
                get(new URL("file:////" + arc.getAbsolutePath()));
            for (Iterator<ArchiveRecord> i = reader.iterator(); i.hasNext();) {
                ArchiveRecord r = (ArchiveRecord)i.next();
                assertTrue("mime unread",StringUtils.isNotBlank(r.getHeader().getMimetype()));
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
    
    /**
     * Test local file as File
     * @throws IOException
     */
    public void testGetFile() throws IOException {
        File arc = ARCWriterTest.createARCFile(getTmpDir(), true);
        ArchiveReader reader = null;
        try {
            reader = ArchiveReaderFactory.get(arc.getAbsoluteFile());
            for (Iterator<ArchiveRecord> i = reader.iterator(); i.hasNext();) {
                ArchiveRecord r = (ArchiveRecord)i.next();
                assertTrue("mime unread",StringUtils.isNotBlank(r.getHeader().getMimetype()));
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
    
    /**
     * Test local file as String path
     * @throws IOException
     */
    public void testGetPath() throws IOException {
        File arc = ARCWriterTest.createARCFile(getTmpDir(), true);
        ArchiveReader reader = null;
        try {
            reader = ArchiveReaderFactory.get(arc.getAbsoluteFile().getAbsolutePath());
            for (Iterator<ArchiveRecord> i = reader.iterator(); i.hasNext();) {
                ArchiveRecord r = (ArchiveRecord)i.next();
                assertTrue("mime unread",StringUtils.isNotBlank(r.getHeader().getMimetype()));
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
