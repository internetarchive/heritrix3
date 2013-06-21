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
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.httpclient.Header;
import org.archive.io.arc.ARCRecord;
import org.archive.io.warc.WARCRecord;

public class HeaderedArchiveRecordTest extends TestCase {
    private static final String HTTPHEADER = "HTTP/1.1 200 OK\r\n"
            + "Last-Modified: Sun, 28 Aug 2005 14:10:55 GMT\r\n"
            + "Content-Length: 108\r\n" + "Connection: close\r\n"
            + "Content-Type: text/html\r\n" + "\r\n";
    private static final String BODY = "<html>\r\n" + "  <head>\r\n"
            + "    <title>Neue Seite 1</title>\r\n" + "  </head>\r\n"
            + "  <body bgcolor=\"#000066\">\r\n" + "  </body>\r\n" + "</html>";

    public void testParseHttpHeadersInWARC() throws IOException {
        final String url = "http://foo.maths.uq.edu.au/index.html";
        // final String warcHeader = "WARC/0.10 000000000486 response " +
        // url + " 20070315152520 " +
        // "urn:uuid:d8b342a8-dba4-4d7f-a551-1d8184f2ff58 " +
        // "application/http; msgtype=response\r\n" +
        // "Checksum: sha1:IT6YEX5WHKK57GOEHV2YHTTXEP5KPM6A\r\n" +
        // "IP-Address: 80.150.6.184\r\n" +
        // "\r\n";

        final String warcHeader = "WARC/0.12\r\n"
           + "MIME-Version: 1.0\r\n"
           + "WARC-Record-Type: response\r\n"
           + "WARC-Target-URI: http://foo.maths.uq.edu.au/index.html\r\n"
           + "WARC-Date: 2006-09-19T17:20:24Z\r\n"
           + "WARC-Digest: sha1:IT6YEX5WHKK57GOEHV2YHTTXEP5KPM6A\r\n"
           + "WARC-IP-Address: 80.150.6.184\r\n"
           + "Content-ID: <urn:uuid:d8b342a8-dba4-4d7f-a551-1d8184f2ff58>\r\n"
           + "Content-Type: application/http; msgtype=response\r\n"
           + "Content-Length: " + (HTTPHEADER.length() + BODY.length()) + "\r\n"
           + "\r\n";

        final String hdr = warcHeader + HTTPHEADER + BODY;

        WARCRecord r = new WARCRecord(new ByteArrayInputStream(hdr.getBytes()),
                "READER_IDENTIFIER", 0, false, true);
        HeaderedArchiveRecord har = new HeaderedArchiveRecord(r, true);

        har.skipHttpHeader();

        byte[] b = new byte[BODY.length()];
        har.read(b);
        String bodyRead = new String(b);
        assertEquals(BODY, bodyRead);
        assertHeaderCorrectlyParsed(har.getContentHeaders());
        assertEquals("failed to retrieve Url from metadata", har.getHeader()
                .getUrl(), url);
    }

    public void testParseHttpHeadersInARC() throws IOException {
        final int len = HTTPHEADER.length() + BODY.length();
        final int contentLength = BODY.length();
        final String url = "http://www.ly.gov.tw:80/accpart.htm";
        final String hdr = HTTPHEADER + BODY;
        // Interesting difference between ARCRecord and WARCRecord is that the
        // stream passed the ARCRecord is supposed to be just past the
        // ARCRecord metadata line where as stream passed WARCRecord is at
        // record start. TODO: Add to ARCRecord constructor that doesn't
        // take an ArchiveRecordHeader but rather parses it from the stream.
        ArchiveRecordHeader arh = new ArchiveRecordHeader() {
            public int getContentBegin() {
                // TODO: In ARCs, this is where http headers end and
                // the content begins. Need to reconcile for generic
                // HeaderedArchiveRecord processing. In this context, it
                // makes sense setting it to zero -- HeaderedArchiveRecord
                // will then figure it out.
                return 0;
            }

            public String getDate() {
                return null;
            }

            public String getDigest() {
                return null;
            }

            public Set<String> getHeaderFieldKeys() {
                return null;
            }

            public Map<String,Object> getHeaderFields() {
                return null;
            }

            public Object getHeaderValue(String key) {
                return null;
            }

            public long getLength() {
                return len;
            }
            
            public long getContentLength() {
            	return contentLength;
            }

            public String getMimetype() {
                return null;
            }

            public long getOffset() {
                return 0;
            }

            public String getReaderIdentifier() {
                return null;
            }

            public String getRecordIdentifier() {
                return null;
            }

            public String getUrl() {
                return url;
            }

            public String getVersion() {
                return null;
            }

        };
        ARCRecord r = new ARCRecord(new ByteArrayInputStream(hdr.getBytes()),
                arh, 0, false, true, false);

        HeaderedArchiveRecord har = new HeaderedArchiveRecord(r, true);
        har.skipHttpHeader();
        byte[] b = new byte[BODY.length()];
        har.read(b);
        String bodyRead = new String(b);
        assertEquals(BODY, bodyRead);
        assertHeaderCorrectlyParsed(har.getContentHeaders());
    }

    public void testEasierParseHttpHeadersInARC() throws IOException {
        final String url = "http://www.archive.org/index.htm";
        final String arcHeader = url
                + " 192.168.0.1 20070515111004 text/html 167568\n";
        final String hdr = arcHeader + HTTPHEADER + BODY;

        ARCRecord r = new ARCRecord(new ByteArrayInputStream(hdr.getBytes()),
                "READER_IDENTIFIER", 0, false, true, false);

        HeaderedArchiveRecord har = new HeaderedArchiveRecord(r, true);
        har.skipHttpHeader();
        byte[] b = new byte[BODY.length()];
        har.read(b);
        String bodyRead = new String(b);
        assertEquals(BODY, bodyRead);
        assertHeaderCorrectlyParsed(har.getContentHeaders());
        assertEquals("failed to retrieve Url from metadata", har.getHeader()
                .getUrl(), url);
    }

    private void assertHeaderCorrectlyParsed(Header[] headers) {
        final List<String> orgHeaders = Arrays.asList(HTTPHEADER.split("\r\n"));
        assertEquals("not all HTTP header entries have been retrieved",
                orgHeaders.size(), headers.length + 1);

        for (Header header : headers) {
            assertTrue(orgHeaders.contains(header.getName() + ": "
                    + header.getValue()));
        }
    }

    public void testNoheaderWARC() throws IOException {
        String b = "hello world";
        String c = "WARC/0.12\r\nContent-Type: text/plain\r\n"
                + "Content-Length: " + b.length() + "\r\n\r\n" + b;
        org.archive.io.warc.WARCRecord r = new org.archive.io.warc.WARCRecord(
                new ByteArrayInputStream(c.getBytes()), "READER_IDENTIFIER", 0,
                false, true);
        HeaderedArchiveRecord har = new HeaderedArchiveRecord(r, true);
        assertTrue(har.isStrict());
    }
}
