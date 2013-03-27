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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import org.apache.commons.httpclient.HttpParser;
import org.archive.io.ArchiveReader;
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
    
    public void testGetCompressedArcStream() throws IOException {
        testGetArcStream(true);
    }
    
    public void testGetUncompressedArcStream() throws IOException {
        testGetArcStream(false);
    }

    protected void testGetArcStream(boolean compress) throws IOException, FileNotFoundException {
        File arc = ARCWriterTest.createARCFile(getTmpDir(), true);
        ArchiveReader reader = ARCReaderFactory.get(null, new FileInputStream(arc), compress);
        assertNotNull(reader);
        Iterator<ArchiveRecord> i = reader.iterator();
        
        // ARC header
        assertTrue(i.hasNext());
        ARCRecord r = (ARCRecord)i.next();
        assertEquals("filedesc://test.arc", r.getHeader().getHeaderValue("subject-uri"));
        
        // 1 fake http record
        assertTrue(i.hasNext());
        r = (ARCRecord)i.next();
        assertEquals(200, r.getStatusCode());
        assertEquals("http://www.archive.org/test/", r.getHeader().getHeaderValue("subject-uri"));
        
        assertFalse(i.hasNext());
        reader.close();
    }

    private static final String ARC_RECORD_BAD_HEADERS = 
            "filedesc://NARA-PEOT-2004-20041014205819-00000-crawling009.archive.org.arc 0.0.0.0 20041014205819 text/plain 1507\n"
                    + "1 1 InternetArchive\n"
                    + "URL IP-address Archive-date Content-type Archive-length\n"
                    + "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
                    + "<arcmetadata xmlns=\"http://archive.org/arc/1.0/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:arc=\"http://archive.org/arc/1.0/\" xsi:schemaLocation=\"http://archive.org/arc/1.0/ http://www.archive.org/arc/1.0/arc.xsd\">\n"
                    + "<arc:software>Heritrix 1.0.5-200410121100 http://crawler.archive.org</arc:software>\n"
                    + "<arc:hostname>crawling009.archive.org</arc:hostname>\n"
                    + "<arc:ip>207.241.227.210</arc:ip>\n"
                    + "<dcterms:isPartOf>NARA-GOV-CRAWL-A</dcterms:isPartOf>\n"
                    + "<dc:description>United States National Archives and Records Administration Presidential End of Term Web Harvest</dc:description>\n"
                    + "<arc:operator>Igor Ranitovic</arc:operator>\n"
                    + "<dc:publisher>Internet Archive</dc:publisher>\n"
                    + "<dcterms:audience>United States National Archives and Records Administration</dcterms:audience>\n"
                    + "<dc:date xsi:type=\"dcterms:W3CDTF\">2004-10-14T20:58:03+00:00</dc:date>\n"
                    + "<arc:http-header-user-agent>Mozilla/5.0 (compatible; heritrix/1.0.5-200410121100 +http://www.archives.gov/crawl.html)</arc:http-header-user-agent>\n"
                    + "<arc:http-header-from>archive-crawler-agent@lists.sourceforge.net</arc:http-header-from>\n"
                    + "<arc:robots>ignore</arc:robots>\n"
                    + "<dc:format>ARC file version 1.1</dc:format>\n"
                    + "<dcterms:conformsTo xsi:type=\"dcterms:URI\">http://www.archive.org/web/researcher/ArcFileFormat.php</dcterms:conformsTo>\n"
                    + "</arcmetadata>\n"
                    + "\n"
                    + "\n"
                    + "http://schrock.house.gov/PollTemplate_PollAnswers_1 143.231.169.175 20041014211845 text/html 387\n"
                    + "HTTP/1.1 302 Moved Temporarily\r\n"
                    + "Server: Microsoft-IIS/5.0\r\n"
                    + "Date: Thu, 14 Oct 2004 21:21:24 GMT\r\n"
                    + "Location: /PollTemplate_PollAnswers_1/\r\n"
                    + "HTTP/1.1 404 Object Not Found\r\n"
                    + "Server: Microsoft-IIS/5.0\r\n"
                    + "Date: Thu, 14 Oct 2004 21:21:24 GMT\r\n"
                    + "Content-Type: text/html\r\n"
                    + "Content-Length: 108\r\n"
                    + "\r\n"
                    + "<html><head><title>Object Not Found</title></head><body><h1>HTTP/1.1 404 Object Not Found</h1></body></html>\n";

    /**
     * This test exercises handling of bad http headers currently handled in the
     * archive-overlay version of
     * {@link HttpParser#parseHeaders(java.io.InputStream, String)}, which
     * wayback cdx indexer depends on
     */
    public void testBadArcHeaders() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(ARC_RECORD_BAD_HEADERS.getBytes("UTF-8"));
        ArchiveReader reader = ARCReaderFactory.get(null, in, false);
        assertNotNull(reader);

        Iterator<ArchiveRecord> i = reader.iterator();

        // ARC header
        assertTrue(i.hasNext());
        ARCRecord r = (ARCRecord)i.next();
        assertEquals("filedesc://NARA-PEOT-2004-20041014205819-00000-crawling009.archive.org.arc", r.getHeader().getHeaderValue("subject-uri"));

        // record with 2 http status lines
        assertTrue(i.hasNext());
        r = (ARCRecord)i.next();
        assertEquals("http://schrock.house.gov/PollTemplate_PollAnswers_1", r.getHeader().getHeaderValue("subject-uri"));
        assertEquals(302, r.getStatusCode());
        assertEquals("HttpClient-Bad-Header-Line-Failed-Parse", r.getHttpHeaders()[3].getName());
        assertEquals("HTTP/1.1 404 Object Not Found", r.getHttpHeaders()[3].getValue());

        assertFalse(i.hasNext());
        reader.close();
    }
}
