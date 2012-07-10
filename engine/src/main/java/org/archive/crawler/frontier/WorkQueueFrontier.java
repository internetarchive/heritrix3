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

import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.DEFERRED_FOR_RETRY;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.DISREGARDED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.FAILED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.SUCCEEDED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DEFERRED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_RUNTIME_EXCEPTION;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.collections.iterators.ObjectArrayIterator;
import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.framework.ToeThread;
import org.archive.crawler.frontier.precedence.BaseQueuePrecedencePolicy;
import org.archive.crawler.frontier.precedence.QueuePrecedencePolicy;
import org.archive.crawler.util.TopNSet;
import org.archive.modules.CrawlURI;
import org.archive.spring.KeyedProperties;
import org.archive.util.ArchiveUtils;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.ObjectIdentityMemCache;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;

import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.DatabaseException;

/**
 * A common Frontier base using several queues to hold pending URIs. 
 * 
 * Uses in-memory map of all known 'queues' inside a single database.
 * Round-robins between all queues.
 *
 * @author Gordon Mohr
 * @author Christian Kohlschuetter
 */
public abstract class WorkQueueFrontier extends AbstractFrontier
implements Closeable, 
           ApplicationContextAware {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 570384305871965843L;

    /**
     * If we know that only a small amount of queues is held in memory,
     * we can avoid using a disk-based BigMap.
     * This only works efficiently if the WorkQueue does not hold its
     * entries in memory as well.
     */ 
    private static final int MAX_QUEUES_TO_HOLD_ALLQUEUES_IN_MEMORY = 3000;

    /**
     * When a snooze target for a queue is longer than this amount, the queue
     * will be "long snoozed" instead of "short snoozed".  A "long snoozed"
     * queue may be swapped to disk because it's not needed soon.  
     */
    protected long snoozeLongMs = 5L*60L*1000L; 
    public long getSnoozeLongMs() {
        return snoozeLongMs;
    }
    public void setSnoozeLongMs(long snooze) {
        this.snoozeLongMs = snooze;
    }
    
    private static final Logger logger =
        Logger.getLogger(WorkQueueFrontier.class.getName());
    
    // ApplicationContextAware implementation, for eventing
    protected AbstractApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = (AbstractApplicationContext)applicationContext;
    }

    /** amount to replenish budget on each activation (duty cycle) */
    {
        setBalanceReplenishAmount(3000);
    }
    public int getBalanceReplenishAmount() {
        return (Integer) kp.get("balanceReplenishAmount");
    }
    public void setBalanceReplenishAmount(int replenish) {
        kp.put("balanceReplenishAmount",replenish);
    }


    /** budget penalty for an error fetch */
    {
        setErrorPenaltyAmount(100);
    }
    public int getErrorPenaltyAmount() {
        return (Integer) kp.get("errorPenaltyAmount");
    }
    public void setErrorPenaltyAmount(int penalty) {
        kp.put("errorPenaltyAmount",penalty);
    }

    /** total expenditure to allow a queue before 'retiring' it  */
    {
        setQueueTotalBudget(-1L);
    }
    public long getQueueTotalBudget() {
        return (Long) kp.get("queueTotalBudget");
    }
    public void setQueueTotalBudget(long budget) {
        kp.put("queueTotalBudget",budget);
    }
    
    /** queue precedence assignment policy to use. */
    {
        setQueuePrecedencePolicy(new BaseQueuePrecedencePolicy());
    }
    public QueuePrecedencePolicy getQueuePrecedencePolicy() {
        return (QueuePrecedencePolicy) kp.get("queuePrecedencePolicy");
    }
    public void setQueuePrecedencePolicy(QueuePrecedencePolicy policy) {
        kp.put("queuePrecedencePolicy",policy);
    }

    /** precedence rank at or below which queues are not crawled */
    protected int precedenceFloor = 255; 
    public int getPrecedenceFloor() {
        return this.precedenceFloor;
    }
    public void setPrecedenceFloor(int floor) {
        this.precedenceFloor = floor;
    }

    /** truncate reporting of queues at this large but not unbounded number */
    protected int maxQueuesPerReportCategory = 2000; 
    public int getMaxQueuesPerReportCategory() {
        return this.maxQueuesPerReportCategory;
    }
    public void setMaxQueuesPerReportCategory(int max) {
        this.maxQueuesPerReportCategory = max;
    }

    /** All known queues.
     */
    protected ObjectIdentityCache<WorkQueue> allQueues = null; 
    // of classKey -> ClassKeyQueue

    /**
     * All per-class queues whose first item may be handed out.
     * Linked-list of keys for the queues.
     */
    protected BlockingQueue<String> readyClassQueues;
    
    /** all per-class queues from whom a URI is outstanding */
    protected Set<WorkQueue> inProcessQueues = 
        Collections.newSetFromMap(new ConcurrentHashMap<WorkQueue, Boolean>()); // of ClassKeyQueue
    
    /**
     * All per-class queues held in snoozed state, sorted by wake time.
     */
    transient protected DelayQueue<DelayedWorkQueue> snoozedClassQueues;
    protected StoredSortedMap<Long,DelayedWorkQueue> snoozedOverflow; 
    protected AtomicInteger snoozedOverflowCount = new AtomicInteger(0); 
    protected static int MAX_SNOOZED_IN_MEMORY = 10000; 
    
    /** URIs scheduled to be re-enqueued at future date */
    protected StoredSortedMap<Long, CrawlURI> futureUris; 
    
    /** remember keys of small number of largest queues for reporting */
    transient protected TopNSet largestQueues = new TopNSet(20);
    /** remember this many largest queues for reporting's sake; actual tracking
     *  can be somewhat approximate when some queues shrink before others' 
     *  sizes are again noted, or if the size is adjusted mid-crawl. */
    public int getLargestQueuesCount() {
        return largestQueues.getMaxSize();
    }
    public void setLargestQueuesCount(int count) {
        largestQueues.setMaxSize(count);
    }
    
    protected int highestPrecedenceWaiting = Integer.MAX_VALUE;

    /** The UriUniqFilter to use, tracking those UURIs which are 
     * already in-process (or processed), and thus should not be 
     * rescheduled. Also known as the 'alreadyIncluded' or
     * 'alreadySeen' structure */
    protected UriUniqFilter uriUniqFilter;
    public UriUniqFilter getUriUniqFilter() {
        return this.uriUniqFilter;
    }
    @Autowired
    public void setUriUniqFilter(UriUniqFilter uriUniqFilter) {
        this.uriUniqFilter = uriUniqFilter;
    }

    /**
     * Constructor.
     */
    public WorkQueueFrontier() {
        super();
    }
    
    public void start() {
        if(isRunning()) {
            return; 
        }
        uriUniqFilter.setDestination(this);
        super.start();
        try {
            initInternalQueues();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

    }

    /**
     * Initializes internal queues.  May decide to keep all queues in memory based on
     * {@link QueueAssignmentPolicy#maximumNumberOfKeys}.  Otherwise invokes
     * {@link #initAllQueues()} to actually set up the queues.
     * 
     * Subclasses should invoke this method with recycle set to "true" in 
     * a private readObject method, to restore queues after a checkpoint.
     * 
     * @param recycle
     * @throws IOException
     * @throws DatabaseException
     */
    protected void initInternalQueues() 
    throws IOException, DatabaseException {
        this.initOtherQueues();
        if (workQueueDataOnDisk()
                && preparer.getQueueAssignmentPolicy().maximumNumberOfKeys() >= 0
                && preparer.getQueueAssignmentPolicy().maximumNumberOfKeys() <= 
                    MAX_QUEUES_TO_HOLD_ALLQUEUES_IN_MEMORY) {
            this.allQueues = 
                new ObjectIdentityMemCache<WorkQueue>(701, .9f, 100);
        } else {
            this.initAllQueues();
        }
    }
    
    /**
     * Initialize the allQueues field in an implementation-appropriate
     * way.
     * @throws DatabaseException
     */
    protected abstract void initAllQueues() throws DatabaseException;
    
    /**
     * Initialize all other internal queues in an implementation-appropriate
     * way.
     * @throws DatabaseException
     */
    protected abstract void initOtherQueues() throws DatabaseException;

    
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.AbstractFrontier#stop()
     */
    @Override
    public void stop() {
        super.stop();
    }
    
    public void destroy() {
        // release resources and trigger end-of-frontier actions
        close();
    }
    
    /**
     * Release resources only needed when running
     */
    public void close() {
        ArchiveUtils.closeQuietly(uriUniqFilter);     
        ArchiveUtils.closeQuietly(allQueues);
    }
    
    /**
     * Accept the given CrawlURI for scheduling, as it has
     * passed the alreadyIncluded filter. 
     * 
     * Choose a per-classKey queue and enqueue it. If this
     * item has made an unready queue ready, place that 
     * queue on the readyClassQueues queue. 
     * @param caUri CrawlURI.
     */
    protected void processScheduleAlways(CrawlURI curi) {
//        assert Thread.currentThread() == managerThread;
        assert KeyedProperties.overridesActiveFrom(curi); 
        
        prepForFrontier(curi);
        sendToQueue(curi);
    }
    
    
    /**
     * Arrange for the given CrawlURI to be visited, if it is not
     * already enqueued/completed. 
     * 
     * Differs from superclass in that it operates in calling thread, rather 
     * than deferring operations via in-queue to managerThread. TODO: settle
     * on either defer or in-thread approach after testing. 
     *
     * @see org.archive.crawler.framework.Frontier#schedule(org.archive.modules.CrawlURI)
     */
    @Override
    public void schedule(CrawlURI curi) {
        sheetOverlaysManager.applyOverlaysTo(curi);
        try {
            KeyedProperties.loadOverridesFrom(curi);
            if(curi.getClassKey()==null) {
                // remedial processing
                preparer.prepare(curi);
            }
            processScheduleIfUnique(curi);
        } finally {
            KeyedProperties.clearOverridesFrom(curi); 
        }
    }

    /**
     * Arrange for the given CrawlURI to be visited, if it is not
     * already scheduled/completed.
     *
     * @see org.archive.crawler.framework.Frontier#schedule(org.archive.modules.CrawlURI)
     */
    protected void processScheduleIfUnique(CrawlURI curi) {
//        assert Thread.currentThread() == managerThread;
        assert KeyedProperties.overridesActiveFrom(curi); 
        
        // Canonicalization may set forceFetch flag.  See
        // #canonicalization(CrawlURI) javadoc for circumstance.
        String canon = curi.getCanonicalString();
        if (curi.forceFetch()) {
            uriUniqFilter.addForce(canon, curi);
        } else {
            uriUniqFilter.add(canon, curi);
        }
    }

    /**
     * Send a CrawlURI to the appropriate subqueue.
     * 
     * @param curi
     */
    protected void sendToQueue(CrawlURI curi) {
//        assert Thread.currentThread() == managerThread;
        
        WorkQueue wq = getQueueFor(curi.getClassKey());
        synchronized(wq) {
            int originalPrecedence = wq.getPrecedence();
            wq.enqueue(this, curi);
            // always take budgeting values from current curi
            // (whose overlay settings should be active here)
            wq.setSessionBudget(getBalanceReplenishAmount());
            wq.setTotalBudget(getQueueTotalBudget());
            
            if(!wq.isRetired()) {
                incrementQueuedUriCount();
                int currentPrecedence = wq.getPrecedence();
                if(!wq.isManaged() || currentPrecedence < originalPrecedence) {
                    // queue newly filled or bumped up in precedence; ensure enqueuing
                    // at precedence level (perhaps duplicate; if so that's handled elsewhere)
                    deactivateQueue(wq);
                }
            }
        }
        // Update recovery log.
        doJournalAdded(curi);
        wq.makeDirty();
        largestQueues.update(wq.getClassKey(), wq.getCount());
    }

    /**
     * Put the given queue on the readyClassQueues queue
     * @param wq
     */
    protected void readyQueue(WorkQueue wq) {
//        assert Thread.currentThread() == managerThread;

        try {
            readyClassQueues.put(wq.getClassKey());
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "queue readied: " + wq.getClassKey());
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.err.println("unable to ready queue "+wq);
            // propagate interrupt up 
            throw new RuntimeException(e);
        }
    }

    /**
     * Put the given queue on the inactiveQueues queue
     * @param wq
     */
    protected void deactivateQueue(WorkQueue wq) {
        int precedence = wq.getPrecedence();

        synchronized(wq) {
            wq.noteDeactivated();
            inProcessQueues.remove(wq);
            if(wq.getCount()==0) {
                System.err.println("deactivate empty queue?");
            }

            synchronized (getInactiveQueuesByPrecedence()) {
                getInactiveQueuesForPrecedence(precedence).add(wq.getClassKey());
                if(wq.getPrecedence() < highestPrecedenceWaiting ) {
                    highestPrecedenceWaiting = wq.getPrecedence();
                }
            }

            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "queue deactivated to p" + precedence 
                        + ": " + wq.getClassKey());
            }
        }
    }
    
    /**
     * Get the queue of inactive uri-queue names at the given precedence. 
     * 
     * @param precedence
     * @return queue of inacti
     */
    protected Queue<String> getInactiveQueuesForPrecedence(int precedence) {
        Map<Integer,Queue<String>> inactiveQueuesByPrecedence = 
            getInactiveQueuesByPrecedence();
        Queue<String> candidate = inactiveQueuesByPrecedence.get(precedence);
        if(candidate==null) {
            candidate = createInactiveQueueForPrecedence(precedence);
            inactiveQueuesByPrecedence.put(precedence,candidate);
        }
        return candidate;
    }

    /**
     * Return a sorted map of all queues of WorkQueue keys, keyed by precedence
     * @return SortedMap<Integer, Queue<String>> of inactiveQueues
     */
    protected abstract SortedMap<Integer, Queue<String>> getInactiveQueuesByPrecedence();

    /**
     * Create an inactiveQueue to hold queue names at the given precedence
     * @param precedence
     * @return Queue<String> for names of inactive queues
     */
    protected abstract Queue<String> createInactiveQueueForPrecedence(int precedence);

    /**
     * Put the given queue on the retiredQueues queue
     * @param wq
     */
    protected void retireQueue(WorkQueue wq) {
//        assert Thread.currentThread() == managerThread;

        inProcessQueues.remove(wq);
        getRetiredQueues().add(wq.getClassKey());
        decrementQueuedCount(wq.getCount());
        wq.setRetired(true);
        if(logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "queue retired: " + wq.getClassKey());
        }
    }
    
    /**
     * Return queue of all retired queue names.
     * 
     * @return Queue<String> of retired queue names
     */
    protected abstract Queue<String> getRetiredQueues();

    /** 
     * Accommodate any changes in retirement-determining settings (like
     * total-budget or force-retire changes/overlays. 
     * 
     * (Essentially, exists to be called from tools like the UI 
     * Scripting Console when the operator knows it's necessary.)
     */
    public void reconsiderRetiredQueues() {

        // The rules for a 'retired' queue may have changed; so,
        // unretire all queues to 'inactive'. If they still qualify
        // as retired/overbudget next time they come up, they'll
        // be re-retired; if not, they'll get a chance to become
        // active under the new rules.
        
        // TODO: Do this automatically, only when necessary.
        
        String key = getRetiredQueues().poll();
        while (key != null) {
            WorkQueue q = (WorkQueue)this.allQueues.get(key);
            if(q != null) {
                unretireQueue(q);
                q.makeDirty();
            }
            key = getRetiredQueues().poll();
        }
    }
    /**
     * Restore a retired queue to the 'inactive' state. 
     * 
     * @param q
     */
    private void unretireQueue(WorkQueue q) {
//        assert Thread.currentThread() == managerThread;

        deactivateQueue(q);
        q.setRetired(false); 
        incrementQueuedUriCount(q.getCount());
    }

    /**
     * Return the work queue for the given classKey, or null
     * if no such queue exists.
     * 
     * @param classKey key to look for
     * @return the found WorkQueue
     */
    protected abstract WorkQueue getQueueFor(String classKey);
    
 
    /**
     * Return the next CrawlURI eligible to be processed (and presumably
     * visited/fetched) by a a worker thread.
     *
     * Relies on the readyClassQueues having been loaded with
     * any work queues that are eligible to provide a URI. 
     *
     * @return next CrawlURI eligible to be processed, or null if none available
     *
     * @see org.archive.crawler.framework.Frontier#next()
     */
    protected CrawlURI findEligibleURI() {
            // wake any snoozed queues
            wakeQueues();
            // consider rescheduled URIS
            checkFutures();
                   
            // find a non-empty ready queue, if any 
            // TODO: refactor to untangle these loops, early-exits, etc!
            WorkQueue readyQ = null;
            findauri: while(true) {
                findaqueue: do {
                    String key = readyClassQueues.poll();
                    if(key==null) {
                        // no ready queues; try to activate one
                        if(!getInactiveQueuesByPrecedence().isEmpty() 
                            && highestPrecedenceWaiting < getPrecedenceFloor()) {
                            activateInactiveQueue();
                            continue findaqueue;
                        } else {
                            // nothing ready or readyable
                            break findaqueue;
                        }
                    }
                    readyQ = getQueueFor(key);
                    if(readyQ==null) {
                         // readyQ key wasn't in all queues: unexpected
                        logger.severe("Key "+ key +
                            " in readyClassQueues but not allQueues");
                        break findaqueue;
                    }
                    if(readyQ.getCount()==0) {
                        // readyQ is empty and ready: it's exhausted
                        readyQ.noteExhausted(); 
                        readyQ.makeDirty();
                        readyQ = null;
                        continue; 
                    }
                    if(!inProcessQueues.add(readyQ)) {
                        // double activation; discard this and move on
                        // (this guard allows other enqueuings to ready or 
                        // the various inactive-by-precedence queues to 
                        // sometimes redundantly enqueue a queue key)
                        readyQ = null; 
                        continue;
                    }
                    // queue has gone 'in process' 
                    readyQ.considerActive();
                    readyQ.setWakeTime(0); // clear obsolete wake time, if any
                    
                    // we know readyQ is not empty (getCount()!=0) so peek() shouldn't return null
                    CrawlURI readyQUri = readyQ.peek(this);
                    // see HER-1973 and HER-1946
                    sheetOverlaysManager.applyOverlaysTo(readyQUri);
                    try {
                        KeyedProperties.loadOverridesFrom(readyQUri);
                        readyQ.setSessionBudget(getBalanceReplenishAmount());
                        readyQ.setTotalBudget(getQueueTotalBudget()); 
                    } finally {
                        KeyedProperties.clearOverridesFrom(readyQUri); 
                    }
                    
                    if (readyQ.isOverSessionBudget()) {
                        deactivateQueue(readyQ);
                        readyQ.makeDirty();
                        readyQ = null;
                        continue; 
                    }
                    if (readyQ.isOverTotalBudget()) {
                        retireQueue(readyQ);
                        readyQ.makeDirty();
                        readyQ = null;
                        continue; 
                    }
                } while (readyQ == null);
                
                if (readyQ == null) {
                    // no queues left in ready or readiable
                    break findauri; 
                }
           
                returnauri: while(true) { // loop left by explicit return or break on empty
                    CrawlURI curi = null;
                    curi = readyQ.peek(this);   
                    if(curi == null) {
                        // should not reach
                        logger.severe("No CrawlURI from ready non-empty queue "
                                + readyQ.classKey + "\n" 
                                + readyQ.shortReportLegend() + "\n"
                                + readyQ.shortReportLine() + "\n");
                        break returnauri;
                    }
                    
                    // from queues, override names persist but not map source
                    curi.setOverlayMapsSource(sheetOverlaysManager);
                    // TODO: consider optimizations avoiding this recalc of
                    // overrides when not necessary
                    sheetOverlaysManager.applyOverlaysTo(curi);
                    // check if curi belongs in different queue
                    String currentQueueKey;
                    try {
                        KeyedProperties.loadOverridesFrom(curi);
                        currentQueueKey = getClassKey(curi);
                    } finally {
                        KeyedProperties.clearOverridesFrom(curi); 
                    }
                    if (currentQueueKey.equals(curi.getClassKey())) {
                        // curi was in right queue, emit
                        noteAboutToEmit(curi, readyQ);
                        return curi;
                    }
                    // URI's assigned queue has changed since it
                    // was queued (eg because its IP has become
                    // known). Requeue to new queue.
                    // TODO: consider synchronization on readyQ
                    readyQ.dequeue(this,curi);
                    doJournalRelocated(curi);
                    curi.setClassKey(currentQueueKey);
                    decrementQueuedCount(1);
                    curi.setHolderKey(null);
                    sendToQueue(curi);
                    if(readyQ.getCount()==0) {
                        // readyQ is empty and ready: it's exhausted
                        // release held status, allowing any subsequent 
                        // enqueues to again put queue in ready
                        // FIXME: tiny window here where queue could 
                        // receive new URI, be readied, fail not-in-process?
                        inProcessQueues.remove(readyQ);
                        readyQ.noteExhausted();
                        readyQ.makeDirty();
                        readyQ = null;
                        continue findauri;
                    }
                }
            }
                
            if(inProcessQueues.size()==0) {
                // Nothing was ready or in progress or imminent to wake; ensure 
                // any piled-up pending-scheduled URIs are considered
                uriUniqFilter.requestFlush();
            }
            
            // if truly nothing ready, wait a moment before returning null
            // so that loop in surrounding next() has a chance of getting something
            // next time
            if(getTotalEligibleInactiveQueues()==0) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // 
                } 
            }
            
            // nothing eligible
            return null; 
    }

    /**
     * Check for any future-scheduled URIs now eligible for reenqueuing
     */
    protected void checkFutures() {
//        assert Thread.currentThread() == managerThread;
        // TODO: consider only checking this every set interval
        if(!futureUris.isEmpty()) {
            synchronized(futureUris) {
                Iterator<CrawlURI> iter = 
                    futureUris.headMap(System.currentTimeMillis())
                        .values().iterator();
                while(iter.hasNext()) {
                    CrawlURI curi = iter.next();
                    curi.setRescheduleTime(-1); // unless again set elsewhere
                    iter.remove();
                    futureUriCount.decrementAndGet();
                    receive(curi);
                }
            }
        }
    }
    
    /**
     * Activate an inactive queue, if any are available. 
     */
    protected boolean activateInactiveQueue() {
        for (Entry<Integer, Queue<String>> entry: getInactiveQueuesByPrecedence().entrySet()) {
            int expectedPrecedence = entry.getKey();
            Queue<String> queueOfWorkQueueKeys = entry.getValue();

            while (true) {
                synchronized (getInactiveQueuesByPrecedence()) {
                    String workQueueKey = queueOfWorkQueueKeys.poll();
                    if (workQueueKey == null) {
                        break;
                    }

                    WorkQueue candidateQ = (WorkQueue) this.allQueues.get(workQueueKey);
                    if (candidateQ.getPrecedence() > expectedPrecedence) {
                        // queue demoted since placed; re-deactivate
                        deactivateQueue(candidateQ);
                        candidateQ.makeDirty();
                        continue; 
                    }

                    updateHighestWaiting(expectedPrecedence);
                    try {
                        readyClassQueues.put(workQueueKey);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e); 
                    } 
                    
                    return true; 
                }
            }
        }
        
        return false;
    }

    /**
     * Recalculate the value of thehighest-precedence queue waiting
     * among inactive queues. 
     * 
     * @param startFrom start looking at this precedence value
     */
    protected void updateHighestWaiting(int startFrom) {
        // probe for new highestWaiting
        for(int precedenceKey : getInactiveQueuesByPrecedence().tailMap(startFrom).keySet()) {
            if(!getInactiveQueuesByPrecedence().get(precedenceKey).isEmpty()) {
                highestPrecedenceWaiting = precedenceKey;
                return;
            }
        }
        // nothing waiting
        highestPrecedenceWaiting = Integer.MAX_VALUE;
    }

    /**
     * Enqueue the given queue to either readyClassQueues or inactiveQueues,
     * as appropriate.
     * 
     * @param wq
     */
    protected void reenqueueQueue(WorkQueue wq) { 
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("queue reenqueued: " +
                wq.getClassKey());
        }
        if(highestPrecedenceWaiting < wq.getPrecedence() 
            || wq.getPrecedence() >= getPrecedenceFloor()) {
            // if still over budget, deactivate
            deactivateQueue(wq);
        } else {
            readyQueue(wq);
        }
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.AbstractFrontier#getMaxInWait()
     */
    @Override
    protected long getMaxInWait() {
        Delayed next = snoozedClassQueues.peek();
        return next == null ? 60000 : next.getDelay(TimeUnit.MILLISECONDS);
    }

    /**
     * Utility method for advanced users/experimentation: force wake all snoozed
     * queues -- for example to kick a crawl where connectivity problems have
     * put all queues in slow-retry-snoozes back to busy-ness. 
     */
    public void forceWakeQueues() {
        Iterator<DelayedWorkQueue> iterSnoozed = snoozedClassQueues.iterator();
        while(iterSnoozed.hasNext()) {
            WorkQueue queue = iterSnoozed.next().getWorkQueue(WorkQueueFrontier.this);
            queue.setWakeTime(0);
            reenqueueQueue(queue);
            queue.makeDirty();
            iterSnoozed.remove(); 
        }
        Iterator<DelayedWorkQueue> iterOverflow = snoozedOverflow.values().iterator();
        while(iterOverflow.hasNext()) {
            WorkQueue queue = iterOverflow.next().getWorkQueue(WorkQueueFrontier.this);
            queue.setWakeTime(0);
            reenqueueQueue(queue);
            queue.makeDirty();
            iterOverflow.remove(); 
            snoozedOverflowCount.decrementAndGet();
        }
    }
    
    /**
     * Wake any queues sitting in the snoozed queue whose time has come.
     */
    protected void wakeQueues() {
        DelayedWorkQueue waked; 
        while((waked = snoozedClassQueues.poll())!=null) {
            WorkQueue queue = waked.getWorkQueue(this);
            queue.setWakeTime(0);
            queue.makeDirty();
            reenqueueQueue(queue);
        }
        // also consider overflow (usually empty)
        if(!snoozedOverflow.isEmpty()) {
            synchronized(snoozedOverflow) {
                Iterator<DelayedWorkQueue> iter = 
                    snoozedOverflow.headMap(System.currentTimeMillis()).values().iterator();
                while(iter.hasNext()) {
                    DelayedWorkQueue dq = iter.next();
                    iter.remove();
                    snoozedOverflowCount.decrementAndGet();
                    WorkQueue queue = dq.getWorkQueue(this);
                    queue.setWakeTime(0);
                    queue.makeDirty();
                    reenqueueQueue(queue);
                }
            }
        }
    }
    
    /**
     * Note that the previously emitted CrawlURI has completed
     * its processing (for now).
     *
     * The CrawlURI may be scheduled to retry, if appropriate,
     * and other related URIs may become eligible for release
     * via the next next() call, as a result of finished().
     *
     * TODO: make as many decisions about what happens to the CrawlURI
     * (success, failure, retry) and queue (retire, snooze, ready) as 
     * possible elsewhere, such as in DispositionProcessor. Then, break
     * this into simple branches or focused methods for each case. 
     *  
     * @see org.archive.crawler.framework.Frontier#finished(org.archive.modules.CrawlURI)
     */
    protected void processFinish(CrawlURI curi) {
//        assert Thread.currentThread() == managerThread;
        
        long now = System.currentTimeMillis();

        curi.incrementFetchAttempts();
        logNonfatalErrors(curi);
        
        WorkQueue wq = (WorkQueue) curi.getHolder();
        // always refresh budgeting values from current curi
        // (whose overlay settings should be active here)
        wq.setSessionBudget(getBalanceReplenishAmount());
        wq.setTotalBudget(getQueueTotalBudget());
        
        assert (wq.peek(this) == curi) : "unexpected peek " + wq;

        int holderCost = curi.getHolderCost();

        if (needsReenqueuing(curi)) {
            // codes/errors which don't consume the URI, leaving it atop queue
            if(curi.getFetchStatus()!=S_DEFERRED) {
                wq.expend(holderCost); // all retries but DEFERRED cost
            }
            long delay_ms = retryDelayFor(curi) * 1000;
            curi.processingCleanup(); // lose state that shouldn't burden retry
            wq.unpeek(curi);
            wq.update(this, curi); // rewrite any changes
            handleQueue(wq,curi.includesRetireDirective(),now,delay_ms);
            appCtx.publishEvent(new CrawlURIDispositionEvent(this,curi,DEFERRED_FOR_RETRY));
            doJournalReenqueued(curi);
            wq.makeDirty();
            return; // no further dequeueing, logging, rescheduling to occur
        }

        // Curi will definitely be disposed of without retry, so remove from queue
        wq.dequeue(this,curi);
        decrementQueuedCount(1);
        largestQueues.update(wq.getClassKey(), wq.getCount());
        log(curi);

        
        if (curi.isSuccess()) {
            // codes deemed 'success' 
            incrementSucceededFetchCount();
            totalProcessedBytes.addAndGet(curi.getRecordedSize());
            appCtx.publishEvent(new CrawlURIDispositionEvent(this,curi,SUCCEEDED));
            doJournalFinishedSuccess(curi);
           
        } else if (isDisregarded(curi)) {
            // codes meaning 'undo' (even though URI was enqueued, 
            // we now want to disregard it from normal success/failure tallies)
            // (eg robots-excluded, operator-changed-scope, etc)
            incrementDisregardedUriCount();
            appCtx.publishEvent(new CrawlURIDispositionEvent(this,curi,DISREGARDED));
            holderCost = 0; // no charge for disregarded URIs
            // TODO: consider reinstating forget-URI capability, so URI could be
            // re-enqueued if discovered again
            doJournalDisregarded(curi);
            
        } else {
            // codes meaning 'failure'
            incrementFailedFetchCount();
            appCtx.publishEvent(new CrawlURIDispositionEvent(this,curi,FAILED));
            // if exception, also send to crawlErrors
            if (curi.getFetchStatus() == S_RUNTIME_EXCEPTION) {
                Object[] array = { curi };
                loggerModule.getRuntimeErrors().log(Level.WARNING, curi.getUURI()
                        .toString(), array);
            }        
            // charge queue any extra error penalty
            wq.noteError(getErrorPenaltyAmount());
            doJournalFinishedFailure(curi);
            
        }

        wq.expend(holderCost); // successes & failures charge cost to queue
        
        long delay_ms = curi.getPolitenessDelay();
        handleQueue(wq,curi.includesRetireDirective(),now,delay_ms);
        wq.makeDirty();
        
        if(curi.getRescheduleTime()>0) {
            // marked up for forced-revisit at a set time
            curi.processingCleanup();
            curi.resetForRescheduling(); 
            futureUris.put(curi.getRescheduleTime(),curi);
            futureUriCount.incrementAndGet(); 
        } else {
            curi.stripToMinimal();
            curi.processingCleanup();
        }
    }
    
    /**
     * Send an active queue to its next state, based on the supplied 
     * parameters.
     * 
     * @param wq
     * @param forceRetire
     * @param now
     * @param delay_ms
     */
    protected void handleQueue(WorkQueue wq, boolean forceRetire, long now, long delay_ms) {
        inProcessQueues.remove(wq);
        if(forceRetire) {
            retireQueue(wq);
        } else if (delay_ms > 0) {
            snoozeQueue(wq, now, delay_ms);
        } else {
            getQueuePrecedencePolicy().queueReevaluate(wq);
            reenqueueQueue(wq);
        }
    }

    /**
     * Place the given queue into 'snoozed' state, ineligible to
     * supply any URIs for crawling, for the given amount of time. 
     * 
     * @param wq queue to snooze 
     * @param now time now in ms 
     * @param delay_ms time to snooze in ms
     */
    private void snoozeQueue(WorkQueue wq, long now, long delay_ms) {
        long nextTime = now + delay_ms;
        wq.setWakeTime(nextTime);
        DelayedWorkQueue dq = new DelayedWorkQueue(wq);
        if(snoozedClassQueues.size()<MAX_SNOOZED_IN_MEMORY) {
            snoozedClassQueues.add(dq);
        } else {
            synchronized(snoozedOverflow) {
                snoozedOverflow.put(nextTime, dq);
                snoozedOverflowCount.incrementAndGet();
            }
        }
    }

    /**
     * Forget the given CrawlURI. This allows a new instance
     * to be created in the future, if it is reencountered under
     * different circumstances.
     *
     * @param curi The CrawlURI to forget
     */
    protected void forget(CrawlURI curi) {
        logger.finer("Forgetting " + curi);
        uriUniqFilter.forget(curi.getCanonicalString(), curi);
    }

    /**  (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#discoveredUriCount()
     */
    public long discoveredUriCount() {
        return (this.uriUniqFilter != null)? this.uriUniqFilter.count(): 0;
    }

    /**
     * @param match String to  match.
     * @return Number of items deleted.
     */
    public long deleteURIs(String queueRegex, String uriRegex) {
        long count = 0;
        Pattern queuePat = Pattern.compile(queueRegex);
        for (String qname: allQueues.keySet()) {
            if (queuePat.matcher(qname).matches()) {
                WorkQueue wq = getQueueFor(qname);
                wq.unpeek(null);
                count += wq.deleteMatching(this, uriRegex);
                wq.makeDirty();
            }
        }
        decrementQueuedCount(count);
        return count;
    }

    //
    // Reporter implementation
    //
    
    
    @Override
    public Map<String, Object> shortReportMap() {
        if (this.allQueues == null) {
            return null;
        }
        
        int allCount = allQueues.size();
        int inProcessCount = inProcessQueues.size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = getSnoozedCount();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int inactiveCount = getTotalEligibleInactiveQueues();
        int ineligibleCount = getTotalIneligibleInactiveQueues();
        int retiredCount = getRetiredQueues().size();
        int exhaustedCount = allCount - activeCount - inactiveCount - retiredCount;

        Map<String,Object> map = new LinkedHashMap<String, Object>();
        map.put("totalQueues", allCount);
        map.put("inProcessQueues", inProcessCount);
        map.put("readyQueues", readyCount);
        map.put("snoozedQueues", snoozedCount);
        map.put("activeQueues", activeCount);
        map.put("inactiveQueues", inactiveCount);
        map.put("ineligibleQueues", ineligibleCount);
        map.put("retiredQueues", retiredCount);
        map.put("exhaustedQueues", exhaustedCount);
        map.put("lastReachedState", lastReachedState);

        return map;
    }

    /**
     * @param w Where to write to.
     */
    @Override
    public void shortReportLineTo(PrintWriter w) {
        if (!isRunning()) return; //???
        
        if (this.allQueues == null) {
            return;
        }
        int allCount = allQueues.size();
        int inProcessCount = inProcessQueues.size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = getSnoozedCount();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int inactiveCount = getTotalEligibleInactiveQueues();
        int ineligibleCount = getTotalIneligibleInactiveQueues();
        int retiredCount = getRetiredQueues().size();
        int exhaustedCount = 
            allCount - activeCount - inactiveCount - retiredCount;
        State last = lastReachedState;
        w.print(last);
        w.print(" - ");
        w.print(allCount);
        w.print(" URI queues: ");
        w.print(activeCount);
        w.print(" active (");
        w.print(inProcessCount);
        w.print(" in-process; ");
        w.print(readyCount);
        w.print(" ready; ");
        w.print(snoozedCount);
        w.print(" snoozed); ");
        w.print(inactiveCount);
        w.print(" inactive; ");
        w.print(ineligibleCount);
        w.print(" ineligible; ");
        w.print(retiredCount);
        w.print(" retired; ");
        w.print(exhaustedCount);
        w.print(" exhausted");        
        w.flush();
    }

    /**
     * Total of all URIs in inactive queues at all precedences
     * @return int total 
     */
    protected int getTotalInactiveQueues() {
        return tallyInactiveTotals(getInactiveQueuesByPrecedence());
    }
    
    /**
     * Total of all URIs in inactive queues at precedences above the floor
     * @return int total 
     */
    protected int getTotalEligibleInactiveQueues() {
        return tallyInactiveTotals(
                getInactiveQueuesByPrecedence().headMap(getPrecedenceFloor()));
    }
    
    /**
     * Total of all URIs in inactive queues at precedences at or below the floor
     * @return int total 
     */
    protected int getTotalIneligibleInactiveQueues() {
        return tallyInactiveTotals(
                getInactiveQueuesByPrecedence().tailMap(getPrecedenceFloor()));
    }

    /**
     * @param iqueue 
     * @return
     */
    private int tallyInactiveTotals(SortedMap<Integer,Queue<String>> iqueues) {
        int inactiveCount = 0; 
        for(Queue<String> q : iqueues.values()) {
            inactiveCount += q.size();
        }
        return inactiveCount;
    }
    
    /* (non-Javadoc)
     * @see org.archive.util.Reporter#singleLineLegend()
     */
    @Override
    public String shortReportLegend() {
        return "total active in-process ready snoozed inactive retired exhausted";
    }

    /**
     * This method compiles a human readable report on the status of the frontier
     * at the time of the call.
     * @param name Name of report.
     * @param writer Where to write to.
     */
    @Override
    public synchronized void reportTo(PrintWriter writer) {
        int allCount = allQueues.size();
        int inProcessCount = inProcessQueues.size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = getSnoozedCount();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int inactiveCount = getTotalInactiveQueues();
        int retiredCount = getRetiredQueues().size();
        int exhaustedCount = 
            allCount - activeCount - inactiveCount - retiredCount;
        
        writer.print("Frontier report - ");
        writer.print(ArchiveUtils.get12DigitDate());
        writer.print("\n");
        writer.print(" Job being crawled: ");
        writer.print(controller.getMetadata().getJobName());
        writer.print("\n");
        writer.print("\n -----===== STATS =====-----\n");
        writer.print(" Discovered:    ");
        writer.print(Long.toString(discoveredUriCount()));
        writer.print("\n");
        writer.print(" Queued:        ");
        writer.print(Long.toString(queuedUriCount()));
        writer.print("\n");
        writer.print(" Finished:      ");
        writer.print(Long.toString(finishedUriCount()));
        writer.print("\n");
        writer.print("  Successfully: ");
        writer.print(Long.toString(succeededFetchCount()));
        writer.print("\n");
        writer.print("  Failed:       ");
        writer.print(Long.toString(failedFetchCount()));
        writer.print("\n");
        writer.print("  Disregarded:  ");
        writer.print(Long.toString(disregardedUriCount()));
        writer.print("\n");
        writer.print("\n -----===== QUEUES =====-----\n");
        writer.print(" Already included size:     ");
        writer.print(Long.toString(uriUniqFilter.count()));
        writer.print("\n");
        writer.print("               pending:     ");
        writer.print(Long.toString(uriUniqFilter.pending()));
        writer.print("\n");
        writer.print("\n All class queues map size: ");
        writer.print(Long.toString(allCount));
        writer.print("\n");
        writer.print( "             Active queues: ");
        writer.print(activeCount);
        writer.print("\n");
        writer.print("                    In-process: ");
        writer.print(inProcessCount);
        writer.print("\n");
        writer.print("                         Ready: ");
        writer.print(readyCount);
        writer.print("\n");
        writer.print("                       Snoozed: ");
        writer.print(snoozedCount);
        writer.print("\n");
        writer.print("           Inactive queues: ");
        writer.print(inactiveCount);
        writer.print(" (");
        Map<Integer,Queue<String>> inactives = getInactiveQueuesByPrecedence();
        boolean betwixt = false; 
        for(Integer k : inactives.keySet()) {
            if(betwixt) {
                writer.print("; ");
            }
            writer.print("p");
            writer.print(k);
            writer.print(": ");
            writer.print(inactives.get(k).size());
            betwixt = true; 
        }
        writer.print(")\n");
        writer.print("            Retired queues: ");
        writer.print(retiredCount);
        writer.print("\n");
        writer.print("          Exhausted queues: ");
        writer.print(exhaustedCount);
        writer.print("\n");
        
        State last = lastReachedState;
        writer.print("\n             Last state: "+last);        
        
        writer.print("\n -----===== MANAGER THREAD =====-----\n");
        ToeThread.reportThread(managerThread, writer);
        
        writer.print("\n -----===== "+largestQueues.size()+" LONGEST QUEUES =====-----\n");
        appendQueueReports(writer, "LONGEST", largestQueues.getEntriesDescending().iterator(), largestQueues.size(), largestQueues.size());
        
        writer.print("\n -----===== IN-PROCESS QUEUES =====-----\n");
        Collection<WorkQueue> inProcess = inProcessQueues;
        ArrayList<WorkQueue> copy = extractSome(inProcess, maxQueuesPerReportCategory);
        appendQueueReports(writer, "IN-PROCESS", copy.iterator(), copy.size(), maxQueuesPerReportCategory);
        
        writer.print("\n -----===== READY QUEUES =====-----\n");
        appendQueueReports(writer, "READY", this.readyClassQueues.iterator(),
            this.readyClassQueues.size(), maxQueuesPerReportCategory);
        
        writer.print("\n -----===== SNOOZED QUEUES =====-----\n");
        Object[] objs = snoozedClassQueues.toArray();
        DelayedWorkQueue[] qs = Arrays.copyOf(objs,objs.length,DelayedWorkQueue[].class);
        Arrays.sort(qs);
        appendQueueReports(writer, "SNOOZED", new ObjectArrayIterator(qs), getSnoozedCount(), maxQueuesPerReportCategory);
        
        writer.print("\n -----===== INACTIVE QUEUES =====-----\n");
        SortedMap<Integer,Queue<String>> sortedInactives = getInactiveQueuesByPrecedence();
        for(Integer prec : sortedInactives.keySet()) {
            Queue<String> inactiveQueues = sortedInactives.get(prec);
            appendQueueReports(writer, "INACTIVE-p"+prec, inactiveQueues.iterator(),
                    inactiveQueues.size(), maxQueuesPerReportCategory);
        }
        
        writer.print("\n -----===== RETIRED QUEUES =====-----\n");
        appendQueueReports(writer, "RETIRED", getRetiredQueues().iterator(),
            getRetiredQueues().size(), maxQueuesPerReportCategory);
        
        writer.flush();
    }
    
    /** Compact report of all nonempty queues (one queue per line)
     * 
     * @param writer
     */
    public void allNonemptyReportTo(PrintWriter writer) {
        ArrayList<WorkQueue> inProcessQueuesCopy;
        synchronized(this.inProcessQueues) {
            // grab a copy that will be stable against mods for report duration 
            Collection<WorkQueue> inProcess = this.inProcessQueues;
            inProcessQueuesCopy = new ArrayList<WorkQueue>(inProcess);
        }
        writer.print("\n -----===== IN-PROCESS QUEUES =====-----\n");
        queueSingleLinesTo(writer, inProcessQueuesCopy.iterator());

        writer.print("\n -----===== READY QUEUES =====-----\n");
        queueSingleLinesTo(writer, this.readyClassQueues.iterator());

        writer.print("\n -----===== SNOOZED QUEUES =====-----\n");
        queueSingleLinesTo(writer, this.snoozedClassQueues.iterator());
        queueSingleLinesTo(writer, this.snoozedOverflow.values().iterator());
        
        writer.print("\n -----===== INACTIVE QUEUES =====-----\n");
        for(Queue<String> inactiveQueues : getInactiveQueuesByPrecedence().values()) {
            queueSingleLinesTo(writer, inactiveQueues.iterator());
        }
        
        writer.print("\n -----===== RETIRED QUEUES =====-----\n");
        queueSingleLinesTo(writer, getRetiredQueues().iterator());
    }

    /** Compact report of all nonempty queues (one queue per line)
     * 
     * @param writer
     */
    public void allQueuesReportTo(PrintWriter writer) {
        queueSingleLinesTo(writer, allQueues.keySet().iterator());
    }
    
    /**
     * Writer the single-line reports of all queues in the
     * iterator to the writer 
     * 
     * @param writer to receive report
     * @param iterator over queues of interest.
     */
    private void queueSingleLinesTo(PrintWriter writer, Iterator<?> iterator) {
        Object obj;
        WorkQueue q;
        boolean legendWritten = false;
        while( iterator.hasNext()) {
            obj = iterator.next();
            if (obj ==  null) {
                continue;
            }
            if(obj instanceof WorkQueue) {
                q = (WorkQueue)obj;
            } else if (obj instanceof DelayedWorkQueue) {
                q = ((DelayedWorkQueue)obj).getWorkQueue(this);
            } else {
                try {
                    q = this.allQueues.get((String)obj);
                } catch (ClassCastException cce) {
                    logger.log(Level.SEVERE,"not convertible to workqueue:"+obj,cce);
                    q = null; 
                }
            }

            if(q != null) {
                if(!legendWritten) {
                    writer.println(q.shortReportLegend());
                    legendWritten = true;
                }
                q.shortReportLineTo(writer);
            } else {
                writer.print(" ERROR: "+obj);
            }
        }       
    }

    /**
     * Extract some of the elements in the given collection to an
     * ArrayList.  This method synchronizes on the given collection's
     * monitor.  The returned list will never contain more than the
     * specified maximum number of elements.
     * 
     * @param c    the collection whose elements to extract
     * @param max  the maximum number of elements to extract
     * @return  the extraction
     */
    private static <T> ArrayList<T> extractSome(Collection<T> c, int max) {
        // Try to guess a sane initial capacity for ArrayList
        // Hopefully given collection won't grow more than 10 items
        // between now and the synchronized block...
        int initial = Math.min(c.size() + 10, max);
        int count = 0;
        ArrayList<T> list = new ArrayList<T>(initial);
        synchronized (c) {
            Iterator<T> iter = c.iterator();
            while (iter.hasNext() && (count < max)) {
                list.add(iter.next());
                count++;
            }
        }
        return list;
    }

    /**
     * Append queue report to general Frontier report.
     * @param w StringBuffer to append to.
     * @param iterator An iterator over 
     * @param total
     * @param max
     */
    @SuppressWarnings("rawtypes")
    protected void appendQueueReports(PrintWriter w, String label, Iterator<?> iterator,
            int total, int max) {
        Object obj;
        WorkQueue q;
        int count;
        for(count = 0; iterator.hasNext() && (count < max); count++) {
            obj = iterator.next();
            if (obj ==  null) {
                continue;
            }
            if(obj instanceof WorkQueue) {
                q = (WorkQueue)obj;
            } else if (obj instanceof DelayedWorkQueue) {
                q = (WorkQueue)((DelayedWorkQueue)obj).getWorkQueue(this);
            } else if (obj instanceof Map.Entry) {
                q = this.allQueues.get((String)((Map.Entry)obj).getKey());
            } else {
                q = this.allQueues.get((String)obj);
            }
            if(q != null) {
                w.println(label+"#"+count+":");
                q.reportTo(w);
            } else {
                w.print("WARNING: No report for queue "+obj);
            }
        }
        count++;
        if(count < total) {
            w.print("...and " + (total - count) + " more "+label+".\n");
        }
    }

    /**
     * Force logging, etc. of operator- deleted CrawlURIs
     * 
     * @see org.archive.crawler.framework.Frontier#deleted(org.archive.modules.CrawlURI)
     */
    public void deleted(CrawlURI curi) {
        //treat as disregarded
        appCtx.publishEvent(
            new CrawlURIDispositionEvent(this,curi,DISREGARDED));
        log(curi);
        incrementDisregardedUriCount();
        curi.stripToMinimal();
        curi.processingCleanup();
    }

    public void considerIncluded(CrawlURI curi) {
        sheetOverlaysManager.applyOverlaysTo(curi);
        if(curi.getClassKey()==null) {
            // remedial processing
            preparer.prepare(curi);
        }
        this.uriUniqFilter.note(curi.getCanonicalString());
        try {
            KeyedProperties.loadOverridesFrom(curi);
            curi.setClassKey(getClassKey(curi));
            WorkQueue wq = getQueueFor(curi.getClassKey());
            wq.expend(curi.getHolderCost());
            wq.makeDirty();
        } finally {
            KeyedProperties.clearOverridesFrom(curi); 
        }
    }
    
    /**
     * Returns <code>true</code> if the WorkQueue implementation of this
     * Frontier stores its workload on disk instead of relying
     * on serialization mechanisms.
     * 
     * TODO: rename! (this is a very misleading name) or kill (don't
     * see any implementations that return false)
     * 
     * @return a constant boolean value for this class/instance
     */
    protected abstract boolean workQueueDataOnDisk();

    public long averageDepth() {
        if(inProcessQueues==null || readyClassQueues==null || snoozedClassQueues==null) {
            return 0; 
        }
        int inProcessCount = inProcessQueues.size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = getSnoozedCount();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int inactiveCount = getTotalInactiveQueues();
        int totalQueueCount = (activeCount+inactiveCount);
        return (totalQueueCount == 0) ? 0 : queuedUriCount.get() / totalQueueCount;
    }
    
    protected int getSnoozedCount() {
        return snoozedClassQueues.size() + snoozedOverflowCount.get();
    }
    
    public float congestionRatio() {
        if(inProcessQueues==null || readyClassQueues==null || snoozedClassQueues==null) {
            return 0; 
        }
        int inProcessCount = inProcessQueues.size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = getSnoozedCount();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int eligibleInactiveCount = getTotalEligibleInactiveQueues();
        return (float)(activeCount + eligibleInactiveCount) / (inProcessCount + snoozedCount);
    }
    public long deepestUri() {
        return largestQueues.getTopSet().size()==0 ? -1 : largestQueues.getTopSet().get(largestQueues.getLargest());
    }
    
    /** 
     * Return whether frontier is exhausted: all crawlable URIs done (none
     * waiting or pending). Only gives precise answer inside managerThread.
     * 
     * @see org.archive.crawler.framework.Frontier#isEmpty()
     */
    public boolean isEmpty() {
        return queuedUriCount.get() == 0 
            && (uriUniqFilter == null || uriUniqFilter.pending() == 0)
            && futureUriCount.get() == 0;
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.AbstractFrontier#getInProcessCount()
     */
    @Override
    protected int getInProcessCount() {
        return inProcessQueues.size();
    }
    
} // TODO: slim class! Suspect it should be < 800 lines, shedding budgeting/reporting