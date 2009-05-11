/* PieceReader
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
package org.archive.util.ms;

import java.io.IOException;

import org.archive.io.Endian;
import org.archive.io.SeekInputStream;
import org.archive.io.SeekReader;


class PieceReader extends SeekReader {


    private PieceTable table;
    private SeekInputStream doc;
    
    private boolean unicode;
    private int charPos;
    private int limit;


    public PieceReader(PieceTable table, SeekInputStream doc)
    throws IOException {
        this.table = table;
        this.doc = doc;
        charPos = 0;
        limit = -1;
    }


    private void seekIfNecessary() throws IOException {
        if (doc == null) {
            throw new IOException("Stream closed.");
        }
        if (charPos >= table.getMaxCharPos()) {
            return;
        }
        if (charPos < limit) {
            return;
        }
        Piece piece = table.next();
        unicode = piece.isUnicode();
        limit = piece.getCharPosLimit();
        doc.position(piece.getFilePos());
    }


    public int read() throws IOException {
        seekIfNecessary();
        if (doc == null) {
            throw new IOException("Stream closed.");
        }
        if (charPos >= table.getMaxCharPos()) {
            return -1;
        }

        int ch;
        if (unicode) {
            ch = Endian.littleChar(doc);
        } else {
            ch = Cp1252.decode(doc.read());
        }
        charPos++;
        return ch;
    }


    public int read(char[] buf, int ofs, int len) throws IOException {
        // FIXME: Think of a faster implementation that will work with
        // both unicode and non-unicode.
        seekIfNecessary();
        if (doc == null) {
            throw new IOException("Stream closed.");
        }
        if (charPos >= table.getMaxCharPos()) {
            return 0;
        }
        for (int i = 0; i < len; i++) {
            int ch = read();
            if (ch < 0) {
                return i;
            }
            buf[ofs + i] = (char)ch;
        }
        return len;
    }
    
    
    public void close() throws IOException {
        doc.close();
        table = null;
    }
    
    
    public long position() throws IOException {
        return charPos;
    }
    
    
    public void position(long p) throws IOException {
        if (p > Integer.MAX_VALUE) {
            throw new IOException("File too large.");
        }
        int charPos = (int)p;
        Piece piece = table.pieceFor(charPos);
        if (piece == null) {
            throw new IOException("Illegal position: " + p);
        }
        unicode = piece.isUnicode();
        limit = piece.getCharPosLimit();
        
        int ofs = charPos - piece.getCharPosStart();
        this.charPos = charPos;
        doc.position(piece.getFilePos() + ofs);
    }
}
