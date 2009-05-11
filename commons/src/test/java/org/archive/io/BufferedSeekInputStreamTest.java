/* BufferedSeekInputStreamTest
*
* Created on September 18, 2006
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

import java.util.Random;

import junit.framework.TestCase;


/**
 * Unit test for BufferedSeekInputStream.  The tests do some random 
 * repositioning in the stream to make sure the buffer is always valid.
 * 
 * @author pjack
 */
public class BufferedSeekInputStreamTest extends TestCase {

    
    private static byte[] TEST_DATA = makeTestData();
    
    public void testPosition() throws Exception {
        Random random = new Random(); 
        ArraySeekInputStream asis = new ArraySeekInputStream(TEST_DATA);
        BufferedSeekInputStream bsis = new BufferedSeekInputStream(asis, 11);
        for (int i = 0; i < TEST_DATA.length; i++) {
            byte b = (byte)bsis.read();
            assertEquals(TEST_DATA[i], b);
        }
        for (int i = 0; i < 1000; i++) {
            int index = random.nextInt(TEST_DATA.length);
            bsis.position(index);
            char expected = (char)((int)TEST_DATA[index] & 0xFF);
            char read = (char)(bsis.read() & 0xFF);
            assertEquals(expected, read);
        }
    }    
    
    
    private static byte[] makeTestData() {
        String s = "If the dull substance of my flesh were thought\n"
         + "Injurious distance could not stop my way\n"
         + "For then, despite of space, I would be brought\n"
         + "From limits far remote where thou dost stay.\n";
        byte[] r = new byte[s.length()];
        for (int i = 0; i < r.length; i++) {
            r[i] = (byte)s.charAt(i);
//            r[i] = (byte)s.charAt(i);
        }
        return r;
    }
}
