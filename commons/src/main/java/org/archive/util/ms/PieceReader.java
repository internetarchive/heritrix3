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
