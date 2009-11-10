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
