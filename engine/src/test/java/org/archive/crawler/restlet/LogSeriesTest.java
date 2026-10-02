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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

public class LogSeriesTest {
    @TempDir
    File dir;

    File write(String name, String content) throws Exception {
        File f = new File(dir, name);
        Files.write(f.toPath(), content.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    static List<String> names(LogSeries series) {
        List<String> names = new ArrayList<>();
        for (File f : series.getFiles()) {
            names.add(f.getName());
        }
        return names;
    }

    @Test
    public void testGenerations() throws Exception {
        write("crawl.log.cp00002-20260101120000", "c1\nc2\n");
        write("crawl.log.cp00001-20260101000000", "a1\n");
        write("crawl.log.20260101060000", ""); // plain rotation, empty
        File active = write("crawl.log", "d1\nd2\n");
        write("crawl.log.cp00003-20260101180000.gz", "compressed, left out");
        write("crawl.log.bak", "not a generation");
        write("crawl.log.cp00001-20260101000000.extra", "nor this");
        write("alerts.log.cp00001-20260101000000", "other log");

        try (LogSeries series = LogSeries.all(active)) {
            assertEquals(List.of("crawl.log.cp00001-20260101000000",
                    "crawl.log.20260101060000",
                    "crawl.log.cp00002-20260101120000", "crawl.log"),
                    names(series));
            assertEquals(3 + 0 + 6 + 6, series.length());
            assertEquals(9, LogSeries.startOfInAll(active));
            assertEquals(3, LogSeries.startOfInAll(new File(dir,
                    "crawl.log.cp00002-20260101120000")));
            assertEquals(-1, LogSeries.startOfInAll(new File(dir, "alerts.log")));

            // the empty file is never the file containing a position
            assertEquals(0, series.fileIndexAt(0));
            assertEquals(2, series.fileIndexAt(3));
            assertEquals(3, series.fileIndexAt(9));
            assertEquals(3, series.fileIndexAt(series.length()));

            // reads span files
            byte[] buf = new byte[8];
            series.read(1, buf, 0, 8);
            assertEquals("1\nc1\nc2\n", new String(buf, StandardCharsets.UTF_8));
            assertThrows(java.io.EOFException.class,
                    () -> series.read(10, new byte[10], 0, 10));
        }

        // a rotated generation finds the same series
        try (LogSeries series = LogSeries.all(new File(dir,
                "crawl.log.cp00001-20260101000000"))) {
            assertEquals(4, series.getFiles().size());
        }
        assertEquals(3, LogSeries.countRotated(active));
        assertEquals(1, LogSeries.countRotated(new File(dir, "alerts.log")));
        assertEquals(0, LogSeries.countRotated(new File(dir, "uri-errors.log")));
    }

    @Test
    public void testScanAcrossFiles() throws Exception {
        write("crawl.log.cp00001-20260101000000", "a1\nb1\n");
        write("crawl.log.cp00002-20260101120000", "a2\n");
        File active = write("crawl.log", "b3\na3\n");
        try (LogSeries series = LogSeries.all(active)) {
            FilteredLineScanner.Result r = FilteredLineScanner.scan(series, 0,
                    10, s -> s.startsWith("a"));
            assertEquals(List.of("a1", "a2", "a3"), r.lines);
            assertEquals(List.of(0L, 6L, 12L), r.lineStarts);
            r = FilteredLineScanner.scan(series, -1, -2, s -> true);
            assertEquals(List.of("b3", "a3"), r.lines);
            assertEquals(9, r.range.getMinimum());
        }
    }

    @Test
    public void testRotationWhileOpen() throws Exception {
        File active = write("crawl.log", "a1\nb1\n");
        try (LogSeries series = LogSeries.all(active)) {
            // a checkpoint renames the active log and starts a new one
            File rotated = new File(dir, "crawl.log.cp00001-20260101000000");
            assertTrue(active.renameTo(rotated));
            write("crawl.log", "new\n");
            byte[] buf = new byte[(int) series.length()];
            series.read(0, buf, 0, buf.length);
            assertEquals("a1\nb1\n", new String(buf, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testBaseName() {
        assertEquals("crawl.log", LogSeries.baseName("crawl.log"));
        assertEquals("crawl.log",
                LogSeries.baseName("crawl.log.cp00012-20260101120000"));
        assertEquals("alerts.log", LogSeries.baseName("alerts.log.20260101120000"));
        assertEquals("crawl.log.2026", LogSeries.baseName("crawl.log.2026"));
    }

    @Test
    public void testActiveLogMissing() throws Exception {
        File rotated = write("crawl.log.cp00001-20260101000000", "a1\n");
        try (LogSeries series = LogSeries.all(rotated)) {
            assertEquals(List.of("crawl.log.cp00001-20260101000000"), names(series));
        }
        assertEquals(1, LogSeries.countRotated(rotated));
    }
}
