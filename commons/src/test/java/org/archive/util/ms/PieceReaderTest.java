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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import org.archive.io.ArraySeekInputStream;
import org.archive.io.SafeSeekInputStream;
import org.archive.io.SeekInputStream;

import junit.framework.TestCase;


/**
 * Unit test for PieceReader.  Takes a quatrain of a sonnet and stores the
 * lines out-of-order, then constructs a PieceTable that will re-order the
 * lines correctly.  Finally constructs a PieceReader with that raw data
 * and piece table and sees if the correct quatrain is produced by the
 * stream.  Also performs some tests of random seeking within the stream.
 * 
 * @author pjack
 */
public class PieceReaderTest extends TestCase {

    
    final private static String[] QUATRAIN = new String[] { 
        "If the dull substance of my flesh were thought\n",
        "Injurious distance could not stop my way\n",
        "For then, despite of space, I would be brought\n",
        "From limits far remote where thou dost stay.\n"
    };
    
    
    final private static String QUATRAIN_STRING = 
        QUATRAIN[0] + QUATRAIN[1] + QUATRAIN[2] + QUATRAIN[3];
    
    final private static byte[] QUATRAIN_BYTES;
    final private static byte[] PIECE_TABLE;
    
    
    
    public void testPosition() throws Exception {
        PieceTable table = makePieceTable();
        SeekInputStream asis = new ArraySeekInputStream(QUATRAIN_BYTES);
        asis = new SafeSeekInputStream(asis);
        PieceReader reader = new PieceReader(table, asis);
        StringBuilder sb = new StringBuilder();
        for (int ch = reader.read(); ch > 0; ch = reader.read()) {
            sb.append((char)ch);
        }
        assertEquals(QUATRAIN_STRING, sb.toString());
        
        reader.position(0);
        sb = new StringBuilder();
        for (int ch = reader.read(); ch > 0; ch = reader.read()) {
            sb.append((char)ch);
        }
        assertEquals(QUATRAIN_STRING, sb.toString());
        
        Random random = new Random();
        for (int i = 0; i < 1000; i++) {
            int index = random.nextInt(QUATRAIN_BYTES.length);
            reader.position(index);
            char ch = (char)reader.read();
            assertEquals(QUATRAIN_STRING.charAt(index), ch);
        }
    }


    private static PieceTable makePieceTable() throws IOException {
        ArraySeekInputStream stream = new ArraySeekInputStream(PIECE_TABLE);
        int maxSize = QUATRAIN_BYTES.length;
        return new PieceTable(stream, 0, maxSize, 4);
    }
    
    
    static {
        QUATRAIN_BYTES = new byte[QUATRAIN_STRING.length()];
        PIECE_TABLE = new byte[4 * 12 + 5 + 4];
        int ofs = 0;
        int line3 = 0;
        ofs += addLine(ofs, QUATRAIN[2]);
        int line1 = ofs;
        ofs += addLine(ofs, QUATRAIN[0]);
        int line4 = ofs;
        ofs += addLine(ofs, QUATRAIN[3]);
        int line2 = ofs;
        ofs += addLine(ofs, QUATRAIN[1]);
        
        int start = 0;
        int end = QUATRAIN[0].length();
        addPiece(0, start, end, line1);
        
        start += QUATRAIN[0].length();
        end += QUATRAIN[1].length();
        addPiece(1, start, end, line2);

        start += QUATRAIN[1].length();
        end += QUATRAIN[2].length();
        addPiece(2, start, end, line3);

        start += QUATRAIN[2].length();
        end += QUATRAIN[3].length();
        addPiece(3, start, end, line4);
        
        ByteBuffer buf = ByteBuffer.wrap(PIECE_TABLE);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(0, (byte)2);
        buf.putInt(1, 52);
    }
    
    
    private static int addLine(int ofs, String line) {
        for (int i = 0; i < line.length(); i++) {
            QUATRAIN_BYTES[ofs + i] = (byte)line.charAt(i);
        }
        return line.length();
    }
    
    
    private static void addPiece(int index, int start, int end, int fp) {
        ByteBuffer buf = ByteBuffer.wrap(PIECE_TABLE);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int orig = fp;
        fp = (fp * 2) | PieceTable.CP1252_INDICATOR;
        if ((fp & PieceTable.CP1252_MASK) / 2 != orig) {
            throw new RuntimeException("No.");
        }
        buf.putInt(index * 4 + 5, start);
        buf.putInt(5 + 20 + index * 8 + 2, fp);
    }
}
