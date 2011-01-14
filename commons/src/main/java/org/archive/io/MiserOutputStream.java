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

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A filter stream that both counts bytes written, and optionally swallows 
 * flush() requests. 
 * 
 * @contributor gojomo
 */
public class MiserOutputStream extends FilterOutputStream {
    protected long count;
    protected boolean passFlushes;
    
    /**
     * Wraps another output stream, counting the number of bytes written.
     *
     * @param out the output stream to be wrapped
     */
    public MiserOutputStream(OutputStream out) {
      this(out,true);
    }
    
    /**
     * Wraps another output stream, counting the number of bytes written.
     *
     * @param out the output stream to be wrapped
     */
    public MiserOutputStream(OutputStream out, boolean passFlushes) {
      super(out);
      this.passFlushes = passFlushes;
    }

    /** Returns the number of bytes written. */
    public long getCount() {
      return count;
    }

    @Override public void write(byte[] b, int off, int len) throws IOException {
      out.write(b, off, len);
      count += len;
    }

    @Override public void write(int b) throws IOException {
      out.write(b);
      count++;
    }

    @Override
    public void close() throws IOException {
        passFlushes = true; 
        super.close();
    }

    @Override
    public void flush() throws IOException {
        if(passFlushes) {
            super.flush();
        }
    } 
}
