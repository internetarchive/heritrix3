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
 
package org.archive.bdb;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.archive.checkpointing.CheckpointRecovery;
import org.archive.checkpointing.Checkpointable;
import org.archive.checkpointing.RecoverAction;
import org.archive.spring.ConfigPath;
import org.archive.util.CachedBdbMap;
import org.archive.util.ObjectIdentityBdbCache;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.bdbje.EnhancedEnvironment;
import org.springframework.context.Lifecycle;

import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.DbInternal;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.dbi.EnvironmentImpl;
import com.sleepycat.je.utilint.DbLsn;

/**
 * Utility module for managing a shared BerkeleyDB-JE environment
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class BdbModule implements Lifecycle, Checkpointable, 
Serializable, Closeable {
    private static final long serialVersionUID = 1L;
    final private static Logger LOGGER = 
        Logger.getLogger(BdbModule.class.getName()); 

    
    private static class DatabasePlusConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        public transient Database database;
        public String name;
        public String primaryName;
        public BdbConfig config;
    }
    
    
    /**
     * Configuration object for databases.  Needed because 
     * {@link DatabaseConfig} is not serializable.  Also it prevents invalid
     * configurations.  (All databases opened through this module must be
     * deferred-write, because otherwise they can't sync(), and you can't
     * run a checkpoint without doing sync() first.)
     * 
     * @author pjack
     *
     */
    public static class BdbConfig implements Serializable {
        private static final long serialVersionUID = 1L;

        boolean allowCreate;
        boolean sortedDuplicates;
        boolean transactional;
        boolean deferredWrite = true; 

        public BdbConfig() {
        }


        public boolean isAllowCreate() {
            return allowCreate;
        }


        public void setAllowCreate(boolean allowCreate) {
            this.allowCreate = allowCreate;
        }


        public boolean getSortedDuplicates() {
            return sortedDuplicates;
        }


        public void setSortedDuplicates(boolean sortedDuplicates) {
            this.sortedDuplicates = sortedDuplicates;
        }

        public DatabaseConfig toDatabaseConfig() {
            DatabaseConfig result = new DatabaseConfig();
            result.setDeferredWrite(deferredWrite);
            result.setTransactional(transactional);
            result.setAllowCreate(allowCreate);
            result.setSortedDuplicates(sortedDuplicates);
            return result;
        }


        public boolean isTransactional() {
            return transactional;
        }


        public void setTransactional(boolean transactional) {
            this.transactional = transactional;
        }


        public void setDeferredWrite(boolean b) {
            this.deferredWrite = true; 
        }
    }
    
    protected ConfigPath dir = new ConfigPath("bdbmodule subdirectory","state");
    public ConfigPath getDir() {
        return dir;
    }
    public void setDir(ConfigPath dir) {
        this.dir = dir;
    }
    
    int cachePercent = -1;
    public int getCachePercent() {
        return cachePercent;
    }
    public void setCachePercent(int cachePercent) {
        this.cachePercent = cachePercent;
    }

    boolean useSharedCache = true; 
    public boolean getUseSharedCache() {
        return useSharedCache;
    }
    public void setUseSharedCache(boolean useSharedCache) {
        this.useSharedCache = useSharedCache;
    }
    
    boolean checkpointCopyLogs = true; 
    public boolean getCheckpointCopyLogs() {
        return checkpointCopyLogs;
    }
    public void setCheckpointCopyLogs(boolean checkpointCopyLogs) {
        this.checkpointCopyLogs = checkpointCopyLogs;
    }
    
    /**
     * Expected number of concurrent threads; used to tune nLockTables
     * according to JE FAQ
     * http://www.oracle.com/technology/products/berkeley-db/faq/je_faq.html#33
     */
    int expectedConcurrency = 25;
    public int getExpectedConcurrency() {
        return expectedConcurrency;
    }
    public void setExpectedConcurrency(int expectedConcurrency) {
        this.expectedConcurrency = expectedConcurrency;
    }
    
    private transient EnhancedEnvironment bdbEnvironment;
        
    private transient StoredClassCatalog classCatalog;
    
    private Map<String,ObjectIdentityCache> oiCaches = 
        new ConcurrentHashMap<String,ObjectIdentityCache>();

    private Map<String,DatabasePlusConfig> databases =
        new ConcurrentHashMap<String,DatabasePlusConfig>();

    private transient Thread shutdownHook;
    
    public BdbModule() {
    }

    
    public void start() {
        if (isRunning()) {
            return;
        }
        try {
            setUp(getDir().getFile(), getCachePercent(), true, getUseSharedCache());
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        }
        shutdownHook = new BdbShutdownHook(this);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }
    
    public boolean isRunning() {
        return shutdownHook!=null;
    }

    public void stop() {
        if (!isRunning()) {
            return;
        }
        close();
    }
    
    private void setUp(File f, int cachePercent, boolean create, boolean sharedCache) 
    throws DatabaseException {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(create);
        config.setLockTimeout(5000000);
        if(cachePercent>0) {
            config.setCachePercent(cachePercent);
        }
        config.setSharedCache(sharedCache);
        
        // we take the advice literally from...
        // http://www.oracle.com/technology/products/berkeley-db/faq/je_faq.html#33
        long nLockTables = expectedConcurrency-1;
        while(!BigInteger.valueOf(nLockTables).isProbablePrime(Integer.MAX_VALUE)) {
            nLockTables--;
        }
        config.setConfigParam("je.lock.nLockTables", Long.toString(nLockTables));
        
        // triple this value to 6K because stats show many faults
        config.setConfigParam("je.log.faultReadSize", "6144");
        f.mkdirs();
        this.bdbEnvironment = new EnhancedEnvironment(f, config);
        
        this.classCatalog = this.bdbEnvironment.getClassCatalog();
    }


    public void closeDatabase(Database db) {
        try {
            closeDatabase(db.getDatabaseName());
        } catch (DatabaseException e) {
            LOGGER.log(Level.SEVERE, "Error getting db name", e);            
        }
    }
    
    public void closeDatabase(String name) {
        DatabasePlusConfig dpc = databases.remove(name);
        if (dpc == null) {
            throw new IllegalStateException("No such database: " + name);
        }
        Database db = dpc.database;
        try {
            db.sync();
            db.close();
        } catch (DatabaseException e) {
            LOGGER.log(Level.WARNING, "Error closing db " + name, e);
        }
    }
    
    

    /**
     * Open a Database inside this BdbModule's environment.
     * 
     * @param name
     * @param config
     * @param usePriorData use existing data in database, if any 
     * @return
     * @throws DatabaseException
     */
    public Database openDatabase(String name, BdbConfig config, boolean usePriorData) 
    throws DatabaseException {
        if (!usePriorData) {
            try {
                bdbEnvironment.truncateDatabase(null, name, false);
            } catch (DatabaseNotFoundException e) {
                // Ignored
            }
        }
        return bdbEnvironment.openDatabase(null, name, config.toDatabaseConfig());
    }
    
    /**
     * Open a Database inside this BdbModule's environment, and 
     * remember it for automatic close-at-module-stop. 
     * 
     * @param name
     * @param config
     * @param usePriorData
     * @return
     * @throws DatabaseException
     */
    public Database openManagedDatabase(String name, BdbConfig config, boolean usePriorData) 
    throws DatabaseException {
        if(!isRunning()) {
            // proper initialization hasn't occurred
            throw new IllegalStateException(
                    "BdbModule not started; as a Lifecycle bean it must not be an inner bean.");
        }
        if (databases.containsKey(name)) {
            throw new IllegalStateException("Database already exists: " +name);
        }
        
        DatabasePlusConfig dpc = new DatabasePlusConfig();
        dpc.database = openDatabase(name, config, usePriorData);
        dpc.name = name;
        dpc.config = config;
        databases.put(name, dpc);
        return dpc.database;
    }

    public StoredClassCatalog getClassCatalog() {
        return classCatalog;
    }


    /**
     * Get a CachedBdbMap, backed by a BDB Database of the given name, 
     * with the given key and value class types. If 'recycle' is true,
     * reuse values already in the database; otherwise start with an 
     * empty map.  
     * 
     * @param <K>
     * @param <V>
     * @param dbName
     * @param recycle
     * @param key
     * @param value
     * @return
     * @throws DatabaseException
     */
    public <K,V> CachedBdbMap<K,V> getCBMMap(String dbName, boolean recycle,
            Class<? super K> key, Class<? super V> value) 
    throws DatabaseException {
        if (!recycle) {
            try {
                bdbEnvironment.truncateDatabase(null, dbName, false);
            } catch (DatabaseNotFoundException e) {
                // ignored
            }
        }
        CachedBdbMap<K, V> r = new CachedBdbMap<K,V>(dbName);     
        r.initialize(bdbEnvironment, key, value, classCatalog);
        oiCaches.put(dbName, r);
        return r;
    }

    /**
     * Get an ObjectIdentityBdbCache, backed by a BDB Database of the 
     * given name, with the given value class type. If 'recycle' is true,
     * reuse values already in the database; otherwise start with an 
     * empty cache. 
     *  
     * @param <V>
     * @param dbName
     * @param recycle
     * @param valueClass
     * @return
     * @throws DatabaseException
     */
    public <V> ObjectIdentityBdbCache<V> getOIBCCache(String dbName, boolean recycle,
            Class<? super V> valueClass) 
    throws DatabaseException {
        if (!recycle) {
            try {
                bdbEnvironment.truncateDatabase(null, dbName, false);
            } catch (DatabaseNotFoundException e) {
                // ignored
            }
        }
        ObjectIdentityBdbCache<V> oic = new ObjectIdentityBdbCache<V>();
        oic.initialize(bdbEnvironment, dbName, valueClass, classCatalog);
        oiCaches.put(dbName, oic);
        return oic;
    }

    /** controls which alternate ObjectIdentityCache implementation to use */
    private static boolean USE_OIBC = true;
    

    
    /**
     * Get an ObjectIdentityCache, backed by a BDB Database of the given 
     * name, with objects of the given valueClass type. If 'recycle' is
     * true, reuse values already in the database; otherwise start with 
     * an empty cache. 
     * 
     * @param <V>
     * @param dbName
     * @param recycle
     * @param valueClass
     * @return
     * @throws DatabaseException
     */
    public <V> ObjectIdentityCache<String, V> getObjectCache(String dbName, boolean recycle,
            Class<? super V> valueClass) 
    throws DatabaseException {
        @SuppressWarnings("unchecked")
        ObjectIdentityCache<String,V> oic = oiCaches.get(dbName);
        if(oic!=null) {
            return oic; 
        }
        if(USE_OIBC) {
            oic =  getOIBCCache(dbName, recycle, valueClass);
        } else {
            oic =  getCBMMap(dbName, recycle, String.class, valueClass);
        }
        return oic; 
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
    
    // TODO:FIXME: restore functionality
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in) 
    throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (in instanceof CheckpointRecovery) {
//            CheckpointRecovery cr = (CheckpointRecovery)in;
//            path = cr.translatePath(path);
//            cr.setState(this, DIR, path);
        }
        try {
            setUp(getDir().getFile(), getCachePercent(), false, getUseSharedCache());
//            for (CachedBdbMap map: bigMaps.values()) {
//                map.initialize(
//                        this.bdbEnvironment, 
//                        null,
//                        null,
//                        map.getKeyClass(), 
//                        map.getValueClass(), 
//                        this.classCatalog);
//          }
            for (DatabasePlusConfig dpc: databases.values()) {
                dpc.database = bdbEnvironment.openDatabase(null, 
                        dpc.name, dpc.config.toDatabaseConfig());
            }
        } catch (DatabaseException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        }
        this.shutdownHook = new BdbShutdownHook(this);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }


    @SuppressWarnings("unchecked")
    public void checkpoint(File dir, List<RecoverAction> actions) 
    throws IOException {
        if (checkpointCopyLogs) {
            actions.add(new BdbRecover(getDir().getFile().getAbsolutePath()));
        }
        // First sync objectCaches
        for (ObjectIdentityCache oic : oiCaches.values()) {
            oic.sync();
        }

        EnvironmentConfig envConfig;
        try {
            // sync all databases
            for (DatabasePlusConfig dbc: databases.values()) {
                dbc.database.sync();
            }
            envConfig = bdbEnvironment.getConfig();
        } catch (DatabaseException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        }
        
        final List<String> bkgrdThreads = Arrays.asList(new String []
            {"je.env.runCheckpointer", "je.env.runCleaner",
                "je.env.runINCompressor"});
        try {
            // Disable background threads
            setBdbjeBkgrdThreads(envConfig, bkgrdThreads, "false");
            // Do a force checkpoint.  Thats what a sync does (i.e. doSync).
            CheckpointConfig chkptConfig = new CheckpointConfig();
            chkptConfig.setForce(true);
            
            // Mark Hayes of sleepycat says:
            // "The default for this property is false, which gives the current
            // behavior (allow deltas).  If this property is true, deltas are
            // prohibited -- full versions of internal nodes are always logged
            // during the checkpoint. When a full version of an internal node
            // is logged during a checkpoint, recovery does not need to process
            // it at all.  It is only fetched if needed by the application,
            // during normal DB operations after recovery. When a delta of an
            // internal node is logged during a checkpoint, recovery must
            // process it by fetching the full version of the node from earlier
            // in the log, and then applying the delta to it.  This can be
            // pretty slow, since it is potentially a large amount of
            // random I/O."
            chkptConfig.setMinimizeRecoveryTime(true);
            bdbEnvironment.checkpoint(chkptConfig);
            LOGGER.fine("Finished bdb checkpoint.");
            
            // From the sleepycat folks: A trick for flipping db logs.
            EnvironmentImpl envImpl = 
                DbInternal.envGetEnvironmentImpl(bdbEnvironment);
            long firstFileInNextSet =
                DbLsn.getFileNumber(envImpl.forceLogFileFlip());
            // So the last file in the checkpoint is firstFileInNextSet - 1.
            // Write manifest of all log files into the bdb directory.
            final String lastBdbCheckpointLog =
                getBdbLogFileName(firstFileInNextSet - 1);
            processBdbLogs(dir, lastBdbCheckpointLog);
            LOGGER.fine("Finished processing bdb log files.");
        } catch (DatabaseException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        } finally {
            // Restore background threads.
            setBdbjeBkgrdThreads(envConfig, bkgrdThreads, "true");
        }
    }


    private void processBdbLogs(final File checkpointDir,
            final String lastBdbCheckpointLog) throws IOException {
        File bdbDir = getBdbSubDirectory(checkpointDir);
        if (!bdbDir.exists()) {
            bdbDir.mkdir();
        }
        PrintWriter pw = new PrintWriter(new FileOutputStream(new File(
             checkpointDir, "bdbje-logs-manifest.txt")));
        try {
            // Don't copy any beyond the last bdb log file (bdbje can keep
            // writing logs after checkpoint).
            boolean pastLastLogFile = false;
            Set<String> srcFilenames = null;
            do {
                FilenameFilter filter = new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name != null 
                            && name.toLowerCase().endsWith(".jdb");
                    }
                };

                srcFilenames =
                    new HashSet<String>(Arrays.asList(getDir().getFile().list(filter)));
                List<String> tgtFilenames = Arrays.asList(bdbDir.list(filter));
                if (tgtFilenames != null && tgtFilenames.size() > 0) {
                    srcFilenames.removeAll(tgtFilenames);
                }
                if (srcFilenames.size() > 0) {
                    // Sort files.
                    srcFilenames = new TreeSet<String>(srcFilenames);
                    int count = 0;
                    for (final Iterator<String> i = srcFilenames.iterator();
                            i.hasNext() && !pastLastLogFile;) {
                        String name = (String) i.next();
                        if (this.checkpointCopyLogs) {
                            FileUtils.copyDirectory(new File(getDir().getFile(), name),
                                new File(bdbDir, name));
                        }
                        pw.println(name);
                        if (name.equals(lastBdbCheckpointLog)) {
                            // We're done.
                            pastLastLogFile = true;
                        }
                        count++;
                    }
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine("Copied " + count);
                    }
                }
            } while (!pastLastLogFile && srcFilenames != null &&
                srcFilenames.size() > 0);
        } finally {
            pw.close();
        }
    }


    
    private void setBdbjeBkgrdThreads(final EnvironmentConfig config,
            final List<String> threads, final String setting) {
        for (final Iterator<String> i = threads.iterator(); i.hasNext();) {
            config.setConfigParam((String)i.next(), setting);
        }
    }

    
    private String getBdbLogFileName(final long index) {
        String lastBdbLogFileHex = Long.toHexString(index);
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < (8 - lastBdbLogFileHex.length()); i++) {
            buffer.append('0');
        }
        buffer.append(lastBdbLogFileHex);
        buffer.append(".jdb");
        return buffer.toString();
    }

    
    public void close() {
        close2();
        if(shutdownHook!=null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            shutdownHook = null; 
        }
    }
    
    @SuppressWarnings("unchecked")
    void close2() {
        if (classCatalog == null) {
            return;
        }
        
        for(ObjectIdentityCache cache : oiCaches.values()) {
            try {
                cache.close();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error closing oiCache " + cache, e);
            }
        }

        List<String> dbNames = new ArrayList<String>(databases.keySet());
        for (String dbName: dbNames) try {
            closeDatabase(dbName);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error closing db " + dbName, e);
        }

        try {
            this.bdbEnvironment.sync();
            this.bdbEnvironment.close();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error closing environment.", e);
        }
    }


    private static File getBdbSubDirectory(File checkpointDir) {
        return new File(checkpointDir, "bdbje-logs");
    }
    
    
    public Database getDatabase(String name) {
        DatabasePlusConfig dpc = databases.get(name);
        if (dpc == null) {
            return null;
        }
        return dpc.database;
    }


    private static class BdbRecover implements RecoverAction {

        private static final long serialVersionUID = 1L;

        private String path;

        public BdbRecover(String path) {
            this.path = path;
        }
        
        public void recoverFrom(File checkpointDir, 
            CheckpointRecovery recovery) throws Exception {
            File bdbDir = getBdbSubDirectory(checkpointDir);
            path = recovery.translatePath(path);
            FileUtils.copyDirectory(bdbDir, new File(path));
        }
    }

    
    private static class BdbShutdownHook extends Thread {
        final private BdbModule bdb;
        
        public BdbShutdownHook(BdbModule bdb) {
            this.bdb = bdb;
        }
        
        public void run() {
            this.bdb.close2();
        }
    }

    /** uniqueness serial number for temp map databases */
    long sn = 0; 
        
    /**
     * Creates a database-backed TempStoredSortedMap for transient 
     * reporting requirements. Calling the returned map's destroy()
     * method when done discards the associated Database. 
     * 
     * @param <K>
     * @param <V>
     * @param dbName Database name to use; if null a name will be synthesized
     * @param keyClass Class of keys; should be a Java primitive type
     * @param valueClass Class of values; may be any serializable type
     * @param allowDuplicates whether duplicate keys allowed
     * @return
     */
    public <K,V> TempStoredSortedMap<K, V> getStoredMap(String dbName, Class<K> keyClass, Class<V> valueClass, boolean allowDuplicates) {
        BdbConfig config = new BdbConfig(); 
        config.setSortedDuplicates(true);
        config.setAllowCreate(true); 
        Database mapDb;
        if(dbName==null) {
            dbName = "tempMap-"+System.identityHashCode(this)+"-"+sn;
            sn++;
        }
        try {
            mapDb = openDatabase(dbName,config,false);
        } catch (DatabaseException e) {
            throw new RuntimeException(e); 
        } 
        EntryBinding<V> valueBinding = TupleBinding.getPrimitiveBinding(valueClass);
        if(valueBinding == null) {
            valueBinding = new SerialBinding<V>(classCatalog, valueClass);
        }
        TempStoredSortedMap<K,V> storedMap = new TempStoredSortedMap<K, V>(
                mapDb,
                TupleBinding.getPrimitiveBinding(keyClass),
                valueBinding,
                true);
        return storedMap; 
    }
}
