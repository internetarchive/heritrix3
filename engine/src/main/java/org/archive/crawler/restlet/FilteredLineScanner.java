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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import org.apache.commons.lang3.LongRange;

/**
 * Scans a file forwards or backwards from a byte position, collecting lines
 * that match a filter, until enough matches are found or the start/end of
 * the file is reached.
 *
 * This keeps no state between requests: the caller continues by scanning
 * again from the edge of the returned range.
 */
public class FilteredLineScanner {
    protected static final int CHUNK_SIZE = 64 * 1024;
    /**
     * longest line returned, in bytes; longer lines are truncated so that a
     * file with few or no newlines can't exhaust the heap
     */
    protected static final int MAX_LINE_LENGTH = 256 * 1024;

    /** Result of a scan. */
    public static class Result {
        /** matching lines, in file order */
        public final List<String> lines;
        /** position of the start of each matching line */
        public final List<Long> lineStarts;
        /** byte range [start-of-first-line, past-end-of-last-line] scanned */
        public final LongRange range;

        Result(List<String> lines, List<Long> lineStarts, LongRange range) {
            this.lines = lines;
            this.lineStarts = lineStarts;
            this.range = range;
        }
    }

    protected final LogSeries source;
    protected final long length;
    protected final byte[] buf = new byte[CHUNK_SIZE];
    protected long bufStart = 0;
    protected int bufLen = 0;

    protected final Predicate<String> filter;
    protected final List<String> matches = new ArrayList<>();
    protected final List<Long> matchStarts = new ArrayList<>();

    protected FilteredLineScanner(LogSeries source, Predicate<String> filter) {
        this.source = source;
        this.length = source.length();
        this.filter = filter;
    }

    /**
     * Scan for matching lines.
     *
     * @param source file or files to scan
     * @param position byte position to scan from. When scanning forward,
     *        scanning starts at the beginning of the line containing this
     *        position; when scanning backward, it starts at the end of the
     *        line containing it. Negative means end of file.
     * @param lineCount number of matching lines wanted; positive to scan
     *        forward, negative to scan backward
     * @param filter returns true for lines to include
     */
    public static Result scan(LogSeries source, long position, int lineCount,
            Predicate<String> filter) throws IOException {
        FilteredLineScanner scanner = new FilteredLineScanner(source, filter);
        if (lineCount >= 0) {
            return scanner.forward(position, lineCount);
        } else {
            return scanner.backward(position, -lineCount);
        }
    }

    protected Result forward(long position, int wanted) throws IOException {
        if (position < 0 || position > length) {
            position = length;
        }
        long start = lineStart(position);
        long pos = start;
        while (pos < length && matches.size() < wanted) {
            long end = lineEnd(pos);
            test(readLine(pos, end), pos);
            pos = end;
        }
        return new Result(matches, matchStarts, LongRange.of(start, pos));
    }

    protected Result backward(long position, int wanted) throws IOException {
        if (length == 0) {
            return new Result(matches, matchStarts, LongRange.of(0L, 0L));
        }
        if (position < 0 || position >= length) {
            position = length - 1;
        }
        long end = lineEnd(position);
        long pos = end;
        while (pos > 0 && matches.size() < wanted) {
            long start = lineStart(pos - 1);
            test(readLine(start, pos), start);
            pos = start;
        }
        Collections.reverse(matches);
        Collections.reverse(matchStarts);
        return new Result(matches, matchStarts, LongRange.of(pos, end));
    }

    /** Applies the filter to a line, adding it to the matches if it passes. */
    protected void test(String line, long lineStart) {
        if (filter.test(line)) {
            matches.add(line);
            matchStarts.add(lineStart);
        }
    }

    /** @return position of the first byte of the line containing pos */
    protected long lineStart(long pos) throws IOException {
        for (long p = pos - 1; p >= 0; p--) {
            if (byteAt(p, false) == '\n') {
                return p + 1;
            }
        }
        return 0;
    }

    /** @return position just past the newline ending the line containing pos */
    protected long lineEnd(long pos) throws IOException {
        for (long p = pos; p < length; p++) {
            if (byteAt(p, true) == '\n') {
                return p + 1;
            }
        }
        return length;
    }

    protected byte byteAt(long pos, boolean forward) throws IOException {
        if (pos < bufStart || pos >= bufStart + bufLen) {
            long start = forward ? pos : Math.max(0, pos - CHUNK_SIZE + 1);
            fill(start);
        }
        return buf[(int) (pos - bufStart)];
    }

    protected void fill(long start) throws IOException {
        int len = (int) Math.min(CHUNK_SIZE, length - start);
        source.read(start, buf, 0, len);
        bufStart = start;
        bufLen = len;
    }

    /**
     * Reads [start, end) as UTF-8, dropping the trailing line terminator.
     * Lines longer than {@link #MAX_LINE_LENGTH} bytes are truncated.
     */
    protected String readLine(long start, long end) throws IOException {
        // read just enough to hold a maximal line plus its \r\n terminator
        int len = (int) Math.min(end - start, MAX_LINE_LENGTH + 2);
        byte[] bytes;
        int offset;
        if (start >= bufStart && start + len <= bufStart + bufLen) {
            bytes = buf;
            offset = (int) (start - bufStart);
        } else {
            bytes = new byte[len];
            source.read(start, bytes, 0, len);
            offset = 0;
        }
        if (len == end - start && len > 0 && bytes[offset + len - 1] == '\n') {
            len--;
            if (len > 0 && bytes[offset + len - 1] == '\r') {
                len--;
            }
        }
        if (len > MAX_LINE_LENGTH) {
            len = MAX_LINE_LENGTH;
            // don't split a multi-byte character
            while (len > 0 && (bytes[offset + len] & 0xC0) == 0x80) {
                len--;
            }
        }
        return new String(bytes, offset, len, StandardCharsets.UTF_8);
    }
}
