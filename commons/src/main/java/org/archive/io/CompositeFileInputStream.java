/* CompositeFileInputStream
*
* $Id$
*
* Created on May 18, 2004
*
* Copyright (C) 2004 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
