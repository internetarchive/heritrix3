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

import java.io.IOException;

import org.apache.http.io.HttpTransportMetrics;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.util.CharArrayBuffer;

class RecordingSessionInputBuffer implements SessionInputBuffer {

    protected SessionInputBuffer wrapped;

    public RecordingSessionInputBuffer(SessionInputBuffer wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return wrapped.read(b, off, len);
    }

    @Override
    public int read(byte[] b) throws IOException {
        return wrapped.read(b);
    }

    @Override
    public int read() throws IOException {
        return wrapped.read();
    }

    @Override
    public int readLine(CharArrayBuffer buffer) throws IOException {
        return wrapped.readLine(buffer);
    }

    @Override
    public String readLine() throws IOException {
        return wrapped.readLine();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isDataAvailable(int timeout) throws IOException {
        return wrapped.isDataAvailable(timeout);
    }

    @Override
    public HttpTransportMetrics getMetrics() {
        return wrapped.getMetrics();
    }

//    @Override
//    public boolean isBound() {
//        return wrapped.isBound();
//    }
//
//    @Override
//    public void bind(InputStream inputStream) {
//        wrapped.bind(inputStream);
//    }
//
//    @Override
//    public boolean hasBufferedData() {
//        return wrapped.hasBufferedData();
//    }
//
//    @Override
//    public int fillBuffer() throws IOException {
//        return wrapped.fillBuffer();
//    }
}