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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.BufferedSeekInputStream;
import org.archive.io.Endian;
import org.archive.io.OriginSeekInputStream;
import org.archive.io.SafeSeekInputStream;
import org.archive.io.SeekInputStream;


/**
 * The piece table of a .doc file.  
 * 
 * <p>The piece table maps logical character positions of a document's text
 * stream to actual file stream positions.  The piece table is stored as two
 * parallel arrays.  The first array contains 32-bit integers representing
 * the logical character positions.  The second array contains 64-bit data
 * structures that are mostly mysterious to me, except that they contain a
 * 32-bit subfile offset.  The second array is stored immediately after the
 * first array.  I call the first array the <i>charPos</i> array and the 
 * second array the <i>filePos</i> array.
 * 
 * <p>The arrays are preceded by a special tag byte (2), followed by the
 * combined size of both arrays in bytes.  The number of piece table entries 
 * must be deduced from this byte size.  
 * 
 * <p>Because of this bizarre structure, caching piece table entries is 
 * something of a challenge.  A single piece table entry is actually located
 * in two different file locations.  If there are many piece table entries,
 * then the charPos and filePos information may be separated by many bytes,
 * potentially crossing block boundaries.  The approach I took was to use
 * two different buffered streams.  Up to n charPos offsets and n filePos
 * structures can be buffered in the two streams, preventing any file seeking
 * from occurring when looking up piece information.  (File seeking must 
 * still occur to jump from one piece to the next.)
 * 
 * <p>Note that the vast majority of .doc files in the world will have exactly
 * 1 piece table entry, representing the complete text of the document.  Only
 * those documents that were "fast-saved" should have multiple pieces.
 * 
 * <p>Finally, the text contained in a .doc file can either contain 16-bit
 * unicode characters (charset UTF-16LE) or 8-bit CP1252 characters.  One
 * .doc file can contain both kinds of pieces.  Whether or not a piece is
 * Cp1252 is stored as a flag in the filePos value, bizarrely enough.  If
 * the flag is set, then the actual file position is the filePos with the
 * flag cleared, then divided by 2.
 * 
 * @author pjack
 */
class PieceTable {

    private final static Logger LOGGER
     = Logger.getLogger(PieceTable.class.getName());

    /** The bit that indicates if a piece uses Cp1252 or unicode. */
    protected final static int CP1252_INDICATOR = 1 << 30;
    
    /** The mask to use to clear the Cp1252 flag bit. */
    protected final static int CP1252_MASK = ~(3 << 30);

    /** The total number of pieces in the table. */
    private int count;
    
    /** The total number of characters in the text stream. */
    private int maxCharPos;

    /** The index of the current piece. */
    private int current;
    
    /** The most recently returned piece from this table. */
    private Piece currentPiece;


    /** The buffered stream that provides character position information. */
    private SeekInputStream charPos;
    
    /** The buffered stream that provides file pointer information. */
    private SeekInputStream filePos;


