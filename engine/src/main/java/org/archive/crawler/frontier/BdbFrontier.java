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
package org.archive.crawler.frontier;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.openmbean.CompositeData;

import org.apache.commons.collections.Closure;
import org.archive.bdb.BdbModule;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.modules.CrawlURI;
import org.archive.queue.StoredQueue;
import org.archive.util.ArchiveUtils;
import org.archive.util.Supplier;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * A Frontier using several BerkeleyDB JE Databases to hold its record of
 * known hosts (queues), and pending URIs. 
 *
 * @author Gordon Mohr
 */
public class BdbFrontier extends WorkQueueFrontier 
implements Serializable, Checkpointable, BeanNameAware {
    private static final long serialVersionUID = 1L;

    private static final Logger logger =
        Logger.getLogger(BdbFrontier.class.getName());

    /** 
     * All 'inactive' queues, not yet in active rotation.
     * Linked-list of keys for the queues.
     */
    protected SortedMap<Integer,Queue<String>> inactiveQueuesByPrecedence;

    /**
     * 'retired' queues, no longer considered for activation.
     * Linked-list of keys for queues.
     */
    protected StoredQueue<String> retiredQueues;
    
    /** all URIs scheduled to be crawled */
    protected transient BdbMultipleWorkQueues pendingUris;

    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }
    
    String beanName; 
    public void setBeanName(String name) {
        this.beanName = name;
    }
    
    boolean dumpPendingAtClose = false; 
    public boolean getDumpPendingAtClose() {
        return dumpPendingAtClose;
    }
    public void setDumpPendingAtClose(boolean dumpPendingAtClose) {
        this.dumpPendingAtClose = dumpPendingAtClose;
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.WorkQueueFrontier#getInactiveQueuesByPrecedence()
     */
    @Override
    SortedMap<Integer, Queue<String>> getInactiveQueuesByPrecedence() {
        return inactiveQueuesByPrecedence;
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.WorkQueueFrontier#getRetiredQueues()
     */
    @Override
    Queue<String> getRetiredQueues() {
        return retiredQueues;
    }
    
    /**
     * Create the single object (within which is one BDB database)
     * inside which all the other queues live. 
     * 
     * @return the created BdbMultipleWorkQueues
     * @throws DatabaseException
     */
    protected BdbMultipleWorkQueues createMultipleWorkQueues()
    throws DatabaseException {
        Database db;
        boolean recycle = (recoveryCheckpoint != null);

        BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
        dbConfig.setAllowCreate(!recycle);
        // Make database deferred write: URLs that are added then removed 
        // before a page-out is required need never cause disk IO.
        db = bdb.openManagedDatabase("pending", dbConfig, recycle);
        
        return new BdbMultipleWorkQueues(db, bdb.getClassCatalog());
    }


    /**
     * Return the work queue for the given CrawlURI's classKey. URIs
     * are ordered and politeness-delayed within their 'class'.
     * 
     * @param curi CrawlURI to base queue on
     * @return the found or created BdbWorkQueue
     */
    protected WorkQueue getQueueFor(CrawlURI curi) {
        String classKey = curi.getClassKey();
        return getQueueFor(classKey);
    }
    
    /**
     * Return the work queue for the given classKey, or null
     * if no such queue exists.
     * 
     * @param classKey key to look for
     * @return the found WorkQueue
     */
    protected WorkQueue getQueueFor(final String classKey) {      
        WorkQueue wq = allQueues.getOrUse(
                classKey,
                new Supplier<WorkQueue>() {
                    public WorkQueue get() {
                        String qKey = new String(classKey); // ensure private minimal key
                        WorkQueue q = new BdbWorkQueue(qKey, BdbFrontier.this);
                        q.setTotalBudget(getQueueTotalBudget()); 
                        getQueuePrecedencePolicy().queueCreated(q);
                        return q;
                    }});
        return wq;
    }


    /**
     * Return list of urls.
     * @param marker
     * @param numberOfMatches
     * @param verbose 
     * @return List of URIs (strings).
     */
    public CompositeData getURIsList(String marker, 
            int numberOfMatches, String pattern, final boolean verbose) {
        try {
            Pattern p = Pattern.compile(pattern);
            return pendingUris.getFrom(marker, numberOfMatches, p, verbose);
        } catch (DatabaseException e) {
            throw new IllegalStateException(e);
        }
    }
    
    protected void closeQueue() {
        // before closing/releasing, dump if requested
        if (getDumpPendingAtClose()) {
            try {
                dumpAllPendingToLog();
            } catch (DatabaseException e) {
                logger.log(Level.WARNING, "dump pending problem", e);
            }
        }
        ArchiveUtils.closeQuietly(pendingUris);
    }
        
    protected BdbMultipleWorkQueues getWorkQueues() {
        return pendingUris;
    }

    protected boolean workQueueDataOnDisk() {
        return true;
    }

    public BdbFrontier() {
        super();
    }
    
    public void startCheckpoint(Checkpoint checkpointInProgress) {}

    public void doCheckpoint(Checkpoint checkpointInProgress) {
        // An explicit sync on the any deferred write dbs is needed to make the
        // db recoverable. Sync'ing the environment doesn't work.
        this.pendingUris.sync();
        // object caches will be sync()d by BdbModule
        
        JSONObject json = new JSONObject();
        try {
            json.put("queuedUriCount", queuedUriCount.get());
            json.put("succeededFetchCount", succeededFetchCount.get());
            json.put("failedFetchCount", failedFetchCount.get());
            json.put("disregardedUriCount", disregardedUriCount.get());
            json.put("totalProcessedBytes", totalProcessedBytes.get());
            checkpointInProgress.saveJson(beanName, json);
        } catch (JSONException e) {
            // impossible
            throw new RuntimeException(e);
        }
    }

    public void finishCheckpoint(Checkpoint checkpointInProgress) {}

    Checkpoint recoveryCheckpoint;
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint checkpoint) {
        this.recoveryCheckpoint = checkpoint; 
    }
    
    @Override
    protected void initAllQueues() throws DatabaseException {
        boolean isRecovery = (recoveryCheckpoint != null);
        this.allQueues = bdb.getObjectCache("allqueues", isRecovery, WorkQueue.class);
        if(isRecovery) {
            JSONObject json = recoveryCheckpoint.loadJson(beanName);
            try {
                queuedUriCount.set(json.getLong("queuedUriCount"));
                succeededFetchCount.set(json.getLong("succeededFetchCount"));
                failedFetchCount.set(json.getLong("failedFetchCount"));
                disregardedUriCount.set(json.getLong("disregardedUriCount"));
                totalProcessedBytes.set(json.getLong("totalProcessedBytes"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }            
            // restore WorkQueues to internal management queues
            enqueueOrDo(new Recover());
        }
    }
    
    /**
     * Frontier managerThread action to restore the placement of
     * all queues to either the 'retired' collection or one of the
     * inactive tiers (from which they will become ready/active as
     * necessary). 
     */
    public class Recover extends InEvent {
        @Override
        public void process() {
            // restore WorkQueues to internal management queues
            for (String key : allQueues.keySet()) {
                WorkQueue q = allQueues.get(key);
                q.getOnInactiveQueues().clear();
                q.setSessionBalance(0); 
                if(q.isRetired()) {
                    getRetiredQueues().add(key); 
                } else {
                    deactivateQueue(q);
                }
            }
        }
    }
    
    @Override
    protected void initOtherQueues() throws DatabaseException {
        // small risk of OutOfMemoryError: if 'hold-queues' is false,
        // readyClassQueues may grow in size without bound
        readyClassQueues = new LinkedBlockingQueue<String>();

        inactiveQueuesByPrecedence = new TreeMap<Integer,Queue<String>>();
        
        Database retiredQueuesDb;
        retiredQueuesDb = bdb.openManagedDatabase("retiredQueues", 
                StoredQueue.databaseConfig(), false);
        retiredQueues = new StoredQueue<String>(retiredQueuesDb,
                String.class, null);

        // small risk of OutOfMemoryError: in large crawls with many 
        // unresponsive queues, an unbounded number of snoozed queues 
        // may exist
        snoozedClassQueues = new DelayQueue<DelayedWorkQueue>();
        
        // initialize master map in which other queues live
        this.pendingUris = createMultipleWorkQueues();
    }


    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.WorkQueueFrontier#createInactiveQueueForPrecedence(int)
     */
    @Override
    Queue<String> createInactiveQueueForPrecedence(int precedence) {
        Database inactiveQueuesDb;
        try {
            inactiveQueuesDb = bdb.openManagedDatabase("inactiveQueues-"+precedence,
                    StoredQueue.databaseConfig(), false);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        return new StoredQueue<String>(inactiveQueuesDb,
                String.class, null);
    }
    
    private void readObject(ObjectInputStream in) 
    throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        // rehook StoredQueues to their databases
        for(int precedenceKey : inactiveQueuesByPrecedence.keySet()) {
            Database inactiveQueuesDb = 
                bdb.getDatabase("inactiveQueues-"+precedenceKey);
            ((StoredQueue<String>)inactiveQueuesByPrecedence.get(precedenceKey))
                .hookupDatabase(inactiveQueuesDb, String.class, null);
        }
        
        // rehook retiredQueues to its database
        Database retiredQueuesDb = bdb.getDatabase("retiredQueues");
        retiredQueues.hookupDatabase(retiredQueuesDb, String.class, null);

        try {
            this.pendingUris = new BdbMultipleWorkQueues(bdb.getDatabase("pending"), 
                    bdb.getClassCatalog());
        } catch (DatabaseException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        }
        startManagerThread();
    }
    
    /**
     * Dump all still-enqueued URIs to the crawl.log -- without actually
     * dequeuing. Useful for understanding what was remaining in a crawl that
     * was ended early, for example at a time limit.
     * 
     * @throws DatabaseException
     */
    public void dumpAllPendingToLog() throws DatabaseException {
        Closure tolog = new Closure() {
            public void execute(Object curi) {
                log((CrawlURI) curi);
            }
        };
        pendingUris.forAllPendingDo(tolog);
    }
}
