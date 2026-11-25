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

package org.archive.modules.index;

import static org.archive.modules.CoreAttributeConstants.HEADER_TRUNC;
import static org.archive.modules.CoreAttributeConstants.LENGTH_TRUNC;
import static org.archive.modules.CoreAttributeConstants.TIMER_TRUNC;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_FILENAME;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_FILE_OFFSET;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.archive.bdb.BdbModule;
import org.archive.format.warc.WARCConstants.WARCRecordType;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.revisit.IdenticalPayloadDigestRevisit;
import org.archive.modules.revisit.ServerNotModifiedRevisit;
import org.archive.spring.ConfigPathConfigurer;
import org.archive.util.SURT;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.sleepycat.bind.tuple.IntegerBinding;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.OperationStatus;

/**
 * A {@link CaptureIndex} stored in BDB. Every capture is kept, newest first for each URL.
 */
public class BdbCaptureIndex implements CaptureIndex, Lifecycle {
    private static final int VERSION = 1;
    private static final int REVISIT = 1, TRUNCATED = 2, HAS_REQUEST = 4, HAS_REFERS_TO = 8;
    private static final String URN_UUID = "urn:uuid:";

    private final BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
    private BdbModule bdb;
    private ConfigPathConfigurer configPathConfigurer;
    private Path jobDir;
    private String dbName = "captureIndex";
    private Database captures;
    private Database files;
    private final Map<String, Integer> fileIdsByName = new ConcurrentHashMap<>();
    private final Map<Integer, Path> filePaths = new ConcurrentHashMap<>();

