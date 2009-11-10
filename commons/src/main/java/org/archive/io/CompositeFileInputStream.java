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

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;


/**
 * @author gojomo
 */
public class CompositeFileInputStream extends FilterInputStream{
    Iterator<File> filenames;

    /* (non-Javadoc)
     * @see java.io.InputStream#read()
     */
    public int read() throws IOException {
        int c = super.read();
        if( c == -1 && filenames.hasNext() ) {
            cueStream();
            return read();
        }
        return c;
    }
    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public int read(byte[] b, int off, int len) throws IOException {
        int c = super.read(b, off, len);
        if( c == -1 && filenames.hasNext() ) {
            cueStream();
            return read(b,off,len);
        }
        return c;
    }
    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[])
     */
    public int read(byte[] b) throws IOException {
        int c = super.read(b);
        if( c == -1 && filenames.hasNext() ) {
            cueStream();
            return read(b);
        }
        return c;
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#skip(long)
     */
    public long skip(long n) throws IOException {
        long s = super.skip(n);
        if( s<n && filenames.hasNext() ) {
            cueStream();
            return s + skip(n-s);
        }
        return s;
    }

    /**
     * @param files
     * @throws IOException
     */
    public CompositeFileInputStream(List<File> files) throws IOException {
        super(null);
        filenames = files.iterator();
        cueStream();
    }

    private void cueStream() throws IOException {
        if(filenames.hasNext()) {
            this.in = new FileInputStream(filenames.next());
        }
    }

}
