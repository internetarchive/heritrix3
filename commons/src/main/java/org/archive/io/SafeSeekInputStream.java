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


import java.io.IOException;


/**
 * Enables multiple concurrent streams based on the same underlying stream.
 * 
 * @author pjack
 */
public class SafeSeekInputStream extends SeekInputStream {


    /**
     * The underlying stream.
     */
    private SeekInputStream input;


    /**
     * The expected position of the underlying stream.
     */
    private long expected;


    /**
     * Constructor.  The given stream will be positioned to 0 so that an
     * accurate position can be tracked.
     * 
     * @param input  the underlying input stream
     * @throws IOException   if an IO error occurs
     */
    public SafeSeekInputStream(SeekInputStream input) throws IOException {
        this.input = input;
        this.expected = input.position();
    }


    /**
     * Ensures that the underlying stream's position is what we expect to be.
     * 
     * @throws IOException  if an IO error occurs
     */
    private void ensure() throws IOException {
        if (expected != input.position()) {
            input.position(expected);
        }
    }


    @Override
    public int read() throws IOException {
        ensure();
        int c = input.read();
        if (c >= 0) {
            expected++;
        }
        return c;
    }


    @Override
    public int read(byte[] buf, int ofs, int len) throws IOException {
        ensure();
        int r = input.read(buf, ofs, len);
        if (r > 0) {
            expected += r;
        }
        return r;
    }


    @Override
    public int read(byte[] buf) throws IOException {
        ensure();
        int r = input.read(buf);
        if (r > 0) {
            expected += r;
        }
        return r;
    }


    @Override
    public long skip(long c) throws IOException {
        ensure();
        long r = input.skip(c);
        if (r > 0) {
            expected += r;
        }
        return r;
    }
    
    
    public void position(long p) throws IOException {
        input.position(p);
        expected = p;
    }


    public long position() throws IOException {
        return expected;
    }

}
