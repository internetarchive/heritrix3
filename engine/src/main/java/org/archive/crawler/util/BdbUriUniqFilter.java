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
package org.archive.crawler.util;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.bdb.BdbModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import st.ata.util.FPGenerator;

import com.sleepycat.bind.tuple.LongBinding;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DatabaseNotFoundException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.OperationStatus;


/**
 * A BDB implementation of an AlreadySeen list.
 * 
 * This implementation performs adequately without blowing out 
 * the heap. See
 * <a href="http://crawler.archive.org/cgi-bin/wiki.pl?AlreadySeen">AlreadySeen</a>.
 * 
 * <p>Makes keys that have URIs from same server close to each other.  Mercator
 * and 2.3.5 'Elminating Already-Visited URLs' in 'Mining the Web' by Soumen
 * Chakrabarti talk of a two-level key with the first 24 bits a hash of the
 * host plus port and with the last 40 as a hash of the path.  Testing
 * showed adoption of such a scheme halving lookup times (Tutilhis implementation
 * actually concatenates scheme + host in first 24 bits and path + query in
 * trailing 40 bits).
 * 
 * @author stack
 * @version $Date$, $Revision$
 */
public class BdbUriUniqFilter extends SetBasedUriUniqFilter 
implements Lifecycle, Serializable {
    private static final long serialVersionUID = -8099357538178524011L;

    private static Logger logger =
        Logger.getLogger(BdbUriUniqFilter.class.getName());

    protected boolean createdEnvironment = false;
    protected long lastCacheMiss = 0;
    protected long lastCacheMissDiff = 0;
    protected transient Database alreadySeen = null;
    protected transient DatabaseEntry value = null;
    static protected DatabaseEntry ZERO_LENGTH_ENTRY = 
        new DatabaseEntry(new byte[0]);
    private static final String DB_NAME = "alreadySeenUrl";
    protected long count = 0;
    private long aggregatedLookupTime = 0;
    
    private static final String COLON_SLASH_SLASH = "://";
    
    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }
    
    public BdbUriUniqFilter() {
    }
    
    boolean isRunning = false; 
    public void start() {
        if(isRunning()) {
            return; 
        }
        try {
            BdbModule.BdbConfig config = getDatabaseConfig();
            config.setAllowCreate(true);
            initialize(bdb.openManagedDatabase(DB_NAME, config, false));
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        }
        isRunning = true; 
    }
    
    public boolean isRunning() {
        return isRunning;
    }

    public void stop() {
        if(!isRunning()) {
            return; 
        }
        close();
        isRunning = false; 
    }
    
    /**
     * Constructor.
     * 
     * Only used for testing; usually no-arg constructor is used, and
     * environment provided by injected BdbModule. 
     * 
     * @param bdbEnv The directory that holds the bdb environment. Will
     * make a database under here if doesn't already exit.  Otherwise
     * reopens any existing dbs.
     * @throws IOException
     */
    public BdbUriUniqFilter(File bdbEnv)
    throws IOException {
        this(bdbEnv, -1);
    }
    
    /**
     * Constructor.
     * 
     * Only used for testing; usually no-arg constructor is used, and
     * environment provided by injected BdbModule. 
     * 
     * @param bdbEnv The directory that holds the bdb environment. Will
     * make a database under here if doesn't already exit.  Otherwise
     * reopens any existing dbs.
     * @param cacheSizePercentage Percentage of JVM bdb allocates as
     * its cache.  Pass -1 to get default cache size.
     * @throws IOException
     */
    public BdbUriUniqFilter(File bdbEnv, final int cacheSizePercentage)
    throws IOException {
        super();
        if (!bdbEnv.exists()) {
            bdbEnv.mkdirs();
        }
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        if (cacheSizePercentage > 0 && cacheSizePercentage < 100) {
            envConfig.setCachePercent(cacheSizePercentage);
        }
        try {
            createdEnvironment = true;
            Environment env = new Environment(bdbEnv, envConfig);
            BdbModule.BdbConfig config = getDatabaseConfig();
            config.setAllowCreate(true);
            try {
                env.truncateDatabase(null, DB_NAME, false);
            } catch (DatabaseNotFoundException e) {
                // ignored
            }
            Database db = env.openDatabase(null, DB_NAME, config.toDatabaseConfig());
            initialize(db);
        } catch (DatabaseException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        }
    }


    /**
     * Method shared by constructors.
     * @param env Environment to use.
     * @throws DatabaseException
     */
    protected void initialize(Database db) throws DatabaseException {
        open(db);
    }

    /**
     * @return DatabaseConfig to use
     */
    protected BdbModule.BdbConfig getDatabaseConfig() {
        BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
        return dbConfig;
    }
    
    /**
     * Call after deserializing an instance of this class.  Will open the
     * already seen in passed environment.
     * @param env DB Environment to use.
     * @throws DatabaseException
     */
    public void reopen(Database db)
    throws DatabaseException {
        open(db);
    }
    
    protected void open(final Database db)
    throws DatabaseException {
        this.alreadySeen = db;
        this.value = new DatabaseEntry("".getBytes());
    }
    
    public synchronized void close() {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Count of alreadyseen on close "
                    + Long.toString(count));
        }
        Environment env = null;
        if (this.alreadySeen != null) {
            try {
                env = this.alreadySeen.getEnvironment();
                alreadySeen.sync();
            } catch (DatabaseException e) {
                logger.severe(e.getMessage());
            }
        }
        if (env != null) {
            try {
                // This sync flushes whats in RAM. Its expensive operation.
                // Without, data can be lost. Not for transactional operation.
                env.sync();
            } catch (DatabaseException e) {
                logger.severe(e.getMessage());
            }
        }
        
        if (createdEnvironment) {
            // Only manually close database if it were created via a
            // constructor, and not via a BdbModule. Databases created by a 
            // BdbModule will be closed by that BdbModule.
            if (this.alreadySeen != null) {
                try {
                    alreadySeen.close();
                } catch (DatabaseException e) {
                    logger.severe(e.getMessage());
                }
            }
            if (env != null) {
                try {
                    env.close();
                } catch (DatabaseException e) {
                    logger.severe(e.getMessage());
                }
            }
        }
    }
    
    public synchronized long getCacheMisses() {
        if(alreadySeen==null) {
            return 0;
        }
        try {
            long cacheMiss = this.alreadySeen.getEnvironment().
            getStats(null).getNCacheMiss();
            // FIXME: get shouldn't define intervals (should be idempotent)
            this.lastCacheMissDiff = cacheMiss - this.lastCacheMiss;
            this.lastCacheMiss = cacheMiss;
            return this.lastCacheMiss;
        } catch (DatabaseException de) {
            return 0;
        }
        
    }
    
    public long getLastCacheMissDiff() {
        return this.lastCacheMissDiff;
    }
    
    /**
     * Create fingerprint.
     * Pubic access so test code can access createKey.
     * @param uri URI to fingerprint.
     * @return Fingerprint of passed <code>url</code>.
     */
    public static long createKey(CharSequence uri) {
        String url = uri.toString();
        int index = url.indexOf(COLON_SLASH_SLASH);
        if (index > 0) {
            index = url.indexOf('/', index + COLON_SLASH_SLASH.length());
        }
        CharSequence hostPlusScheme = (index == -1)? url: url.subSequence(0, index);
        long tmp = FPGenerator.std24.fp(hostPlusScheme);
        return tmp | (FPGenerator.std40.fp(url) >>> 24);
    }



    protected boolean setAdd(CharSequence uri) {
        DatabaseEntry key = new DatabaseEntry();
        LongBinding.longToEntry(createKey(uri), key);
        long started = 0;
        
        OperationStatus status = null;
        try {
            if (logger.isLoggable(Level.INFO)) {
                started = System.currentTimeMillis();
            }
            status = alreadySeen.putNoOverwrite(null, key, ZERO_LENGTH_ENTRY);
            if (logger.isLoggable(Level.INFO)) {
                aggregatedLookupTime +=
                    (System.currentTimeMillis() - started);
            }
        } catch (DatabaseException e) {
            logger.severe(e.getMessage());
        }
        if (status == OperationStatus.SUCCESS) {
            count++;
            if (logger.isLoggable(Level.INFO)) {
                final int logAt = 10000;
                if (count > 0 && ((count % logAt) == 0)) {
                    logger.info("Average lookup " +
                        (aggregatedLookupTime / logAt) + "ms.");
                    aggregatedLookupTime = 0;
                }
            }
        }
        if(status == OperationStatus.KEYEXIST) {
            return false; // not added
        } else {
            return true;
        }
    }

    protected long setCount() {
        return count;
    }

    protected boolean setRemove(CharSequence uri) {
        DatabaseEntry key = new DatabaseEntry();
        LongBinding.longToEntry(createKey(uri), key);
            OperationStatus status = null;
        try {
            status = alreadySeen.delete(null, key);
        } catch (DatabaseException e) {
            logger.severe(e.getMessage());
        }
        if (status == OperationStatus.SUCCESS) {
            count--;
            return true; // removed
        } else {
            return false; // not present
        }
    }

    public long flush() {
    	    // We always write but this might be place to do the sync
        // when checkpointing?  TODO.
        return 0;
    }

    private void writeObject(ObjectOutputStream output) throws IOException {
        // sync deferred-write database
        try {
            alreadySeen.sync();
        } catch (DatabaseException e) {
            // TODO Auto-generated catch block
            throw new RuntimeException(e);
        }
        output.defaultWriteObject();
    }

    private void readObject(ObjectInputStream input) 
    throws IOException, ClassNotFoundException {
        input.defaultReadObject();        

        try {
            BdbModule.BdbConfig config = getDatabaseConfig();
            config.setAllowCreate(false);
            reopen(bdb.getDatabase(DB_NAME));
        } catch (DatabaseException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        }
    }

    

}