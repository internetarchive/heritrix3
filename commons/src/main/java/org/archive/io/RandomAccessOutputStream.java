/* Copyright (C) 2003 Internet Archive.
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
 *
 * RandomAccessOutputStream.java
 * Created on May 21, 2004
 *
 * $Header$
 */
package org.archive.io;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;


/**
 * Wraps a RandomAccessFile with OutputStream interface.
 *
 * @author gojomo
 */
public class RandomAccessOutputStream extends OutputStream {
    RandomAccessFile raf;

    /**
     * Wrap the given RandomAccessFile
     */
    public RandomAccessOutputStream(RandomAccessFile raf) {
        super();
        this.raf = raf;
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#write(int)
     */
    public void write(int b) throws IOException {
        raf.write(b);
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#close()
     */
    public void close() throws IOException {
        raf.close();
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#write(byte[], int, int)
     */
    public void write(byte[] b, int off, int len) throws IOException {
        raf.write(b, off, len);
    }

    /* (non-Javadoc)
     * @see java.io.OutputStream#write(byte[])
     */
    public void write(byte[] b) throws IOException {
        raf.write(b);
    }
}
