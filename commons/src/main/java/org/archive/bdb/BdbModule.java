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
import java.io.FileFilter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.spring.ConfigPath;
import org.archive.util.ObjectIdentityBdbCache;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.bdbje.EnhancedEnvironment;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.util.DbBackup;

/**
 * Utility module for managing a shared BerkeleyDB-JE environment
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class BdbModule implements Lifecycle, Checkpointable, Closeable {
    private static final long serialVersionUID = 1L;
    final private static Logger LOGGER = 
        Logger.getLogger(BdbModule.class.getName()); 

    
    private static class DatabasePlusConfig implements Serializable {
        private static final long serialVersionUID = 1L;
        public transient Database database;
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
    
    @SuppressWarnings("unchecked")
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
            boolean isRecovery = false; 
            if(recoveryCheckpoint!=null) {
                isRecovery = true; 
                doRecover(); 
            }
   
            setup(getDir().getFile(), !isRecovery);
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        } catch (IOException e) {
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
    
    protected void setup(File f, boolean create) 
    throws DatabaseException {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(create);
        config.setLockTimeout(75, TimeUnit.MINUTES); // set to max
        if(getCachePercent()>0) {
            config.setCachePercent(getCachePercent());
        }
        config.setSharedCache(getUseSharedCache());
        
        // we take the advice literally from...
        // http://www.oracle.com/technology/products/berkeley-db/faq/je_faq.html#33
        long nLockTables = getExpectedConcurrency()-1;
        while(!BigInteger.valueOf(nLockTables).isProbablePrime(Integer.MAX_VALUE)) {
            nLockTables--;
        }
        config.setConfigParam("je.lock.nLockTables", Long.toString(nLockTables));
        
        // triple this value to 6K because stats show many faults
        config.setConfigParam("je.log.faultReadSize", "6144"); 

        // to support checkpoints, prevent BDB's cleaner from deleting log files
        config.setConfigParam("je.cleaner.expunge", "false");

        f.mkdirs();
        this.bdbEnvironment = new EnhancedEnvironment(f, config);
        this.classCatalog = this.bdbEnvironment.getClassCatalog();
        if(!create) {
            // freeze last log file -- so that originating checkpoint isn't fouled
            DbBackup dbBackup = new DbBackup(bdbEnvironment);
            dbBackup.startBackup();
            dbBackup.endBackup();
        }
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
            LOGGER.warning("No such database: " + name);
            return; 
        }
        Database db = dpc.database;
        try {
            db.sync();
            db.close();
        } catch (DatabaseException e) {
            LOGGER.log(Level.WARNING, "Error closing db " + name, e);
        }
    }
    
    public void disposeDatabase(String name) {
        DatabasePlusConfig dpc = databases.remove(name);
        if (dpc == null) {
            throw new IllegalStateException("No such database: " + name);
        }
        Database db = dpc.database;
        try {
            db.close();
            bdbEnvironment.removeDatabase(null, name);
        } catch (DatabaseException e) {
            LOGGER.log(Level.WARNING, "Error closing db " + name, e);
        }
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
    public Database openDatabase(String name, BdbConfig config, boolean usePriorData) 
    throws DatabaseException {
        if(!isRunning()) {
            // proper initialization hasn't occurred
            throw new IllegalStateException(
                    "BdbModule not started; as a Lifecycle bean it must not be an inner bean.");
        }
        if (databases.containsKey(name)) {
            DatabasePlusConfig dpc = databases.get(name);
            if(dpc.config == config) {
                // object-identical configs: OK to share DB
                return dpc.database;
            }
            // unshared config object: might be name collision; error
            throw new IllegalStateException("Database already exists: " +name);
        }
        
        DatabasePlusConfig dpc = new DatabasePlusConfig();
        if (!usePriorData) {
            try {
                bdbEnvironment.truncateDatabase(null, name, false);
            } catch (DatabaseNotFoundException e) {
                // Ignored
            }
        }
        dpc.database = bdbEnvironment.openDatabase(null, name, config.toDatabaseConfig());
        dpc.config = config;
        databases.put(name, dpc);
        return dpc.database;
    }

    public StoredClassCatalog getClassCatalog() {
        return classCatalog;
    }

    public <K extends Serializable> StoredQueue<K> getStoredQueue(String dbname, Class<K> clazz) {
        try {
            Database queueDb;
            queueDb = openDatabase(dbname,
                    StoredQueue.databaseConfig(), false);
            return new StoredQueue<K>(queueDb, clazz, getClassCatalog());
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        
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
            Class<? extends V> valueClass) 
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
  
    public <V> ObjectIdentityCache<String, V> getObjectCache(String dbName, boolean recycle,
            Class<V> valueClass) 
    throws DatabaseException {
        return getObjectCache(dbName, recycle, valueClass, valueClass);
    }
    
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
            Class<V> declaredClass, Class<? extends V> valueClass) 
    throws DatabaseException {
        @SuppressWarnings("unchecked")
        ObjectIdentityCache<String,V> oic = oiCaches.get(dbName);
        if(oic!=null) {
            return oic; 
        }
        oic =  getOIBCCache(dbName, recycle, valueClass);
        return oic; 
    }
    
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
    
    public void startCheckpoint(Checkpoint checkpointInProgress) {}

    @SuppressWarnings("unchecked")
    public void doCheckpoint(Checkpoint checkpointInProgress) throws IOException {
        // First sync objectCaches
        for (ObjectIdentityCache oic : oiCaches.values()) {
            oic.sync();
        }

        try {
            // sync all databases
            for (DatabasePlusConfig dbc: databases.values()) {
                dbc.database.sync();
            }
        
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
            // chkptConfig.setMinimizeRecoveryTime(true);
            bdbEnvironment.checkpoint(chkptConfig);
            LOGGER.fine("Finished bdb checkpoint.");
        
            DbBackup dbBackup = new DbBackup(bdbEnvironment);
            try {
                dbBackup.startBackup();
                
                File envCpDir = new File(dir.getFile(),checkpointInProgress.getName());
                envCpDir.mkdirs();
                File logfilesList = new File(envCpDir,"jdbfiles.manifest");
                String[] filedata = dbBackup.getLogFilesInBackupSet();
                for (int i=0; i<filedata.length;i++) {
                    File f = new File(dir.getFile(),filedata[i]);
                    filedata[i] += " "+f.length();
                }
                FileUtils.writeLines(logfilesList,Arrays.asList(filedata));
                LOGGER.fine("Finished processing bdb log files.");
            } finally {
                dbBackup.endBackup();
            }
        } catch (DatabaseException e) {
            throw new IOException(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    protected void doRecover() throws IOException {
        File logfilesList = new File(
                new File(dir.getFile(),recoveryCheckpoint.getName()),
                "jdbfiles.manifest");
        List<String> filesAndLengths = FileUtils.readLines(logfilesList);
        HashMap<String,Long> retainLogfiles = new HashMap<String,Long>();
        for(String line : filesAndLengths) {
            String[] fileAndLength = line.split(" ");
            retainLogfiles.put(fileAndLength[0],Long.valueOf(fileAndLength[1]));
        }
        IOFileFilter filter = FileFilterUtils.orFileFilter(
                FileFilterUtils.suffixFileFilter(".jdb"), 
                FileFilterUtils.suffixFileFilter(".del"));
        filter = FileFilterUtils.makeFileOnly(filter);
        for(File f : dir.getFile().listFiles((FileFilter)filter)) {
            if(retainLogfiles.containsKey(f.getName())) {
                long expectedLength = retainLogfiles.get(f.getName());
                if(f.length()!=expectedLength) {
                    LOGGER.warning(f.getName()+" expected "+expectedLength+" actual "+f.length());
                }
                retainLogfiles.remove(f.getName()); 
                continue;
            }
            String undelName = f.getName().replace(".del", ".jdb");
            if(retainLogfiles.containsKey(undelName)) {
                long expectedLength = retainLogfiles.get(undelName);
                if(f.length()!=expectedLength) {
                    LOGGER.warning(f.getName()+" expected "+expectedLength+" actual "+f.length());
                }
                if(!f.renameTo(new File(f.getParentFile(),undelName))) {
                    throw new IOException("Unable to rename " + f + " to " +
                            undelName);
                }
                retainLogfiles.remove(undelName); 
            }
            // not needed; move aside
            org.archive.util.FileUtils.moveAsideIfExists(f);
            // TODO: log/warn of ruined later checkpoints? 
        }
        if(retainLogfiles.size()>0) {
            // some needed files weren't present
            LOGGER.severe("needed log files missing: "+retainLogfiles);
        }
        
    }

    public void finishCheckpoint(Checkpoint checkpointInProgress) {}
     
    Checkpoint recoveryCheckpoint;
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint checkpoint) {
        this.recoveryCheckpoint = checkpoint; 
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
    
    public Database getDatabase(String name) {
        DatabasePlusConfig dpc = databases.get(name);
        if (dpc == null) {
            return null;
        }
        return dpc.database;
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
    public <K,V> DisposableStoredSortedMap<K, V> getStoredMap(String dbName, Class<K> keyClass, Class<V> valueClass, boolean allowDuplicates, boolean usePriorData) {
        BdbConfig config = new BdbConfig(); 
        config.setSortedDuplicates(allowDuplicates);
        config.setAllowCreate(!usePriorData); 
        Database mapDb;
        if(dbName==null) {
            dbName = "tempMap-"+System.identityHashCode(this)+"-"+sn;
            sn++;
        }
        final String openName = dbName; 
        try {
            mapDb = openDatabase(openName,config,usePriorData);
        } catch (DatabaseException e) {
            throw new RuntimeException(e); 
        } 
        EntryBinding<V> valueBinding = TupleBinding.getPrimitiveBinding(valueClass);
        if(valueBinding == null) {
            valueBinding = new SerialBinding<V>(classCatalog, valueClass);
        }
        DisposableStoredSortedMap<K,V> storedMap = new DisposableStoredSortedMap<K, V>(
                mapDb,
                TupleBinding.getPrimitiveBinding(keyClass),
                valueBinding,
                true) {
                    @Override
                    public void dispose() {
                        BdbModule.this.disposeDatabase(openName);
                    }
            ;
        };
        return storedMap; 
    }

}
