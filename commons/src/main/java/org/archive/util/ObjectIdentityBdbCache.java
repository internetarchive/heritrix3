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
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.bdb.KryoBinding;

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
 * return the exact same object. (If all outside references are lost,
 * when the soft reference is broken, the object state -- still 
 * accessible to this class via reflective access to a phantom 
 * referent --is flushed to disk. The next get() will reconsitute a new
 * object, from the disk state.)
 * <p/>
 * The backing disk is only guaranteed to be up-to-date after a flush 
 * of all in-memory values to disk, as can be forced by sync().
 * <p/>
 * To ensure that changes/mutations to values in this map are coherent and
 * consistent at the application level, it is assumed that the application
 * level only mutates values that are in this map and does not retain references
 * to values longer than necessary.  This allows mappings to be persisted
 * during GC without explicit transactions or write operations.
 * <p/>
 * Based on the earlier CachedBdbMap. 
 * <p/>
 * 
 * @author John Erik Halse
 * @author stack
 * @author gojomo
 * @author paul baclace (conversion to ConcurrentMap)
 *  
 */
public class ObjectIdentityBdbCache<V extends IdentityCacheable> 
implements ObjectIdentityCache<V>, Closeable, Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger =
        Logger.getLogger(ObjectIdentityBdbCache.class.getName());

    /** The BDB JE database used for this instance. */
    protected transient Database db;

    /** in-memory map of new/recent/still-referenced-elsewhere instances */
    protected transient ConcurrentHashMap<String,SoftEntry<V>> memMap;
    protected transient ReferenceQueue<V> refQueue;

    /** The Collection view of the BDB JE database used for this instance. */
    protected transient StoredSortedMap<String, V> diskMap;

    protected AtomicLong count;
    
    //
    // USAGE STATS
    //
    /** Count of times we got an object from in-memory cache */
    private AtomicLong cacheHit = new AtomicLong(0);
    /** Count of times the {@link ObjectIdentityBdbCache#get} method was called. */
    private AtomicLong countOfGets = new AtomicLong(0);
    /** Count of every time disk-based map provided non-null object */ 
    private AtomicLong diskHit = new AtomicLong(0);
    /** Count of times Supplier was used for new object */
    private AtomicLong supplierUsed = new AtomicLong(0);
    /** count of expunge put() to BDB (implies disk) */
    private AtomicLong expungeStatsDiskPut = new AtomicLong(0);
    /** count of {@link #sync()} use */
    transient private AtomicLong useStatsSyncUsed = new AtomicLong(0);
    
    /** Reference to the Reference#referent Field. */
    protected static Field referentField;
    static {
        // We need access to the referent field in the PhantomReference.
        // For more on this trick, see
        //
        // http://www.javaspecialists.co.za/archive/Issue098.html and for
        // discussion:
        // http://www.theserverside.com/tss?service=direct/0/NewsThread/threadViewer.markNoisy.link&sp=l29865&sp=l146901
        try {
            referentField = Reference.class.getDeclaredField("referent");
            referentField.setAccessible(true);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Constructor. You must call
     * {@link #initialize(Environment, Class, Class, StoredClassCatalog)}
     * to finish construction. Construction is two-stepped to support
     * reconnecting a deserialized CachedBdbMap with its backing bdbje
     * database.
     * 
     * @param dbName Name of the backing db this instance should use.
     */
    public ObjectIdentityBdbCache() {
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
        // TODO: initial capacity should be related to number of seeds, max depth, max docs
        this.memMap = new ConcurrentHashMap<String,SoftEntry<V>>(
                                                            8192, // initial capacity
                                                            0.9f, // acceptable load factor
                                                            64 // est. number of concurrent threads
                                                            ); 
        this.refQueue = new ReferenceQueue<V>();
        canary = new SoftReference<LowMemoryCanary>(new LowMemoryCanary());
        
        this.db = openDatabase(env, dbName);
        this.diskMap = createDiskMap(this.db, classCatalog, valueClass);
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
        SoftEntry<V> entry = memMap.get(key);
        if(entry != null) {
            V val = entry.get();
            if(val != null) {
                // the concurrent garden path: in mem, valid
                cacheHit.incrementAndGet();
                val.setIdentityCache(this); 
                return val;
            } 
        }
        
        // everything in other difficult cases happens inside this block
        synchronized(this) {
            // recheck mem cache -- if another thread beat us into sync 
            // block and already filled the key 
            entry = memMap.get(key);
            if(entry != null) {
                V val = entry.get();
                if(val != null) {
                    cacheHit.incrementAndGet();
                    val.setIdentityCache(this); 
                    return val;
                } 
            }
            // persist to disk all ref-enqueued stale (soft-ref-cleared) entries now
            pageOutStaleEntries();
            // and catch if this exact entry not yet ref-enqueued 
            if(memMap.get(key)!=null) {
                pageOutStaleEntry(entry);
                if(memMap.get(key)!=null) {
                    logger.log(Level.SEVERE,"nulled key "+key+" not paged-out", new Exception());
                }
            }
            
            // check disk 
            V valDisk = (V) diskMap.get(key); 
            if(valDisk==null) {
                // never yet created, consider creating
                if(supplierOrNull==null) {
                    return null;
                }
                // create using provided Supplier
                valDisk = supplierOrNull.get();
                supplierUsed.incrementAndGet();
                // putting initial value directly into diskMap
                // (rather than just the memMap until page-out)
                // ensures diskMap.keySet() provides complete view
                V prevVal = diskMap.putIfAbsent(key, valDisk); 
                count.incrementAndGet();
                if(prevVal!=null) {
                    // ERROR: diskMap modification since previous
                    // diskMap.get() should be impossible
                    logger.log(Level.SEVERE,"diskMap modified outside synchronized block?");
                }
            } else {
                diskHit.incrementAndGet();
            }

            // keep new val in memMap
            SoftEntry<V> newEntry = new SoftEntry<V>(key, valDisk, refQueue);
            SoftEntry<V> prevVal = memMap.putIfAbsent(key, newEntry); 
            if(prevVal != null) {
                // ERROR: memMap modification since previous 
                // memMap.get() should be impossible
                logger.log(Level.SEVERE,"memMap modified outside synchronized block?", new Exception());
            }
            valDisk.setIdentityCache(this); 
            return valDisk; 
        }
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
          .append(" expungePuts=")
          .append(expungeStatsDiskPut.get())
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
        
        for (String key : this.memMap.keySet()) {
            SoftEntry<V> entry = memMap.get(key);
            if (entry != null) {
                // Get & hold so not cleared pre-return.
                V value = entry.get();
                if (value != null) {
                    expungeStatsDiskPut.incrementAndGet();
                    this.diskMap.put(key, value); // unchecked cast
                } 
            }
        }
        pageOutStaleEntries();
        
        // force sync of deferred-writes
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
        // do nothing, because our weak/phantom trickery is supposed to
        // ensure sync-to-persistence if/when dereferenced and collected
    }

    /** An incremental, poll-based expunger.
     * 
     * Package-protected for unit-test visibility. 
     */
    @SuppressWarnings("unchecked")
    protected synchronized void pageOutStaleEntries() {
        int c = 0;
        long startTime = System.currentTimeMillis();
        for(SoftEntry<V> entry; (entry = (SoftEntry<V>)refQueue.poll()) != null;) {
            pageOutStaleEntry(entry);
            c++;
        }
        if (c > 0 && logger.isLoggable(Level.FINER)) {
            long endTime = System.currentTimeMillis();
            try {
                logger.finer("DB: " + db.getDatabaseName() + ",  Expunged: "
                        + c + ", Diskmap size: " + diskMap.size()
                        + ", Cache size: " + memMap.size()
                        + ", in "+(endTime-startTime)+"ms");
            } catch (DatabaseException e) {
                logger.log(Level.FINER,"exception while logging",e);
            }
        }
    }
    
    /** 
     * Expunge an entry from memMap while updating diskMap.
     * 
     * @param entry a SoftEntry<V> obtained from refQueuePoll()
     */
   synchronized private void pageOutStaleEntry(SoftEntry<V> entry) {
        PhantomEntry<V> phantom = entry.phantom;
        
        // Still in memMap? if not, was paged-out by earlier direct access
        // before placed into reference-queue; just return
        if (memMap.get(phantom.key) != entry) { // NOTE: intentional identity compare
            return; 
        }
        
        // recover hidden value
        V phantomValue = phantom.doctoredGet(); 

        // Expected value present? (should be; only clear is at end of
        // this method, after entry removal from memMap)
        if(phantomValue == null) {
            logger.log(Level.WARNING,"unexpected null phantomValue", new Exception());
            return; // nothing to do
        }
        
        // given instance entry still in memMap;
        // we have the key and phantom Value, 
        // the diskMap can be updated.
        diskMap.put(phantom.key, phantomValue); // unchecked cast
        expungeStatsDiskPut.incrementAndGet();
        
        //  remove memMap entry 
        boolean removed = memMap.remove(phantom.key, entry);
        if(!removed) {
            logger.log(Level.WARNING,"expunge memMap.remove() ineffective",new Exception());
        }
        phantom.clear(); // truly allows GC of unreferenced V object
    }
    
    private static class PhantomEntry<V> extends PhantomReference<V> {
        protected final String key;

        public PhantomEntry(String key, V referent) {
            super(referent, null);
            this.key = key;
        }

        /**
         * @return Return the referent. The contract for {@link #get()}
         * always returns a null referent.  We've cheated and doctored
         * PhantomReference to return the actual referent value.  See notes
         * at {@link #referentField};
         */
        @SuppressWarnings("unchecked")
        final public V doctoredGet() {
            try {
                // Here we use the referentField saved off on static
                // initialization of this class to get at this References'
                // private referent field.
                return (V) referentField.get(this);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** 
     * SoftReference cache entry.
     * 
     * A PhantomReference is used to hold the key and value as a last
     * chance before GC hook that can effect the update of diskMap.
     * <p/>
     * Entries are not recycled.
     */
    private static class SoftEntry<V> extends SoftReference<V> {
        PhantomEntry<V> phantom;

        public SoftEntry(String key, V referent, ReferenceQueue<V> q) {
            super(referent, q);
            this.phantom = new PhantomEntry<V>(key, referent);
        }

        public V get() {
            // ensure visibility 
            synchronized (this) {
                return super.get();
            }
        }

        public String toString() {
            if (phantom != null) {
                return "SoftEntry(key=" + phantom.key + ")";
            } else {
                return "SoftEntry()";
            }
        }
    }

    //
    // Crude, probably unreliable/fragile but harmless mechanism to 
    // trigger expunge of cleared SoftReferences in low-memory 
    // conditions even without any of the other get/put triggers. 
    //
    
    protected transient SoftReference<LowMemoryCanary> canary;
    protected class LowMemoryCanary {
        /** When collected/finalized -- as should be expected in 
         *  low-memory conditions -- trigger an expunge and a 
         *  new 'canary' insertion. */
        public void finalize() {
            ObjectIdentityBdbCache.this.pageOutStaleEntries();
//            System.err.println("CANARY KILLED - "+ObjectIdentityBdbCache.this);
            // only install new canary if map still 'open' with db reference
            if(ObjectIdentityBdbCache.this.db !=null) {
                ObjectIdentityBdbCache.this.canary = 
                    new SoftReference<LowMemoryCanary>(new LowMemoryCanary());
            } else {
                ObjectIdentityBdbCache.this.canary = null; 
            }
        }
    }
}
