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

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;
import org.archive.util.Recorder;

public class RecordingSocketOutputBuffer implements SessionOutputBuffer {

    protected static final byte[] CRLF = new byte[] {HTTP.CR, HTTP.LF};
    protected static final Charset ASCII = Charset.forName("US-ASCII");

    protected Socket socket;
    protected HttpTransportMetricsImpl metrics;
    protected OutputStream out;
    protected Charset charset;
    protected boolean ascii;
    protected CharsetEncoder encoder;
    protected CodingErrorAction onMalformedInputAction;
    protected CodingErrorAction onUnMappableInputAction;
    protected ByteBuffer bbuf;

    public RecordingSocketOutputBuffer(Socket socket, int buffersize, HttpParams params) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket may not be null");
        }
        this.socket = socket;
        this.metrics = new HttpTransportMetricsImpl();

        this.charset = Charset.forName(HttpProtocolParams.getHttpElementCharset(params));
        this.ascii = this.charset.equals(ASCII);
        this.onMalformedInputAction = HttpProtocolParams.getMalformedInputAction(params);
        this.onUnMappableInputAction = HttpProtocolParams.getUnmappableInputAction(params);

        Recorder recorder = Recorder.getHttpRecorder();
        Recorder httpRecorder = Recorder.getHttpRecorder();
        if (httpRecorder == null) {   // XXX || (isSecure() && isProxied())) {
            // no recorder, OR defer recording for pre-tunnel leg
             this.out = new BufferedOutputStream(socket.getOutputStream(), buffersize);
        } else {
            this.out = recorder.outputWrap(new BufferedOutputStream(socket.getOutputStream(), buffersize));
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            return;
        }
        out.write(b, off, len);
        metrics.incrementBytesTransferred(len - off);
    }

    @Override
    public void write(byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        out.write(b);
        metrics.incrementBytesTransferred(b.length);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        metrics.incrementBytesTransferred(1);
    }

    @Override
    public void writeLine(String s) throws IOException {
        if (s == null) {
            return;
        }
        if (s.length() > 0) {
            if (this.ascii) {
                for (int i = 0; i < s.length(); i++) {
                    write(s.charAt(i));
                }
            } else {
                CharBuffer cbuf = CharBuffer.wrap(s);
                writeEncoded(cbuf);
            }
        }
        write(CRLF);

    }

    // copied verbatim from org.apache.http.impl.io.AbstractSessionOutputBuffer
    protected void writeEncoded(final CharBuffer cbuf) throws IOException {
        if (!cbuf.hasRemaining()) {
            return;
        }
        if (this.encoder == null) {
            this.encoder = this.charset.newEncoder();
            this.encoder.onMalformedInput(this.onMalformedInputAction);
            this.encoder.onUnmappableCharacter(this.onUnMappableInputAction);
        }
        if (this.bbuf == null) {
            this.bbuf = ByteBuffer.allocate(1024);
        }
        this.encoder.reset();
        while (cbuf.hasRemaining()) {
            CoderResult result = this.encoder.encode(cbuf, this.bbuf, true);
            handleEncodingResult(result);
        }
        CoderResult result = this.encoder.flush(this.bbuf);
        handleEncodingResult(result);
        this.bbuf.clear();
    }

    // copied verbatim from org.apache.http.impl.io.AbstractSessionOutputBuffer
    protected void handleEncodingResult(final CoderResult result) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
        this.bbuf.flip();
        while (this.bbuf.hasRemaining()) {
            write(this.bbuf.get());
        }
        this.bbuf.compact();
    }


    @Override
    public void writeLine(CharArrayBuffer charbuffer) throws IOException {
        if (charbuffer == null) {
            return;
        }
        if (this.ascii) {
            for (int i = 0; i < charbuffer.length(); i++) {
                write(charbuffer.charAt(i));
            }
        } else {
            // XXX why is this wrapped in AbstractSessionOutputBuffer? copied from there
            CharBuffer cbuf = CharBuffer.wrap(charbuffer.buffer(), 0, charbuffer.length());
            writeEncoded(cbuf);
        }
        write(CRLF);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return metrics;
    }
}