    /**
     * Constructor.
     * 
     * @param tableStream   the stream containing the piece table
     * @param offset        the starting offset of the piece table
     * @param maxCharPos     the total number of characters in the document
     * @param cachedRecords  the number of piece table entries to cache
     * @throws IOException   if an IO error occurs
     */
    public PieceTable(SeekInputStream tableStream, int offset, 
            int maxCharPos, int cachedRecords) throws IOException {
        tableStream.position(offset);
        skipProperties(tableStream);
        int sizeInBytes = Endian.littleInt(tableStream);
        this.count = (sizeInBytes - 4) / 12;
        cachedRecords = Math.min(cachedRecords, count);
        long tp = tableStream.position() + 4;
        long charPosStart = tp;
        long filePosStart = tp + count * 4 + 2;
        
        this.filePos = wrap(tableStream, filePosStart, cachedRecords * 8);
        this.charPos = wrap(tableStream, charPosStart, cachedRecords * 4);
        this.maxCharPos = maxCharPos;
        
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Size in bytes: " + sizeInBytes);
            LOGGER.finest("Piece table count: " + count);
            for (Piece piece = next(); piece != null; piece = next()) {
                LOGGER.finest("#" + current + ": " + piece.toString());
            }
            current = 0;
        }
    }
    
    
    /**
     * Wraps the raw table stream.  This is used to create the charPos and
     * filePos streams.  The streams that this method returns are "safe",
     * meaning that the charPos and filePos position() fields never clobber
     * each other.  They are buffered, meaning that up to <i>n</i> elements
     * can be read before the disk is accessed again.  And they are "origined",
     * meaning result.position(0) actually positions the stream at the 
     * beginning of the piece table array, not the beginning of the file.
     * 
     * @param input   the stream to wrap
     * @param pos     the origin for the returned stream
     * @param cache   the number of bytes for the returned stream to buffer
     * @return   the wrapped stream
     * @throws IOException  if an IO error occurs
     */
    private SeekInputStream wrap(SeekInputStream input, long pos, int cache) 
    throws IOException {
        input.position(pos);
        SeekInputStream r = new SafeSeekInputStream(input);
        r = new OriginSeekInputStream(r, pos);
        r = new BufferedSeekInputStream(r, cache);
        return r;
    }
    
    
    /**
     * Skips over any property information that may precede a piece table.
     * These property structures contain stylesheet information that applies
     * to the piece table.  Since we're only interested in the text itself,
     * we just ignore this property stuff.  (I suppose a third buffered
     * stream could be used to add style information to {@link Piece}, but
     * we don't need it.)
     * 
     * @param input  the input stream containing the piece table
     * @throws IOException  if an IO error occurs
     */
    private static void skipProperties(SeekInputStream input) throws IOException {
        int tag = input.read();
        while (tag == 1) {
            int size = Endian.littleChar(input);
            while (size > 0) {
                size -= input.skip(size);
            }
            tag = input.read();
        }
        if (tag != 2) {
            throw new IllegalStateException();
        }
    }


    /**
     * Returns the maximum character position.  Put another way, returns the
     * total number of characters in the document.
     * 
     * @return  the maximum character position
     */
    public int getMaxCharPos() {
        return maxCharPos;
    }


    /**
     * Returns the next piece in the piece table.
     * 
     * @return  the next piece in the piece table, or null if there is no 
     *   next piece
     * @throws IOException  if an IO error occurs
     */
    public Piece next() throws IOException {
        if (current >= count) {
            currentPiece = null;
            return null;
        }
                
        int cp;
        if (current == count - 1) {
            cp = maxCharPos;
        } else {
            charPos.position(current * 4);
            cp = Endian.littleInt(charPos);
        }
        filePos.position(current * 8);
        int encoded = Endian.littleInt(filePos);

        if (LOGGER.isLoggable(Level.FINEST)) {
            StringBuffer sb = new StringBuffer(Integer.toBinaryString(encoded));
            while (sb.length() < 32) {
                sb.insert(0, '0');
            }
            LOGGER.finest("Encoded offset: " + sb.toString());
        }
        
        current++;

        int start;
        if (currentPiece == null) {
            start = 0;
        } else {
            start = currentPiece.getCharPosLimit();
        }
        if ((encoded & CP1252_INDICATOR) == 0) {
            Piece piece = new Piece(encoded, start, cp, true);
            currentPiece = piece;
            return piece;
        } else {
            int filePos = (encoded & CP1252_MASK) / 2;
            Piece piece = new Piece(filePos, start, cp, false);
            currentPiece = piece;
            return piece;
        }
    }

    
    /**
     * Returns the piece containing the given character position.
     * 
     * @param charPos   the character position whose piece to return
     * @return   that piece, or null if no such piece exists (if charPos 
     *   is greater than getMaxCharPos())
     * @throws IOException   if an IO error occurs
     */
    public Piece pieceFor(int charPos) throws IOException {
        if (currentPiece.contains(charPos)) {
            return currentPiece;
        }
     
        // FIXME: Use binary search to find piece index
        
        current = 0;
        currentPiece = null;
        next();
        
        while (currentPiece != null) {
            if (currentPiece.contains(charPos)) {
                return currentPiece;
            }
            next();
        }
        
        return null;
    }

}
