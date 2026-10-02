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

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A log file and, optionally, the earlier generations of it rotated off at
 * checkpoints, read as one virtual file with the generations laid end to end
 * in time order.
 *
 * Checkpointing renames the active log in place (crawl.log becomes
 * crawl.log.cp00001-20240115120000) and starts a new one, so a byte position
 * in the virtual file stays valid across checkpoints: the renamed file keeps
 * its place, and the new log continues after it.
 *
 * All files are opened, and their lengths fixed, when the series is created,
 * so a rotation or append while reading doesn't change what is read. They
 * are opened with NIO because, unlike java.io, it lets an open file be
 * renamed on Windows, so a checkpoint can rotate the log mid-search.
 */
public class LogSeries implements Closeable {
    /**
     * suffix added to rotated logs: '.cpNNNNN-timestamp' at a checkpoint or
     * '.timestamp' for a plain rotation
     */
    protected static final Pattern ROTATED = Pattern.compile(
            "(.+)\\.(?:cp\\d{5}-)?(\\d{14})");

    protected final List<File> files;
    protected final FileChannel[] channels;
    /** virtual position of the start of each file, plus the total length */
    protected final long[] starts;

    protected LogSeries(List<File> files) throws IOException {
        this.files = files;
        this.channels = new FileChannel[files.size()];
        this.starts = new long[files.size() + 1];
        try {
            for (int i = 0; i < files.size(); i++) {
                channels[i] = FileChannel.open(files.get(i).toPath(),
                        StandardOpenOption.READ);
                starts[i + 1] = starts[i] + channels[i].size();
            }
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /** @return a series of just the given file */
    public static LogSeries single(File file) throws IOException {
        return new LogSeries(Collections.singletonList(file));
    }

    /**
     * @return a series of all generations of the log the given file belongs
     *         to, which may be the active log or a rotated one
     */
    public static LogSeries all(File file) throws IOException {
        return new LogSeries(generations(file));
    }

    /**
     * @return the name of the active log a file belongs to, e.g. 'crawl.log'
     *         for 'crawl.log.cp00001-20240115120000'
     */
    public static String baseName(String name) {
        Matcher m = ROTATED.matcher(name);
        return m.matches() ? m.group(1) : name;
    }

    /**
     * @return number of rotated generations of the log the given file
     *         belongs to, besides the active log
     */
    public static int countRotated(File file) {
        List<File> files = generations(file);
        File active = new File(file.getParentFile(), baseName(file.getName()));
        return files.contains(active) ? files.size() - 1 : files.size();
    }

    /**
     * @return position of the given file in the series of all generations
     *         of its log, from the sizes of the files before it, without
     *         opening them; -1 if it isn't part of the series
     */
    public static long startOfInAll(File file) {
        long start = 0;
        for (File f : generations(file)) {
            if (f.equals(file.getAbsoluteFile())) {
                return start;
            }
            start += f.length();
        }
        return -1;
    }

    /**
     * Lists the generations of a log in time order, the active log last.
     * Compressed generations, which can't be read at random positions, don't
     * match the rotated name pattern and are left out.
     */
    protected static List<File> generations(File file) {
        String base = baseName(file.getName());
        File dir = file.getAbsoluteFile().getParentFile();
        List<File> rotated = new ArrayList<>();
        String[] names = dir.list();
        if (names != null) {
            for (String name : names) {
                if (isRotated(name, base)) {
                    rotated.add(new File(dir, name));
                }
            }
        }
        // by timestamp, then name to put cpNNNNN in order
        rotated.sort(Comparator.comparing((File f) -> timestamp(f.getName()))
                .thenComparing(File::getName));
        File active = new File(dir, base);
        if (active.isFile()) {
            rotated.add(active);
        }
        return rotated;
    }

    protected static boolean isRotated(String name, String base) {
        Matcher m = ROTATED.matcher(name);
        return m.matches() && m.group(1).equals(base);
    }

    protected static String timestamp(String name) {
        Matcher m = ROTATED.matcher(name);
        return m.matches() ? m.group(2) : "";
    }

    /** @return the files in the series, oldest first */
    public List<File> getFiles() {
        return files;
    }

    /** @return total length of the series */
    public long length() {
        return starts[files.size()];
    }

    /** @return index of the file containing the given virtual position */
    public int fileIndexAt(long pos) {
        for (int i = files.size() - 1; i > 0; i--) {
            if (pos >= starts[i]) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Reads len bytes starting at virtual position pos, which may span
     * several files.
     */
    public void read(long pos, byte[] buf, int off, int len)
            throws IOException {
        if (pos < 0 || pos + len > length()) {
            throw new EOFException("read past end of log series");
        }
        int i = fileIndexAt(pos);
        while (len > 0) {
            // skip empty files
            while (pos >= starts[i + 1]) {
                i++;
            }
            int n = (int) Math.min(len, starts[i + 1] - pos);
            ByteBuffer dst = ByteBuffer.wrap(buf, off, n);
            long filePos = pos - starts[i];
            while (dst.hasRemaining()) {
                int read = channels[i].read(dst, filePos);
                if (read < 0) {
                    throw new EOFException(files.get(i) + " was truncated");
                }
                filePos += read;
            }
            pos += n;
            off += n;
            len -= n;
        }
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (FileChannel channel : channels) {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    if (first == null) {
                        first = e;
                    }
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }
}
