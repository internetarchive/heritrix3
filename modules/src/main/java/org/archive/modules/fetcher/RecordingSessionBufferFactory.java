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

import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.apache.http.config.MessageConstraints;
import org.apache.http.impl.io.HttpTransportMetricsImpl;
import org.apache.http.impl.io.SessionBufferImplFactory;
import org.apache.http.impl.io.SessionInputBufferImpl;
import org.apache.http.impl.io.SessionOutputBufferImpl;

public class RecordingSessionBufferFactory implements SessionBufferImplFactory {

    protected static final RecordingSessionBufferFactory INSTANCE = new RecordingSessionBufferFactory();
    
    @Override
    public SessionInputBufferImpl createInputBuffer(
            HttpTransportMetricsImpl metrics, int buffersize,
            int minChunkLimit, MessageConstraints constraints,
            CharsetDecoder chardecoder) {
        return new RecordingSessionInputBuffer(metrics, buffersize, minChunkLimit, constraints, chardecoder); 
    }

    @Override
    public SessionOutputBufferImpl createOutputBuffer(
            HttpTransportMetricsImpl metrics, int buffersize, int minChunkLimit,
            CharsetEncoder charencoder) {
        return new RecordingSessionOutputBuffer(metrics, buffersize, minChunkLimit, charencoder);
    }
}