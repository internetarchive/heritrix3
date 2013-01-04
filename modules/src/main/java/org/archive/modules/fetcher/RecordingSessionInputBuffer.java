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
import java.nio.charset.CharsetDecoder;

import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.Args;
import org.apache.http.util.CharArrayBuffer;
import org.archive.util.Recorder;

class RecordingSessionInputBuffer extends SessionInputBufferImpl {

    protected int buffersize;

    public RecordingSessionInputBuffer(HttpTransportMetricsImpl metrics,
            int buffersize, int minChunkLimit, MessageConstraints constraints,
            CharsetDecoder chardecoder) {
        super(metrics, buffersize, minChunkLimit, constraints, chardecoder);
        this.buffersize = buffersize;
    }

    @Override
    public void bind(InputStream inputStream) throws IOException {
        if (inputStream != null) {
            Recorder recorder = Recorder.getHttpRecorder();
            BufferedInputStream bin = new BufferedInputStream(inputStream, buffersize);
            if (recorder == null) {   // XXX || (isSecure() && isProxied())) {
                super.bind(bin);
            } else {
                InputStream rin = recorder.inputWrap(bin);
                super.bind(rin);
            }
        } else {
            super.bind(null);
        }
    }

    @Override
    public int fillBuffer() throws IOException {
        throw new RuntimeException("don't use me");
    }

    @Override
    public int readLine(CharArrayBuffer charbuffer) throws IOException {
        Args.notNull(charbuffer, "Char array buffer");
        int bytesRead = 0;
        int b = instream.read();
        while (b >= 0 && b != HTTP.LF) {
            bytesRead++;
            linebuffer.append(b);
            b = instream.read();
        }
        if (b >= 0) {
            bytesRead++; // count LF
        }
        
        // if line ends with CR-LF, get rid of the CR
        if (bytesRead > 0 && linebuffer.byteAt(linebuffer.length() - 1) == HTTP.CR) {
            linebuffer.setLength(linebuffer.length() - 1);
        }

        if (bytesRead > 0) {
            metrics.incrementBytesTransferred(bytesRead);
        }
        
        if (bytesRead == 0 && b == -1) {
            // indicate the end of stream
            return -1;
        }

        return lineFromLineBuffer(charbuffer);
    }
    
}