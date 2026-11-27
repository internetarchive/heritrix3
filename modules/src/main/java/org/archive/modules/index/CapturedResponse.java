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

package org.archive.modules.index;

import java.io.Closeable;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.apache.http.impl.io.ChunkedInputStream;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.archive.format.http.HttpHeader;
import org.archive.io.ArchiveReader;
import org.archive.io.HeaderedArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;

/**
 * The HTTP response of a {@link Capture}, read back from its WARC file. The headers are as
 * recorded. The body is as recorded with any chunked transfer coding removed, so a recorded
 * Transfer-Encoding header no longer applies to it.
 */
public record CapturedResponse(int status, List<HttpHeader> headers, InputStream body)
        implements Closeable {

    /**
     * Opens the response of a capture, reading its status and headers. The body is left to be read
     * from the returned response, which must be closed. For a revisit, the status and headers come
     * from the revisit record and the body from the record it refers to, except for a 304 Not
     * Modified revisit, which comes entirely from the record it refers to.
     *
     * @throws IOException if a record can't be read, including a revisit whose original isn't known
     *                     and a response with a transfer coding other than chunked
     */
    public static CapturedResponse open(Capture capture) throws IOException {
        if (!capture.revisit()) return open(capture.path(), capture.offset());
        if (capture.refersToPath() == null) {
            throw new IOException("Unknown original record for revisit");
        }
        CapturedResponse original = open(capture.refersToPath(), capture.refersToOffset());
        if (capture.status() == 304) return original;
        try (CapturedResponse revisit = open(capture.path(), capture.offset())) {
            return new CapturedResponse(revisit.status(), revisit.headers(), original.body());
        } catch (IOException | RuntimeException e) {
            original.close();
            throw e;
        }
    }

    private static CapturedResponse open(Path path, long offset) throws IOException {
        FileInputStream file = openFile(path);
        try {
            file.getChannel().position(offset);
            // the reader detects compression by the name, so give it the one without .open
            ArchiveReader reader = WARCReaderFactory.get(path.toString(), file, false);
            var record = new HeaderedArchiveRecord(reader.get(), true);
            record.skipHttpHeader();
            if (record.getStatusCode() < 0) {
                throw new IOException("Not an HTTP response at " + path + ":" + offset);
            }
            boolean chunked = false;
            for (HttpHeader header : record.getContentHeaders()) {
                if (!header.getName().equalsIgnoreCase("Transfer-Encoding")) continue;
                if (!header.getValue().equalsIgnoreCase("chunked")) {
                    throw new IOException("Unsupported transfer coding " + header.getValue()
                            + " at " + path + ":" + offset);
                }
                chunked = true;
            }
            InputStream body = new FilterInputStream(chunked ? dechunk(record) : record) {
                @Override
                public void close() throws IOException {
                    try (file; reader) {
                        record.close();
                    }
                }
            };
            return new CapturedResponse(record.getStatusCode(),
                    Arrays.asList(record.getContentHeaders()), body);
        } catch (IOException | RuntimeException e) {
            file.close();
            throw e;
        }
    }

    private static InputStream dechunk(InputStream in) {
        var buffer = new SessionInputBufferImpl(new HttpTransportMetricsImpl(), 8192);
        buffer.bind(in);
        return new ChunkedInputStream(buffer);
    }

    private static FileInputStream openFile(Path path) throws IOException {
        try {
            return new FileInputStream(path.toFile());
        } catch (FileNotFoundException e) {
            // still being written
        }
        try {
            return new FileInputStream(path + ".open");
        } catch (FileNotFoundException e) {
            // closed since the first attempt
        }
        return new FileInputStream(path.toFile());
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
