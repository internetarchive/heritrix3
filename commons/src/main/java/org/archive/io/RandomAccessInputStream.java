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
import java.io.IOException;
import java.io.RandomAccessFile;


/**
 * Wraps a RandomAccessFile with an InputStream interface.
 *
 * @author gojomo
 */
public class RandomAccessInputStream extends SeekInputStream {
    
    /**
     * Reference to the random access file this stream is reading from.
     */
    private RandomAccessFile raf = null;
    
    /**
     * When mark is called, save here the current position so we can go back
     * on reset.
     */
    private long markpos = -1;

    /**
     * True if we are to close the underlying random access file when this
     * stream is closed.
     */
    private boolean sympathyClose;

    /**
     * Constructor.
     * 
     * If using this constructor, caller created the RAF and therefore
     * its assumed wants to control close of the RAF.  The RAF.close
     * is not called if this constructor is used on close of this stream.
     * 
     * @param raf RandomAccessFile to wrap.
     * @throws IOException
     */
    public RandomAccessInputStream(RandomAccessFile raf)
    throws IOException {
        this(raf, false, 0);
    }
    
    /**
     * Constructor.
     * 
     * @param file File to get RAFIS on.  Creates an RAF from passed file.
     * Closes the created RAF when this stream is closed.
     * @throws IOException 
     */
    public RandomAccessInputStream(final File file)
    throws IOException {
        this(new RandomAccessFile(file, "r"), true, 0);
    }
    
    /**
     * Constructor.
     * 
     * @param file File to get RAFIS on.  Creates an RAF from passed file.
     * Closes the created RAF when this stream is closed.
     * @param offset 
     * @throws IOException 
     */
    public RandomAccessInputStream(final File file, final long offset)
    throws IOException {
        this(new RandomAccessFile(file, "r"), true, offset);
    }
    
    /**
     * @param raf RandomAccessFile to wrap.
     * @param sympathyClose Set to true if we are to close the RAF
     * file when this stream is closed.
     * @param offset 
     * @throws IOException
     */
    public RandomAccessInputStream(final RandomAccessFile raf,
            final boolean sympathyClose, final long offset)
    throws IOException {
        super();
        this.sympathyClose = sympathyClose;
        this.raf = raf;
        if (offset > 0) {
            this.raf.seek(offset);
        }
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#read()
     */
    public int read() throws IOException {
        return this.raf.read();
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[], int, int)
     */
    public int read(byte[] b, int off, int len) throws IOException {
        return this.raf.read(b, off, len);
    }
    
    /* (non-Javadoc)
     * @see java.io.InputStream#read(byte[])
     */
    public int read(byte[] b) throws IOException {
        return this.raf.read(b);
    }

    /* (non-Javadoc)
     * @see java.io.InputStream#skip(long)
     */
    public long skip(long n) throws IOException {
        this.raf.seek(this.raf.getFilePointer() + n);
        return n;
    }

	public long position() throws IOException {
		return this.raf.getFilePointer();
	}

	public void position(long position) throws IOException {
		this.raf.seek(position);
	}
    
	public int available() throws IOException {
        long amount = this.raf.length() - this.position();
        return (amount >= Integer.MAX_VALUE)? Integer.MAX_VALUE: (int)amount;
	}
	
    public boolean markSupported() {
        return true;
    }
    
    public synchronized void mark(int readlimit) {
        try {
            this.markpos = position();
        } catch (IOException e) {
            // Set markpos to -1. Will cause exception reset.
            this.markpos = -1;
        }
    }
    
    public synchronized void reset() throws IOException {
        if (this.markpos == -1) {
            throw new IOException("Mark has not been set.");
        }
        position(this.markpos);
    }
    
    public void close() throws IOException {
        try {
            super.close();
        } finally {
            if (this.sympathyClose) {
                this.raf.close();
            }
        }
    }
}