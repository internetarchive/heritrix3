/* SeekInputStream
*
* Created on September 12, 2006
*
* Copyright (C) 2006 Internet Archive.
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
