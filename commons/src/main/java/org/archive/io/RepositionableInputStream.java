/* RepositionableInputStream.java
 *
 * $Id$
 *
 * Created Dec 20, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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

import it.unimi.dsi.fastutil.io.RepositionableStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Wrapper around an {@link InputStream} to make a primitive Repositionable
 * stream. Uses a {@link BufferedInputStream}.  Calls mark on every read so
 * we'll remember at least the last thing read (You can only backup on the
 * last thing read -- not last 2 or 3 things read).  Used by
 * {@link GzippedInputStream} when reading streams over a network.  Wraps a
 * HTTP, etc., stream so we can back it up if needs be after the
 * GZIP inflater has done a fill of its full buffer though it only needed
 * the first few bytes to finish decompressing the current GZIP member.
 * 
 * <p>TODO: More robust implementation.  Tried to use the it.unimi.dsi.io
 * FastBufferdInputStream but relies on FileChannel ByteBuffers and if not
 * present -- as would be the case reading from a network stream, the main
 * application for this instance -- then it expects the underlying stream 
 * implements RepositionableStream interface so chicken or egg problem.
 * @author stack
 */
public class RepositionableInputStream extends BufferedInputStream implements
        RepositionableStream {
    private long position = 0;
    private long markPosition = -1;
    
    public RepositionableInputStream(InputStream in) {
        super(in);
    }
    
    public RepositionableInputStream(InputStream in, int size) {
        super(in, size);
    }

    public int read(byte[] b) throws IOException {
        int read = super.read(b);
        if (read != -1) {
            position += read;
        }
        return read;
    }
    
    public synchronized int read(byte[] b, int offset, int ct)
    throws IOException {
        // Mark the underlying stream so that we'll remember what we are about
    	// to read unless a mark has been set in this RepositionableStream
    	// (We have two levels of mark).  In this latter case we want the
    	// underlying stream to preserve its mark position so aligns with
    	// this RS when eset is called.
    	if (!isMarked()) {
    		super.mark((ct > offset)? ct - offset: ct);
    	}
        int read = super.read(b, offset, ct);
        if (read != -1) {
            position += read;
        }
        return read;
    }
    
    public int read() throws IOException {
        // Mark the underlying stream so that we'll remember what we are about
    	// to read unless a mark has been set in this RepositionableStream
    	// (We have two levels of mark).  In this latter case we want the
    	// underlying stream to preserve its mark position so aligns with
    	// this RS when eset is called.
    	if (!isMarked()) {
    		super.mark(1);
    	}
        int c = super.read();
        if (c != -1) {
            position++;
        }
        return c;
    }

    public void position(final long offset) {
        if (this.position == offset) {
            return;
        }
        int diff =  (int)(offset - this.position);
        long lowerBound = this.position - this.pos;
        long upperBound = lowerBound + this.count;
        if (offset < lowerBound || offset >= upperBound) {
            throw new IllegalAccessError("Offset goes outside " +
                "current this.buf (TODO: Do buffer fills if positive)");
        }
        this.position = offset;
        this.pos += diff;
        // Clear any mark.
        this.markPosition = -1;
    }

    public void mark(int readlimit) {
        this.markPosition = this.position;
        super.mark(readlimit);
    }

    public void reset() throws IOException {
        super.reset();
        this.position = this.markPosition;
        this.markPosition = -1;
    }
    
    protected boolean isMarked() {
    	return this.markPosition != -1;
    }

    public long position() {
        return this.position;
    }
}