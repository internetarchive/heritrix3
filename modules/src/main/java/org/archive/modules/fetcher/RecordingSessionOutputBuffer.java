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
import java.nio.charset.CharsetEncoder;

import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionOutputBufferImpl;
import org.archive.util.Recorder;

class RecordingSessionOutputBuffer extends SessionOutputBufferImpl {

    protected int buffersize;

    public RecordingSessionOutputBuffer(HttpTransportMetricsImpl metrics,
            int buffersize, int minChunkLimit, CharsetEncoder charencoder) {
        super(metrics, buffersize, minChunkLimit, charencoder);
        this.buffersize = buffersize;
    }

    @Override
    public void bind(OutputStream outstream) throws IOException {
        if (outstream != null) {
            Recorder recorder = Recorder.getHttpRecorder();
            BufferedOutputStream bout = new BufferedOutputStream(outstream, buffersize);
            if (recorder == null) {   // XXX || (isSecure() && isProxied())) {
                // no recorder, OR defer recording for pre-tunnel leg
                super.bind(bout);
            } else {
                OutputStream rout = recorder.outputWrap(bout);
                super.bind(rout);
            }
        } else {
            super.bind(null);
        }
    }

}