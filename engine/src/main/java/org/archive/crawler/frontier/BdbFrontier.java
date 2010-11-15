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

import java.util.Queue;
import java.util.SortedMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.openmbean.CompositeData;

import org.apache.commons.collections.Closure;
import org.archive.bdb.BdbModule;
import org.archive.bdb.DisposableStoredSortedMap;
import org.archive.bdb.StoredQueue;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.modules.CrawlURI;
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
implements Checkpointable, BeanNameAware {
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
        db = bdb.openDatabase("pending", dbConfig, recycle);
        
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
                    public BdbWorkQueue get() {
                        String qKey = new String(classKey); // ensure private minimal key
                        BdbWorkQueue q = new BdbWorkQueue(qKey, BdbFrontier.this);
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
    
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.AbstractFrontier#finalTasks()
     */
    @Override
    protected void finalTasks() {
        super.finalTasks();
        // before closing/releasing, dump if requested
        if (getDumpPendingAtClose()) {
            try {
                dumpAllPendingToLog();
            } catch (DatabaseException e) {
                logger.log(Level.WARNING, "dump pending problem", e);
            }
        }
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.WorkQueueFrontier#close()
     */
    @Override 
    public void close() {
        ArchiveUtils.closeQuietly(pendingUris);
        super.close(); 
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
    
    public void startCheckpoint(Checkpoint checkpointInProgress) {
        dispositionInProgressLock.writeLock().lock();
    }

    public void doCheckpoint(Checkpoint checkpointInProgress) {
        // An explicit sync on the any deferred write dbs is needed to make the
        // db recoverable. Sync'ing the environment doesn't work.
        this.pendingUris.sync();
        // object caches will be sync()d by BdbModule
        
        JSONObject json = new JSONObject();
        try {
            json.put("nextOrdinal", nextOrdinal.get());
            json.put("queuedUriCount", queuedUriCount.get());
            json.put("futureUriCount", futureUriCount.get());
            json.put("succeededFetchCount", succeededFetchCount.get());
            json.put("failedFetchCount", failedFetchCount.get());
            json.put("disregardedUriCount", disregardedUriCount.get());
            json.put("totalProcessedBytes", totalProcessedBytes.get());
            checkpointInProgress.saveJson(beanName, json);
        } catch (JSONException e) {
            // impossible
            throw new RuntimeException(e);
        }
        if(this.recover!=null) {
            recover.rotateForCheckpoint(checkpointInProgress);
        }
    }

    public void finishCheckpoint(Checkpoint checkpointInProgress) {
        dispositionInProgressLock.writeLock().unlock();
    }

    Checkpoint recoveryCheckpoint;
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint checkpoint) {
        this.recoveryCheckpoint = checkpoint; 
    }
    
    @Override
    protected void initAllQueues() throws DatabaseException {
        boolean isRecovery = (recoveryCheckpoint != null);
        this.allQueues = bdb.getObjectCache("allqueues", isRecovery, WorkQueue.class, BdbWorkQueue.class);
        if(isRecovery) {
            JSONObject json = recoveryCheckpoint.loadJson(beanName);
            try {
                nextOrdinal.set(json.getLong("nextOrdinal"));
                queuedUriCount.set(json.getLong("queuedUriCount"));
                futureUriCount.set(json.getLong("futureUriCount"));
                succeededFetchCount.set(json.getLong("succeededFetchCount"));
                failedFetchCount.set(json.getLong("failedFetchCount"));
                disregardedUriCount.set(json.getLong("disregardedUriCount"));
                totalProcessedBytes.set(json.getLong("totalProcessedBytes"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }            
            // restore WorkQueues to internal management queues
            // either retired or inactive tiers
            for (String key : allQueues.keySet()) {
                WorkQueue q = allQueues.get(key);
                if(q.isRetired()) {
                    getRetiredQueues().add(key); 
                } else if (q.getCount()>0){
                    deactivateQueue(q);
                } else {
                    // ensure quques that were empty-but-not-yet-recognized
                    // (ready/snoozed) at time of checkpoint are reset
                    q.noteDeactivated();
                }
            }
        }
    }

    @Override
    protected void initOtherQueues() throws DatabaseException {
        // tiny risk of OutOfMemoryError: if giant number of snoozed
        // queues all wake-to-ready at once
        readyClassQueues = new LinkedBlockingQueue<String>();

        inactiveQueuesByPrecedence = new ConcurrentSkipListMap<Integer,Queue<String>>();
        
        retiredQueues = bdb.getStoredQueue("retiredQueues", String.class);

        // primary snoozed queues
        snoozedClassQueues = new DelayQueue<DelayedWorkQueue>();
        // just in case: overflow for extreme situations
        snoozedOverflow = bdb.getStoredMap(
                "snoozedOverflow", Long.class, DelayedWorkQueue.class, true, false);
            
        this.futureUris = bdb.getStoredMap(
                "futureUris", Long.class, CrawlURI.class, true, recoveryCheckpoint!=null);
        
        // initialize master map in which other queues live
        this.pendingUris = createMultipleWorkQueues();
    }


    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.WorkQueueFrontier#createInactiveQueueForPrecedence(int)
     */
    @Override
    Queue<String> createInactiveQueueForPrecedence(int precedence) {
        return bdb.getStoredQueue("inactiveQueues-"+precedence, String.class);
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
    
    /**
     * Run a self-consistency check over queue collections, queues-of-queues, 
     * etc. for testing purposes. Requires one of the same locks as for PAUSE, 
     * so should only be run while crawl is running. 
     */
    public void consistencyCheck() {
//        outboundLock.writeLock().lock(); 
        dispositionInProgressLock.writeLock().lock();
        System.err.println("<<<CHECKING FRONTIER CONSISTENCY");
        DisposableStoredSortedMap<String,String> queueSummaries = 
            bdb.getStoredMap(
                    null,
                    String.class,
                    String.class,
                    false,
                    false);
        // mark every queue with the 'managed' collections it's in
        consistencyMarkup(queueSummaries, inProcessQueues, "i");
        consistencyMarkup(queueSummaries,readyClassQueues, "r");
        consistencyMarkup(queueSummaries,snoozedClassQueues, "s");
        consistencyMarkup(queueSummaries,snoozedOverflow.values(), "S");
        for( Entry<Integer, Queue<String>> entry : getInactiveQueuesByPrecedence().entrySet()) {
            consistencyMarkup(queueSummaries,entry.getValue(),Integer.toString(entry.getKey()));
        }
        consistencyMarkup(queueSummaries,retiredQueues, "R");
        
        // report problems where a queue isn't as expected or ideal
        int anomalies = 0; 
        for(String q : allQueues.keySet()) {
            WorkQueue wq = allQueues.get(q); 
            String summary = queueSummaries.get(q);
            if(wq.getCount()>0 && summary == null) {
                // every non-empty queue should have been in at least one collection
                System.err.println("FRONTIER ANOMALY: "+q+" "+wq.getCount()+" "+wq.isManaged()+" but not in managed collections");
//                System.err.println(wq.shortReportLegend()+"\n"+inactiveByClass.get(q)+"\n"+wq.shortReportLine());
                anomalies++;
            }
            if(wq.getCount()==0 && summary == null && wq.isManaged()) {
                // any empty queue should only report isManaged if in a collection
                System.err.println("FRONTIER ANOMALY: "+q+" "+wq.getCount()+" "+wq.isManaged()+" but not in managed collections");
//                System.err.println(wq.shortReportLegend()+"\n"+inactiveByClass.get(q)+"\n"+wq.shortReportLine());
                anomalies++;
            }
        }
        System.err.println(anomalies+" ANOMALIES");
        int concerns = 0; 
        for(String q : queueSummaries.keySet()) {
            String summary = queueSummaries.get(q);
            if(summary != null && summary.split(",").length>1) {
                // ideally queues won't be more than one place (though frontier
                // should operate if they are, and changing precedence values 
                // will cause multiple entries by design)
                WorkQueue wq = allQueues.get(q); 
                System.err.println("FRONTIER CONCERN: "+q+" "+wq.getCount()+" multiple places: "+summary);
                System.err.println("\n"+wq.shortReportLegend()+"\n"+wq.shortReportLine());
                concerns++;
            }
        }
        System.err.println(concerns+" CONCERNS");
        System.err.println("END CHECKING FRONTIER>>>");
        
        queueSummaries.dispose();
        dispositionInProgressLock.writeLock().unlock();
//        outboundLock.writeLock().unlock(); 
    }
    protected void consistencyMarkup(
            DisposableStoredSortedMap<String, String> queueSummaries,
            Iterable<?> queues, String mark) {
        for(Object qq : queues) {
            String key = (qq instanceof String) 
                         ? (String)qq 
                         : (qq instanceof WorkQueue) 
                           ? ((WorkQueue)qq).getClassKey()
                           : ((DelayedWorkQueue)qq).getClassKey();
            String val = queueSummaries.get(key);
            val = (val==null) ? mark : val+","+mark;
            queueSummaries.put(key, val);
        }
    }
}