    {
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
    }

    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }

    /**
     * Autowired. WARC files inside the job directory are recorded relative to it, so they're
     * still found if the job directory moves.
     */
    @Autowired(required = false)
    public void setConfigPathConfigurer(ConfigPathConfigurer configPathConfigurer) {
        this.configPathConfigurer = configPathConfigurer;
    }

    public String getDbName() {
        return dbName;
    }

    /**
     * Name of the BDB database holding the captures. The file names are kept in a second database
     * with "Files" appended to this name.
     */
    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) return;
        jobDir = configPathConfigurer == null ? null
                : configPathConfigurer.getPath().getFile().toPath().toAbsolutePath().normalize();
        captures = bdb.openDatabase(dbName, dbConfig, true);
        files = bdb.openDatabase(dbName + "Files", dbConfig, true);
        try (Cursor cursor = files.openCursor(null, null)) {
            var key = new DatabaseEntry();
            var value = new DatabaseEntry();
            while (cursor.getNext(key, value, null) == OperationStatus.SUCCESS) {
                int id = IntegerBinding.entryToInt(key);
                Path path = Path.of(StringBinding.entryToString(value));
                if (jobDir != null) path = jobDir.resolve(path);
                filePaths.put(id, path);
                fileIdsByName.put(path.getFileName().toString(), id);
            }
        }
    }

    @Override
    public synchronized void stop() {
        // BdbModule closes the databases
        captures = null;
        files = null;
        fileIdsByName.clear();
        filePaths.clear();
    }

    @Override
    public boolean isRunning() {
        return captures != null;
    }

    @Override
    public void add(CrawlURI curi, Iterable<WARCRecordInfo> records) {
        if (curi.getFetchType() != FetchType.HTTP_GET || !curi.hasCanonicalString()) return;

        WARCRecordInfo record = null;
        long requestOffset = -1;
        for (WARCRecordInfo r : records) {
            switch (r.getType()) {
                case response, revisit -> record = r;
                case request -> requestOffset = r.getWARCFileOffset();
                default -> {}
            }
        }
        if (record == null) return;

        String urlKey = urlKey(curi.getCanonicalString());
        boolean revisit = record.getType() == WARCRecordType.revisit;
        String digest = revisit ? revisitedDigest(curi) : curi.getContentDigestSchemeString();
        Path refersToPath = null;
        long refersToOffset = -1;
        if (revisit) {
            Map<String, Object> history = curi.hasContentDigestHistory()
                    ? curi.getContentDigestHistory() : Map.of();
            Integer fileId = history.get(A_WARC_FILENAME) instanceof String filename
                    ? fileIdsByName.get(filename) : null;
            if (fileId != null) {
                refersToPath = filePaths.get(fileId);
                refersToOffset = (Long) history.get(A_WARC_FILE_OFFSET);
            } else if (digest != null) {
                Capture original = find(urlKey,
                        c -> !c.revisit() && !c.truncated() && digest.equals(c.digest()));
                if (original != null) {
                    refersToPath = original.path();
                    refersToOffset = original.offset();
                }
            }
        }
        Collection<String> annotations = curi.getAnnotations();
        boolean truncated = annotations.contains(LENGTH_TRUNC) || annotations.contains(TIMER_TRUNC)
                || annotations.contains(HEADER_TRUNC);

        var capture = new Capture(curi.getFetchBeginTime(), curi.getFetchStatus(),
                curi.getContentType(), digest, recordId(record), revisit, truncated,
                record.getWARCPath(), record.getWARCFileOffset(), record.getRecordLength(),
                requestOffset, refersToPath, refersToOffset);
        captures.put(null, key(urlKey, capture.time()), encode(capture));
    }

    @Override
    public Capture latest(String canonicalUri) {
        return find(urlKey(canonicalUri), c -> true);
    }

    private Capture find(String urlKey, Predicate<Capture> predicate) {
        byte[] prefix = new TupleOutput().writeString(urlKey).toByteArray();
        var key = new DatabaseEntry(prefix);
        var value = new DatabaseEntry();
        try (Cursor cursor = captures.openCursor(null, null)) {
            OperationStatus status = cursor.getSearchKeyRange(key, value, null);
            while (status == OperationStatus.SUCCESS && startsWith(key, prefix)) {
                Capture capture = decode(key, value);
                if (predicate.test(capture)) return capture;
                status = cursor.getNext(key, value, null);
            }
        }
        return null;
    }

    private static String urlKey(String canonicalUri) {
        return SURT.fromURI(canonicalUri, true);
    }

    private static DatabaseEntry key(String urlKey, long time) {
        // negated so that the newest capture of each URL sorts first
        var out = new TupleOutput().writeString(urlKey).writeLong(-time);
        return new DatabaseEntry(out.getBufferBytes(), 0, out.getBufferLength());
    }

    private static boolean startsWith(DatabaseEntry entry, byte[] prefix) {
        return entry.getSize() >= prefix.length && Arrays.equals(entry.getData(), entry.getOffset(),
                entry.getOffset() + prefix.length, prefix, 0, prefix.length);
    }

    private DatabaseEntry encode(Capture c) {
        int flags = (c.revisit() ? REVISIT : 0) | (c.truncated() ? TRUNCATED : 0)
                | (c.requestOffset() >= 0 ? HAS_REQUEST : 0)
                | (c.refersToPath() != null ? HAS_REFERS_TO : 0);
        var out = new TupleOutput();
        out.writeByte(VERSION);
        out.writeByte(flags);
        out.writeShort(c.status());
        out.writeString(c.digest());
        out.writeLong(c.recordId().getMostSignificantBits());
        out.writeLong(c.recordId().getLeastSignificantBits());
        out.writePackedInt(fileId(c.path()));
        out.writePackedLong(c.offset());
        out.writePackedLong(c.length());
        out.writeString(c.mimeType());
        if ((flags & HAS_REQUEST) != 0) {
            out.writePackedLong(c.requestOffset());
        }
        if ((flags & HAS_REFERS_TO) != 0) {
            out.writePackedInt(fileId(c.refersToPath()));
            out.writePackedLong(c.refersToOffset());
        }
        return new DatabaseEntry(out.getBufferBytes(), 0, out.getBufferLength());
    }

    private Capture decode(DatabaseEntry key, DatabaseEntry value) {
        var keyIn = new TupleInput(key.getData(), key.getOffset(), key.getSize());
        keyIn.readString();
        long time = -keyIn.readLong();

        var in = new TupleInput(value.getData(), value.getOffset(), value.getSize());
        int version = in.readByte();
        if (version != VERSION) {
            throw new IllegalStateException("Unsupported capture index version " + version);
        }
        int flags = in.readByte();
        int status = in.readShort();
        String digest = in.readString();
        var recordId = new UUID(in.readLong(), in.readLong());
        Path path = filePaths.get(in.readPackedInt());
        long offset = in.readPackedLong();
        long length = in.readPackedLong();
        String mimeType = in.readString();
        long requestOffset = (flags & HAS_REQUEST) != 0 ? in.readPackedLong() : -1;
        Path refersToPath = null;
        long refersToOffset = -1;
        if ((flags & HAS_REFERS_TO) != 0) {
            refersToPath = filePaths.get(in.readPackedInt());
            refersToOffset = in.readPackedLong();
        }
        return new Capture(time, status, mimeType, digest, recordId, (flags & REVISIT) != 0,
                (flags & TRUNCATED) != 0, path, offset, length, requestOffset, refersToPath,
                refersToOffset);
    }

    private int fileId(Path path) {
        String name = path.getFileName().toString();
        Integer id = fileIdsByName.get(name);
        if (id != null) return id;
        synchronized (fileIdsByName) {
            id = fileIdsByName.get(name);
            if (id == null) {
                id = filePaths.size();
                var key = new DatabaseEntry();
                var value = new DatabaseEntry();
                IntegerBinding.intToEntry(id, key);
                Path stored = path.toAbsolutePath().normalize();
                if (jobDir != null && stored.startsWith(jobDir)) stored = jobDir.relativize(stored);
                StringBinding.stringToEntry(stored.toString(), value);
                files.put(null, key, value);
                filePaths.put(id, path);
                fileIdsByName.put(name, id);
            }
            return id;
        }
    }

    private static UUID recordId(WARCRecordInfo record) {
        String id = record.getRecordId().toString();
        if (!id.startsWith(URN_UUID)) {
            throw new IllegalArgumentException("Not a urn:uuid record ID: " + id);
        }
        return UUID.fromString(id.substring(URN_UUID.length()));
    }

    private static String revisitedDigest(CrawlURI curi) {
        if (curi.getRevisitProfile() instanceof IdenticalPayloadDigestRevisit profile) {
            return profile.getPayloadDigest();
        }
        if (curi.getRevisitProfile() instanceof ServerNotModifiedRevisit profile) {
            return profile.getPayloadDigest();
        }
        return null;
    }
}
