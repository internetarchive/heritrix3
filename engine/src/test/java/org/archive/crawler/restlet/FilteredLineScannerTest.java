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

package org.archive.crawler.restlet;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class FilteredLineScannerTest {
    static final Predicate<String> ALL = s -> true;

    @TempDir
    File tempDir;

    File write(String content) throws Exception {
        File f = new File(tempDir, "test.log");
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    static FilteredLineScanner.Result scan(File f, long pos, int count,
            Predicate<String> filter) throws Exception {
        try (LogSeries source = LogSeries.single(f)) {
            return FilteredLineScanner.scan(source, pos, count, filter);
        }
    }

    @Test
    public void testForward() throws Exception {
        File f = write("a1\nb2\na3\nb4\na5\n");
        FilteredLineScanner.Result r = scan(f, 0, 2, s -> s.startsWith("a"));
        assertEquals(List.of("a1", "a3"), r.lines);
        assertEquals(0, r.range.getMinimum());
        assertEquals(9, r.range.getMaximum());

        // continue from the end of the previous range
        r = scan(f, r.range.getMaximum(), 2, s -> s.startsWith("a"));
        assertEquals(List.of("a5"), r.lines);
        assertEquals(f.length(), r.range.getMaximum());
    }

    @Test
    public void testForwardFromMidLine() throws Exception {
        File f = write("a1\nb2\na3\n");
        // position 4 is inside "b2"; scanning starts at its beginning
        FilteredLineScanner.Result r = scan(f, 4, 10, ALL);
        assertEquals(List.of("b2", "a3"), r.lines);
        assertEquals(3, r.range.getMinimum());
    }

    @Test
    public void testBackward() throws Exception {
        File f = write("a1\nb2\na3\nb4\na5\n");
        FilteredLineScanner.Result r = scan(f, -1, -2, s -> s.startsWith("a"));
        assertEquals(List.of("a3", "a5"), r.lines);
        assertEquals(6, r.range.getMinimum());
        assertEquals(f.length(), r.range.getMaximum());

        // continue from just before the previous range, as 'earlier' does
        r = scan(f, r.range.getMinimum() - 1, -2, s -> s.startsWith("a"));
        assertEquals(List.of("a1"), r.lines);
        assertEquals(0, r.range.getMinimum());
        assertEquals(6, r.range.getMaximum());
    }

    @Test
    public void testNoTrailingNewline() throws Exception {
        File f = write("a1\nb2\na3");
        assertEquals(List.of("a1", "b2", "a3"), scan(f, 0, 10, ALL).lines);
        assertEquals(List.of("b2", "a3"), scan(f, -1, -2, ALL).lines);
        assertEquals(List.of("a3"), scan(f, f.length(), -1, ALL).lines);
    }

    @Test
    public void testCrLfAndUtf8() throws Exception {
        File f = write("café ☃\r\nnaïve\r\n");
        assertEquals(List.of("café ☃", "naïve"), scan(f, 0, 10, ALL).lines);
        assertEquals(List.of("café ☃", "naïve"), scan(f, -1, -10, ALL).lines);
    }

    @Test
    public void testEmptyFile() throws Exception {
        File f = write("");
        assertTrue(scan(f, 0, 10, ALL).lines.isEmpty());
        assertTrue(scan(f, -1, -10, ALL).lines.isEmpty());
    }

    @Test
    public void testPastEnd() throws Exception {
        File f = write("a1\nb2\n");
        FilteredLineScanner.Result r = scan(f, f.length(), 10, ALL);
        assertTrue(r.lines.isEmpty());
        assertEquals(f.length(), r.range.getMinimum());
    }

    @Test
    public void testLinesLongerThanChunk() throws Exception {
        StringBuilder sb = new StringBuilder();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String line = i + "x".repeat(FilteredLineScanner.CHUNK_SIZE + 17 * i);
            expected.add(line);
            sb.append(line).append('\n');
        }
        for (int i = 0; i < 3000; i++) {
            String line = "short " + i;
            expected.add(line);
            sb.append(line).append('\n');
        }
        File f = write(sb.toString());
        assertEquals(expected, scan(f, 0, 100000, ALL).lines);
        assertEquals(expected, scan(f, -1, -100000, ALL).lines);
        assertEquals(List.of("short 2999"), scan(f, -1, -1, ALL).lines);
        assertEquals(expected.subList(0, 2), scan(f, 0, 2, ALL).lines);
    }

    @Test
    public void testLongLinesAreTruncated() throws Exception {
        int max = FilteredLineScanner.MAX_LINE_LENGTH;
        // exactly max bytes plus a CRLF terminator is not truncated
        String exact = "e".repeat(max);
        // truncation backs off rather than splitting the 2-byte 'é'
        String split = "s".repeat(max - 1) + "\u00e9" + "tail";
        // much longer than max, and longer than a chunk past it
        String huge = "h" + "x".repeat(max * 3) + "needle";
        File f = write("first\n" + exact + "\r\n" + split + "\n" + huge
                + "\nlast\n");
        for (int count : new int[] {100, -100}) {
            FilteredLineScanner.Result r = scan(f, count > 0 ? 0 : -1, count, ALL);
            assertEquals(List.of("first", exact, "s".repeat(max - 1),
                    "h" + "x".repeat(max - 1), "last"), r.lines);
            assertEquals(f.length(), r.range.getMaximum());
        }
        // text past the limit can't be matched
        assertEquals(0, scan(f, 0, 100, s -> s.contains("needle")).lines.size());
    }

    @Test
    public void testHugeLineWithoutNewlines() throws Exception {
        // larger than CHUNK_SIZE and MAX_LINE_LENGTH by a wide margin, but
        // returned as a bounded string
        byte[] bytes = new byte[8 * 1024 * 1024];
        java.util.Arrays.fill(bytes, (byte) 'a');
        File f = new File(tempDir, "huge.log");
        Files.write(f.toPath(), bytes);
        FilteredLineScanner.Result r = scan(f, 0, 10, ALL);
        assertEquals(1, r.lines.size());
        assertEquals(FilteredLineScanner.MAX_LINE_LENGTH, r.lines.get(0).length());
        assertEquals(bytes.length, r.range.getMaximum());
        r = scan(f, -1, -10, ALL);
        assertEquals(1, r.lines.size());
        assertEquals(0, r.range.getMinimum());
    }
}
