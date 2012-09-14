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
package org.archive.modules.fetcher;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;
import org.archive.util.Recorder;

public class RecordingSocketInputBuffer implements SessionInputBuffer {

    protected Socket socket;
    protected InputStream in;
    protected HttpTransportMetricsImpl metrics;

    /**
     * Gets recorder from current thread.
     * @param socket
     * @param buffersize
     * @param params
     * @throws IOException
     */
    public RecordingSocketInputBuffer(Socket socket, int buffersize, HttpParams params) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        this.socket = socket;
        this.metrics = new HttpTransportMetricsImpl();
        
        Recorder recorder = Recorder.getHttpRecorder();
        if (recorder == null) {   // XXX || (isSecure() && isProxied())) {
            // no recorder, OR defer recording for pre-tunnel leg
            this.in = new BufferedInputStream(socket.getInputStream(), buffersize);
        } else {
            this.in = recorder.inputWrap(new BufferedInputStream(socket.getInputStream(), buffersize));
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = in.read(b, off, len);
        if (n > 0) {
            metrics.incrementBytesTransferred(n);
        }
        return n;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = in.read(b);
        if (n > 0) {
            metrics.incrementBytesTransferred(n);
        }
        return n;
    }

    @Override
    public int read() throws IOException {
        int n = in.read();
        if (n > 0) {
            metrics.incrementBytesTransferred(n);
        }
        return n;
    }

    /**
     * Reads a complete line of characters up to a line delimiter from this
     * session buffer into the given line buffer. The number of chars actually
     * read is returned as an integer. The line delimiter itself is discarded.
     * If no char is available because the end of the stream has been reached,
     * the value <code>-1</code> is returned. This method blocks until input
     * data is available, end of file is detected, or an exception is thrown.
     * <p>
     * This is only used for http headers, which are all ascii, so input is
     * treated as such.
     * <p>
     * This method treats a lone LF as a valid line delimiters in addition to
     * CR-LF required by the HTTP specification.
     * 
     * @param charbuffer
     *            the line buffer.
     * @return number of bytes (i.e. ascii characters) read
     * @exception IOException
     *                if an I/O error occurs.
     */
    @Override
    public int readLine(CharArrayBuffer buffer) throws IOException {
        int bytesRead = 0;
        int b = in.read();
        while (b >= 0 && b != HTTP.LF) {
            bytesRead++;
            buffer.append((char) b);
            b = in.read();
        }
        if (b >= 0) {
            bytesRead++; // count LF
        }
        
        // if line ends with CR-LF, get rid of the CR
        if (bytesRead > 0 && buffer.charAt(buffer.length() - 1) == HTTP.CR) {
            buffer.setLength(buffer.length() - 1);
        }

        if (bytesRead > 0) {
            metrics.incrementBytesTransferred(bytesRead);
        }
        
        if (bytesRead == 0 && b == -1) {
            // indicate the end of stream
            return -1;
        }

        return bytesRead;
    }

    @Override
    public String readLine() throws IOException {
        CharArrayBuffer charbuffer = new CharArrayBuffer(64);
        int l = readLine(charbuffer);
        if (l != -1) {
            return charbuffer.toString();
        } else {
            return null;
        }
    }

    @Override
    public boolean isDataAvailable(int timeout) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return metrics;
    }
}