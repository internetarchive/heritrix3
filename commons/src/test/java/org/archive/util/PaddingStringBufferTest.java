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
package org.archive.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JUnit test suite for PaddingStringBuffer
 *
 * @author <a href="mailto:me@jamesc.net">James Casey</a>
 * @version $Id$
 */
public class PaddingStringBufferTest {

    @BeforeEach
    public void setUp() {
        buf = new PaddingStringBuffer();
    }

    /** first check that padTo works ok, since all depends on it */
    @Test
    public void testPadTo() {
        PaddingStringBuffer retBuf;
        assertEquals("", buf.toString(), "nothing in buffer");
        retBuf = buf.padTo(5);
        assertEquals(retBuf, buf, "retBuf same as buf");
        assertEquals("     ", buf.toString(), "5 spaces");

        // now do a smaller value - nothing should happen
        buf.padTo(4);
        assertEquals("     ", buf.toString(), "5 spaces");

        // now pad tro a greater length
        buf.padTo(10);
        assertEquals("          ", buf.toString(), "10 spaces");
    }

    /** test that append(String) works correctly */
    @Test
    public void testAppendString() {
        // a buf to hold the return buffer
        PaddingStringBuffer retBuf;
        assertEquals("", buf.toString(), "nothing in buffer");
        retBuf = buf.append("foo");
        assertEquals("foo", buf.toString(), "foo in buffer");
        assertEquals(retBuf.toString(), buf.toString(), "retBuf good");
        retBuf = buf.append("bar");
        assertEquals("foobar", buf.toString(), "foobar in buffer");
        assertEquals(retBuf.toString(), buf.toString(), "retBuf good");
    }

    /** check the reset method clears the buffer */
    @Test
    public void testReset() {
        // append something into the buffer
        assertEquals("", buf.toString(), "nothing in buffer");
        buf.append("foo");
        assertEquals("foo", buf.toString(), "buffer is 'foo'");
        buf.reset();
        assertEquals("", buf.toString(), "nothing in buffer after reset");
    }

    /** test the raAppend(String) works in the simple cases */
    @Test
    public void testRaAppend() {
        // a buf to hold the return buffer
        PaddingStringBuffer retBuf;
        assertEquals("", buf.toString(), "nothing in buffer");
        retBuf = buf.raAppend(5, "foo");
        assertEquals("  foo", buf.toString(), "foo in buffer");
        assertEquals(retBuf.toString(), buf.toString(), "retBuf good");
        retBuf = buf.raAppend(9, "bar");
        assertEquals("  foo bar", buf.toString(), "foobar in buffer");
        assertEquals(retBuf.toString(), buf.toString(), "retBuf good");

        // now check with out-of-range columns - should just append
        buf = new PaddingStringBuffer();
        buf.raAppend(-1, "foo");
        assertEquals("foo", buf.toString(), "no padding for -1");
        buf = new PaddingStringBuffer();
        buf.raAppend(0, "foo");
        assertEquals("foo", buf.toString(), "no padding for 0");

    }

    /** test the newline() */
    @Test
    public void testNewline(){
        assertEquals("", buf.toString(), "nothing should be in the buffer");
        buf.newline();
        assertTrue(buf.toString().indexOf('\n')!=-1, "should contain newline");
        assertEquals(0, (Object) buf.linePos, "line position should be 0");
    }

    /** check what happens when we right append, but the string is longer
     * than the space */
    @Test
    public void testRaAppendWithTooLongString() {
        buf.raAppend(3,"foobar");
        assertEquals("foobar", buf.toString(), "no padding when padding col less than string length");
        buf.reset();
    }

    /** check it all works with the length == the length of the string */
    @Test
    public void testRaAppendWithExactLengthString() {
        buf.raAppend(6, "foobar");
        buf.raAppend(12, "foobar");
        assertEquals("foobarfoobar", buf.toString(), "no padding with exact length string");
    }

    /** check that append(int) works */
    @Test
    public void testAppendInt() {
        buf.append((int)1);
        assertEquals("1", buf.toString(), "buffer is '1'");
        buf.append((int)234);
        assertEquals("1234", buf.toString(), "buffer is '1234'");
    }

    /** check that raAppend(int) works */
    @Test
    public void testRaAppendInt() {
        // right-append '1' to column 5
        buf.raAppend(5, (int)1);
        assertEquals("    1", buf.toString(), "buf is '    1'");
        // try appending a too-long int

        buf.raAppend(6,(int)123);
        assertEquals("    1123", buf.toString(), "'123' appended");
    }

    /** check that  append(long) works */
    @Test
    public void testAppendLong() {
        buf.append((long)1);
        assertEquals("1", buf.toString(), "buffer is '1'");
        buf.append((long)234);
        assertEquals("1234", buf.toString(), "buffer is '1234'");
    }

    /** check that raAppend(long) works */
    @Test
    public void testRaAppendLong() {
        // right-append '1' to column 5
        buf.raAppend(5, (long) 1);
        assertEquals("    1", buf.toString(), "buf is '    1'");
        // try appending a too-long int

        buf.raAppend(6, (long) 123);
        assertEquals("    1123", buf.toString(), "'123' appended");
    }

    /** a temp buffer for testing with */
    private PaddingStringBuffer buf;
}

