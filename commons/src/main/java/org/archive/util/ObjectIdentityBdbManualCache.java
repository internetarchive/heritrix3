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

import java.io.Closeable;
import java.io.File;
import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.bdb.KryoBinding;

import com.google.common.collect.MapEvictionListener;
import com.google.common.collect.MapMaker;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;

/**
 * A BDB JE backed object cache. 
 * 
 * Soft references to previously-instantiated objects are held so that
 * unless/until an object is garbage collected, subsequent get()s will
 * return the exact same object (avoiding redundant creation or disagreement
 * about canonical object state). 
 * <p/>
 * The backing disk is only guaranteed to be up-to-date after a flush 
 * of all dirty values to disk, as can be forced by sync().
 * <p/>
 * 
 * <p/>
 * 
 * @author John Erik Halse
 * @author stack
 * @author gojomo
 * @author paul baclace (conversion to ConcurrentMap)
 *  
 */
public class ObjectIdentityBdbManualCache<V extends IdentityCacheable> 
implements ObjectIdentityCache<V>, Closeable, Serializable, MapEvictionListener<String, V> {
    private static final long serialVersionUID = 1L;
    private static final Logger logger =
        Logger.getLogger(ObjectIdentityBdbManualCache.class.getName());

    /** The BDB JE database used for this instance. */
    protected transient Database db;

    /** in-memory map of new/recent/still-referenced-elsewhere instances */
    protected transient ConcurrentMap<String,V> memMap;

    /** The Collection view of the BDB JE database used for this instance. */
    protected transient StoredSortedMap<String, V> diskMap;

    protected transient ConcurrentMap<String,V> dirtyItems;
    
    protected AtomicLong count;
    
    //
    // USAGE STATS
    //
    /** Count of times we got an object from in-memory cache */
    private AtomicLong cacheHit = new AtomicLong(0);
    /** Count of times the {@link ObjectIdentityBdbManualCache#get} method was called. */
    private AtomicLong countOfGets = new AtomicLong(0);
    /** Count of every time disk-based map provided non-null object */ 
    private AtomicLong diskHit = new AtomicLong(0);
    /** Count of times Supplier was used for new object */
    private AtomicLong supplierUsed = new AtomicLong(0);
    /** count of {@link #sync()} use */
    transient private AtomicLong useStatsSyncUsed = new AtomicLong(0);
    /** Count of times Supplier was used for new object */
    private AtomicLong evictions = new AtomicLong(0);

    /**
     * Constructor. You must call
     * {@link #initialize(Environment, Class, Class, StoredClassCatalog)}
     * to finish construction. Construction is two-stepped to support
     * reconnecting a deserialized CachedBdbMap with its backing bdbje
     * database.
     * 
     * @param dbName Name of the backing db this instance should use.
     */
    public ObjectIdentityBdbManualCache() {
        super();
    }
    
    /**
     * Call this method when you have an instance when you used the
     * default constructor or when you have a deserialized instance that you
     * want to reconnect with an extant bdbje environment.  Do not
     * call this method if you used the
     * {@link #CachedBdbMap(File, String, Class, Class)} constructor.
     * @param env
     * @param keyClass
     * @param valueClass
     * @param classCatalog
     * @throws DatabaseException
     */
    public void initialize(final Environment env, String dbName,
            final Class valueClass, final StoredClassCatalog classCatalog)
    throws DatabaseException {
        // TODO: tune capacity for actual threads, expected size of key caches? 
        this.memMap = new MapMaker().concurrencyLevel(64).initialCapacity(8192).softValues().makeMap();    
        this.db = openDatabase(env, dbName);
        this.diskMap = createDiskMap(this.db, classCatalog, valueClass);
        // keep a record of items that must be persisted; auto-persist if 
        // unchanged after 5 minutes, or more than 10K would collect
        this.dirtyItems = new MapMaker().concurrencyLevel(64)
            .maximumSize(10000).expireAfterWrite(5,TimeUnit.MINUTES)
            .evictionListener(this).makeMap();
            
        this.count = new AtomicLong(diskMap.size());
    }

    @SuppressWarnings("unchecked")
    protected StoredSortedMap<String, V> createDiskMap(Database database,
            StoredClassCatalog classCatalog, Class valueClass) {
        EntryBinding keyBinding = TupleBinding.getPrimitiveBinding(String.class);
        EntryBinding valueBinding = TupleBinding.getPrimitiveBinding(valueClass);
        if(valueBinding == null) {
            valueBinding = 
                new KryoBinding<V>(valueClass);
//                new SerialBinding(classCatalog, valueClass);
//                new BenchmarkingBinding<V>(new EntryBinding[] {
//                      new KryoBinding<V>(valueClass),                   
//                      new RecyclingSerialBinding<V>(classCatalog, valueClass),
//                  }, valueClass);
        }
        return new StoredSortedMap<String,V>(database, keyBinding, valueBinding, true);
    }

    protected Database openDatabase(final Environment environment,
            final String dbName) throws DatabaseException {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
        return environment.openDatabase(null, dbName, dbConfig);
    }

    /* (non-Javadoc)
     * @see org.archive.util.ObjectIdentityCache#close()
     */
    public synchronized void close() {
        // Close out my bdb db.
        if (this.db != null) {
            try {
                sync(); 
                this.db.sync();
                this.db.close();
            } catch (DatabaseException e) {
                logger.log(Level.WARNING,"problem closing ObjectIdentityBdbCache",e);
            } finally {
                this.db = null;
            }
        }
    }

    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    /* (non-Javadoc)
     * @see org.archive.util.ObjectIdentityCache#get(java.lang.String)
     */
    public V get(final String key) {
        return getOrUse(key,null); 
    }
    
    /* (non-Javadoc)
     * @see org.archive.util.ObjectIdentityCache#get(java.lang.String, org.archive.util.ObjectIdentityBdbCache)
     */
    public V getOrUse(final String key, Supplier<V> supplierOrNull) {
        countOfGets.incrementAndGet();
        
        if (countOfGets.get() % 10000 == 0) {
            logCacheSummary();
        }
        
        // check mem cache
        V val = memMap.get(key);
        if(val != null) {
            // the concurrent garden path: in memory and valid
            cacheHit.incrementAndGet();
            val.setIdentityCache(this); 
            return val;
        }
        val = diskMap.get(key);
        V prevVal; 
        if(val == null) {
            // never yet created, consider creating
            if(supplierOrNull==null) {
                return null;
            }
            val = supplierOrNull.get();
            supplierUsed.incrementAndGet();
            // putting initial value directly into diskMap
            // (rather than just the memMap until page-out)
            // ensures diskMap.keySet() provides complete view
            prevVal = diskMap.putIfAbsent(key, val); 
            if(prevVal!=null) {
                // we lost a race; discard our local creation in favor of disk version
                diskHit.incrementAndGet();
                val = prevVal;
            } else {
                // we uniquely added a new key
                count.incrementAndGet();
            }
        } else {
            diskHit.incrementAndGet();
        }
        
        prevVal = memMap.putIfAbsent(key, val); // fill memMap or lose race gracefully
        if(prevVal != null) {
            val = prevVal; 
        }
        val.setIdentityCache(this); 
        return val; 
    }

    /* (non-Javadoc)
     * @see org.archive.util.ObjectIdentityCache#keySet()
     */
    public Set<String> keySet() {
        return diskMap.keySet();
    }
    
    /**
     * Summary to log, if at FINE level
     */
    private void logCacheSummary() {
        if (logger.isLoggable((Level.FINE))) {
            logger.fine(composeCacheSummary());
        }
    }
    
    protected String composeCacheSummary() {
        long totalHits = cacheHit.get() + diskHit.get();
        if (totalHits < 1) {
            return "";
        }
        long cacheHitPercent 
                = (cacheHit.get() * 100) / totalHits;
        StringBuilder sb = new StringBuilder(120);
        sb.append("DB name:")
          .append(getDatabaseName())
          .append(", ")
          .append(" hit%: ")
          .append(cacheHitPercent)
          .append("%, gets=")
          .append(countOfGets.get())
          .append(" memHits=")
          .append(cacheHit.get())
          .append(" diskHits=")
          .append(diskHit.get())
          .append(" supplieds=")
          .append(supplierUsed.get())
          .append(" inMemItems=")
          .append(memMap.size())
          .append(" dirtyItems=")
          .append(dirtyItems.size())
          .append(" evictions=")
          .append(evictions.get())
          .append(" syncs=")
          .append(useStatsSyncUsed.get());
        return sb.toString();
    }

    /* (non-Javadoc)
     * @see org.archive.util.ObjectIdentityCache#size()
     */
    public int size() {
        if(db==null) {
            return 0; 
        }
        return (int) count.get();
    }
    
    protected String getDatabaseName() {
        String name = "DbName-Lookup-Failed";
        try {
            if (this.db != null) {
                name = this.db.getDatabaseName();
            }
        } catch (DatabaseException e) {
            // Ignore.
        }
        return name;
    }
    
    /**
     * Sync all in-memory map entries to backing disk store.
     */
    public synchronized void sync() {
        String dbName = null;
        // Sync. memory and disk.
        useStatsSyncUsed.incrementAndGet();
        long startTime = 0;
        if (logger.isLoggable(Level.FINE)) {
            dbName = getDatabaseName();
            startTime = System.currentTimeMillis();
            logger.fine(dbName + " start sizes: disk " + this.diskMap.size() +
                ", mem " + this.memMap.size());
        }
        
        Iterator<Entry<String, V>> iter = dirtyItems.entrySet().iterator();
        while(iter.hasNext()) {
            Entry<String, V> entry = iter.next(); 
            iter.remove();
            diskMap.put(entry.getKey(), entry.getValue());
        }
        
        try {
            this.db.sync();
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        
        
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(dbName + " sync took " +
                (System.currentTimeMillis() - startTime) + "ms. " +
                "Finish sizes: disk " +
                this.diskMap.size() + ", mem " + this.memMap.size());
        }
    }

    @Override
    public void dirtyKey(String key) {
       V val = memMap.get(key);
       if(val==null) {
           logger.severe("dirty key not in memory should be impossible");
       }
       dirtyItems.put(key,val); 
    }

    @Override
    public void onEviction(String key, V val) {
        evictions.incrementAndGet();
        diskMap.put(key, val);
    }
}
