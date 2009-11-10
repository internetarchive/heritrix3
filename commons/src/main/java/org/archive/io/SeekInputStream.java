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


import it.unimi.dsi.fastutil.io.RepositionableStream;

import java.io.IOException;
import java.io.InputStream;


/**
 * Base class for repositionable input streams.
 * 
 * @author pjack
 */
public abstract class SeekInputStream extends InputStream 
implements RepositionableStream {


    /**
     * The marked file position.  A value less than zero
     * indicates that no mark has been set.
     */
    private long mark = -1;


    /**
     * Marks the current position of the stream.  The limit parameter is
     * ignored; the mark will remain valid until reset is called or the
     * stream is closed.
     * 
     * @param limit  ignored
     */
    public void mark(int limit) {
        try {
            this.mark = position();
        } catch (IOException e) {
            mark = -1;
        }
    }


    /**
     * Resets this stream to its marked position.
     * 
     * @throws IOException  if there is no mark, or if an IO error occurs
     */
    public void reset() throws IOException {
        if (mark < 0) {
            throw new IOException("No mark.");
        }
        position(mark);
    }


    /**
     * Returns true, since SeekInputStreams support mark/reset by default.
     * 
     * @return true
     */
    public boolean markSupported() {
        return true;
    }
}
