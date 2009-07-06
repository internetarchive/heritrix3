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

import static org.archive.crawler.datamodel.CoreAttributeConstants.A_FORCE_RETIRE;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.DEFERRED_FOR_RETRY;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.DISREGARDED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.FAILED;
import static org.archive.crawler.event.CrawlURIDispositionEvent.Disposition.SUCCEEDED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DEFERRED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_RUNTIME_EXCEPTION;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.SortedMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.collections.Bag;
import org.apache.commons.collections.BagUtils;
import org.apache.commons.collections.bag.HashBag;
import org.archive.crawler.datamodel.CrawlURI;
import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.crawler.datamodel.UriUniqFilter.CrawlUriReceiver;
import org.archive.crawler.event.CrawlURIDispositionEvent;
import org.archive.crawler.framework.ToeThread;
import org.archive.crawler.frontier.precedence.BaseQueuePrecedencePolicy;
import org.archive.crawler.frontier.precedence.CostUriPrecedencePolicy;
import org.archive.crawler.frontier.precedence.QueuePrecedencePolicy;
import org.archive.crawler.frontier.precedence.UriPrecedencePolicy;
import org.archive.net.UURI;
import org.archive.spring.KeyedProperties;
import org.archive.util.ArchiveUtils;
import org.archive.util.Transform;
import org.archive.util.Transformer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.support.AbstractApplicationContext;

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
implements Closeable, CrawlUriReceiver, Serializable, 
ApplicationContextAware {
    private static final long serialVersionUID = 570384305871965843L;

    /** truncate reporting of queues at some large but not unbounded number */
    private static final int REPORT_MAX_QUEUES = 2000;
    
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
    long snoozeLongMs = 5L*60L*1000L; 
    public long getSnoozeLongMs() {
        return snoozeLongMs;
    }
    public void setSnoozeLongMs(long snooze) {
        this.snoozeLongMs = snooze;
    }
    
    private static final Logger logger =
        Logger.getLogger(WorkQueueFrontier.class.getName());
    
    // ApplicationContextAware implementation, for eventing
    AbstractApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = (AbstractApplicationContext)applicationContext;
    }
    
    /**
     * Whether queues should start INACTIVE (only becoming active 
     * when needed to keep the crawler busy), or if queues should 
     * start out ready (which means all nonempty queues are 
     * considered in a round-robin fashion)
     * 
     * @return true if new queues should held inactive
     */
    boolean holdQueues = true; 
    public boolean getHoldQueues() {
        return holdQueues;
    }
    public void setHoldQueues(boolean holdQueues) {
        this.holdQueues = holdQueues;
        
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

    /** cost assignment policy to use. */
    {
        setCostAssignmentPolicy(new UnitCostAssignmentPolicy());
    }
    public CostAssignmentPolicy getCostAssignmentPolicy() {
        return (CostAssignmentPolicy) kp.get("costAssignmentPolicy");
    }
    public void setCostAssignmentPolicy(CostAssignmentPolicy policy) {
        kp.put("costAssignmentPolicy",policy);
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

    /** URI precedence assignment policy to use. */
    {
        setUriPrecedencePolicy(new CostUriPrecedencePolicy());
    }
    public UriPrecedencePolicy getUriPrecedencePolicy() {
        return (UriPrecedencePolicy) kp.get("uriPrecedencePolicy");
    }
    public void setUriPrecedencePolicy(UriPrecedencePolicy policy) {
        kp.put("uriPrecedencePolicy",policy);
    }

    /** All known queues.
     */
    protected ConcurrentMap<String,WorkQueue> allQueues = null; 
    // of classKey -> ClassKeyQueue

    /**
     * All per-class queues whose first item may be handed out.
     * Linked-list of keys for the queues.
     */
    protected BlockingQueue<String> readyClassQueues;
    
    /** all per-class queues from whom a URI is outstanding */
    protected Bag inProcessQueues = 
        BagUtils.synchronizedBag(new HashBag()); // of ClassKeyQueue
    
    /**
     * All per-class queues held in snoozed state, sorted by wake time.
     */
    transient protected DelayQueue<DelayedWorkQueue> snoozedClassQueues;
    
    transient protected WorkQueue longestActiveQueue = null;

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
        super.start();
        uriUniqFilter.setDestination(this);
        
        try {
            initInternalQueues(false);
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
    protected void initInternalQueues(boolean recycle) 
    throws IOException, DatabaseException {
        if (workQueueDataOnDisk()
                && getQueueAssignmentPolicy().maximumNumberOfKeys() >= 0
                && getQueueAssignmentPolicy().maximumNumberOfKeys() <= 
                    MAX_QUEUES_TO_HOLD_ALLQUEUES_IN_MEMORY) {
            this.allQueues = 
                new ConcurrentHashMap<String,WorkQueue>(701, .9f, 100);
        } else {
            this.initAllQueues();
        }
        this.initOtherQueues(recycle);
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
    protected abstract void initOtherQueues(boolean recycle) throws DatabaseException;

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.AbstractFrontier#crawlEnded(java.lang.String)
     */
    public void close() {
        // Cleanup.  CrawlJobs persist after crawl has finished so undo any
        // references.
        if (this.uriUniqFilter != null) {
            this.uriUniqFilter.close();
//            this.alreadyIncluded = null;
        }

//        this.queueAssignmentPolicy = null;
        
        try {
            closeQueue();
        } catch (IOException e) {
            // FIXME exception handling
            e.printStackTrace();
        }
        
        this.allQueues.clear();
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
        assert Thread.currentThread() == managerThread;
        assert KeyedProperties.overridesActiveFrom(curi); 
        
        prepForFrontier(curi);
        applySpecialHandling(curi);
        sendToQueue(curi);
    }
    
    /**
     * Arrange for the given CrawlURI to be visited, if it is not
     * already scheduled/completed.
     *
     * @see org.archive.crawler.framework.Frontier#schedule(org.archive.crawler.datamodel.CrawlURI)
     */
    protected void processScheduleIfUnique(CrawlURI curi) {
        assert Thread.currentThread() == managerThread;
        assert KeyedProperties.overridesActiveFrom(curi); 
        
        // Canonicalization may set forceFetch flag.  See
        // #canonicalization(CrawlURI) javadoc for circumstance.
        String canon = canonicalize(curi);
        if (curi.forceFetch()) {
            uriUniqFilter.addForce(canon, curi);
        } else {
            uriUniqFilter.add(canon, curi);
        }
    }

	/* (non-Javadoc)
	 * @see org.archive.crawler.frontier.AbstractFrontier#asCrawlUri(org.archive.crawler.datamodel.CrawlURI)
	 */
	protected void prepForFrontier(CrawlURI caUri) {
		super.prepForFrontier(caUri);
		// force cost to be calculated, pre-insert
		getCost(caUri);
        // set
        // TODO:SPRINGY set overrides by curi? 
        getUriPrecedencePolicy().uriScheduled(caUri);
	}
	
    /**
     * Send a CrawlURI to the appropriate subqueue.
     * 
     * @param curi
     */
    protected void sendToQueue(CrawlURI curi) {
        assert Thread.currentThread() == managerThread;
        
        WorkQueue wq = getQueueFor(curi);
        int originalPrecedence = wq.getPrecedence();
        
        wq.enqueue(this, curi);
        // Update recovery log.
        doJournalAdded(curi);
        
        if(wq.isRetired()) {
            return; 
        }
        incrementQueuedUriCount();
        if(wq.isHeld()) {
            if(wq.isActive()) {
                // queue active -- promote will be handled ok by normal cycling
                // do nothing
            } else {
                // queue is already in a waiting inactive queue; update
                int currentPrecedence = wq.getPrecedence();
                if(currentPrecedence < originalPrecedence ) {
                    // queue bumped up; adjust ordering
                    deactivateQueue(wq);
                    // this intentionally places queue in duplicate inactiveQueue\
                    // only when it comes off the right queue will it activate;
                    // otherwise it reenqueues to right inactive queue, if not
                    // already there (see activateInactiveQueue())
                    if(logger.isLoggable(Level.FINE)) {
                        logger.log(Level.FINE,
                                "queue re-deactivated to p" +currentPrecedence 
                                + ": " + wq.getClassKey());
                    }
                } else {
                    // queue bumped down or stayed same; 
                    // do nothing until it comes up
                }
            } 
        } else {
            // begin juggling queue between internal ordering structures
            wq.setHeld();
            if(getHoldQueues()) {
                deactivateQueue(wq);
            } else {
                replenishSessionBalance(wq);
                readyQueue(wq);
            }
        }
        WorkQueue laq = longestActiveQueue;
        if(((laq==null) || wq.getCount() > laq.getCount())) {
            longestActiveQueue = wq; 
        }

    }

    /**
     * Put the given queue on the readyClassQueues queue
     * @param wq
     */
    private void readyQueue(WorkQueue wq) {
        assert Thread.currentThread() == managerThread;

        try {
            wq.setActive(this, true);
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
    private void deactivateQueue(WorkQueue wq) {
        assert Thread.currentThread() == managerThread;
        
        wq.setSessionBalance(0); // zero out session balance
        int precedence = wq.getPrecedence();
        if(!wq.getOnInactiveQueues().contains(precedence)) {
            // not already on target, add
            Queue<String> inactiveQueues = 
                getInactiveQueuesForPrecedence(precedence);
            inactiveQueues.add(wq.getClassKey());
            wq.getOnInactiveQueues().add(precedence);
            if(wq.getPrecedence() < highestPrecedenceWaiting ) {
                highestPrecedenceWaiting = wq.getPrecedence();
            }
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "queue deactivated to p" + precedence 
                        + ": " + wq.getClassKey());
            }
        } else {
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "queue already p" + precedence+": " + wq.getClassKey());
            }
        }
        wq.setActive(this, false);
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
     * Return a sorted map of all inactive queues, keyed by precedence
     * @return SortedMap<Integer, Queue<String>> of inactiveQueues
     */
    abstract SortedMap<Integer, Queue<String>> getInactiveQueuesByPrecedence();

    /**
     * Create an inactiveQueue to hold queue names at the given precedence
     * @param precedence
     * @return Queue<String> for names of inactive queues
     */
    abstract Queue<String> createInactiveQueueForPrecedence(int precedence);

    /**
     * Put the given queue on the retiredQueues queue
     * @param wq
     */
    private void retireQueue(WorkQueue wq) {
        assert Thread.currentThread() == managerThread;

        getRetiredQueues().add(wq.getClassKey());
        decrementQueuedCount(wq.getCount());
        wq.setRetired(true);
        if(logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE,
                    "queue retired: " + wq.getClassKey());
        }
        wq.setActive(this, false);
    }
    
    /**
     * Return queue of all retired queue names.
     * 
     * @return Queue<String> of retired queue names
     */
    abstract Queue<String> getRetiredQueues();

    /** 
     * Accomodate any changes in settings.
     */
    public void reconsiderRetiredQueues() {

        // The rules for a 'retired' queue may have changed; so,
        // unretire all queues to 'inactive'. If they still qualify
        // as retired/overbudget next time they come up, they'll
        // be re-retired; if not, they'll get a chance to become
        // active under the new rules.
        
        // TODO: Only do this when necessary.
        
        Object key = getRetiredQueues().poll();
        while (key != null) {
            WorkQueue q = (WorkQueue)this.allQueues.get(key);
            if(q != null) {
                unretireQueue(q);
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
        assert Thread.currentThread() == managerThread;

        deactivateQueue(q);
        q.setRetired(false); 
        incrementQueuedUriCount(q.getCount());
    }

    /**
     * Return the work queue for the given CrawlURI's classKey. URIs
     * are ordered and politeness-delayed within their 'class'.
     * If the requested queue is not found, a new instance is created.
     * 
     * @param curi CrawlURI to base queue on
     * @return the found or created ClassKeyQueue
     */
    protected abstract WorkQueue getQueueFor(CrawlURI curi);

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
            assert Thread.currentThread() == managerThread;
            // wake any snoozed queues
            wakeQueues();
            // activate enough inactive queues to fill outbound
            int activationsWanted = 
                outbound.remainingCapacity() - readyClassQueues.size();
            while(activationsWanted > 0 
                    && !getInactiveQueuesByPrecedence().isEmpty() 
                    && highestPrecedenceWaiting < getPrecedenceFloor()) {
                activateInactiveQueue();
                activationsWanted--;
            }
                   
            // find a non-empty ready queue, if any 
            // TODO: refactor to untangle these loops, early-exits, etc!
            WorkQueue readyQ = null;
            findauri: while(true) {
                findaqueue: do {
                    String key = readyClassQueues.poll();
                    if(key== null) {
                        // no ready queues
                        break;
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
                        // release held status, allowing any subsequent 
                        // enqueues to again put queue in ready
                        readyQ.clearHeld();
                        readyQ = null;
                    }
                } while (readyQ == null);
                
                if (readyQ == null) {
                    break findauri; 
                }
               
                assert !inProcessQueues.contains(readyQ) : "double activation";
                returnauri: while(true) { // loop left by explicit return or break on empty
                    CrawlURI curi = null;
                    curi = readyQ.peek(this);   
                    if(curi == null) {
                        // should not reach
                        logger.severe("No CrawlURI from ready non-empty queue "
                                + readyQ.classKey + "\n" 
                                + readyQ.singleLineLegend() + "\n"
                                + readyQ.singleLineReport() + "\n");
                        break returnauri;
                    }
                    
                    // from queues, override names persist but not map source
                    curi.setOverlayMapsSource(sheetOverlaysManager);
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
                        inProcessQueues.add(readyQ);
                        return curi;
                    }
                    // URI's assigned queue has changed since it
                    // was queued (eg because its IP has become
                    // known). Requeue to new queue.
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
                        readyQ.clearHeld();
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
            
            // never return null if there are any eligible inactives
            if(getTotalEligibleInactiveQueues()>0) {
                return findEligibleURI();
            }
            
            // nothing eligible
            return null; 
    }

    /**
     * Return the 'cost' of a CrawlURI (how much of its associated
     * queue's budget it depletes upon attempted processing)
     * 
     * @param curi
     * @return the associated cost
     */
    private int getCost(CrawlURI curi) {
        assert KeyedProperties.overridesActiveFrom(curi);
        
        int cost = curi.getHolderCost();
        if (cost == CrawlURI.UNCALCULATED) {
            //TODO:SPRINGY push overrides by curi
            cost = getCostAssignmentPolicy().costOf(curi);
            curi.setHolderCost(cost);
        }
        return cost;
    }
    
    /**
     * Activate an inactive queue, if any are available. 
     */
    private void activateInactiveQueue() {
        assert Thread.currentThread() == managerThread;

        SortedMap<Integer,Queue<String>> inactiveQueuesByPrecedence = 
            getInactiveQueuesByPrecedence();
        
        int targetPrecedence = highestPrecedenceWaiting;
        Queue<String> inactiveQueues = inactiveQueuesByPrecedence.get(
                targetPrecedence);

        Object key = inactiveQueues.poll();
        assert key != null : "empty precedence queue in map";
        
        if(inactiveQueues.isEmpty()) {
            updateHighestWaiting(targetPrecedence+1);
        }
        
        WorkQueue candidateQ = (WorkQueue) this.allQueues.get(key);
        
        assert candidateQ != null : "missing uri work queue";
        
        boolean was = candidateQ.getOnInactiveQueues().remove(targetPrecedence);
        
        assert was : "queue didn't know it was in "+targetPrecedence+" inactives";
        
        if(candidateQ.isActive()) {
            // queue had been multiply-scheduled due to changing precedence
            // already active, so ignore this activation
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "queue activated+ignored/active from p" + targetPrecedence
                        + ": " + candidateQ.getClassKey());
            }
            return; 
        }
        
        if(candidateQ.getPrecedence() < targetPrecedence) {
            // queue moved up; do nothing (already handled)
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "queue activated+ignored/higher from p" 
                        + targetPrecedence 
                        + ": " + candidateQ.getClassKey() 
                        + " ("+candidateQ.getPrecedence() + ") ");
            }
            return; 
        }
        if(candidateQ.getPrecedence() > targetPrecedence) {
            // queue moved down; deactivate to new level
            if(logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                        "queue activated+deactivated from p" + targetPrecedence
                        + ": " + candidateQ.getClassKey());
            }
            deactivateQueue(candidateQ);
            return; 
        }
        replenishSessionBalance(candidateQ);
        if (candidateQ.isOverBudget()) {
            // if still over-budget after an activation & replenishing,
            // retire
            retireQueue(candidateQ);
            return;
        }
//        long now = System.currentTimeMillis();
//        long delay_ms = candidateQ.getWakeTime() - now;
//        if (delay_ms > 0) {
//            // queue still due for snoozing
//            snoozeQueue(candidateQ, now, delay_ms);
//            return;
//        }
        candidateQ.setWakeTime(0); // clear obsolete wake time, if any
        readyQueue(candidateQ);
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
     * Replenish the budget of the given queue by the appropriate amount.
     * 
     * @param queue queue to replenish
     */
    private void replenishSessionBalance(WorkQueue queue) {
        assert queue.peekItem == null : "unexpected peekItem set";
        // get a CrawlURI for override context purposes
        CrawlURI contextUri = queue.peek(this); 
        if(contextUri == null) {
            // use globals TODO: fix problems this will cause if 
            // global total budget < override on empty queue
            queue.setSessionBalance(getBalanceReplenishAmount());
            queue.setTotalBudget(getQueueTotalBudget());
            return;
        }
        // TODO: consider confusing cross-effects of this and IP-based politeness

        contextUri.setOverlayMapsSource(sheetOverlaysManager);
        try {
            // TODO:SPRINGY set override
            KeyedProperties.loadOverridesFrom(contextUri);
  
            //queue.setSessionBalance(contextUri.get(this, BALANCE_REPLENISH_AMOUNT));
            queue.setSessionBalance(getBalanceReplenishAmount());
            
            // reset total budget (it may have changed)
            // TODO: is this the best way to be sensitive to potential mid-crawl changes
            // TODO:SPRINGY set override
            //long totalBudget = contextUri.get(this, QUEUE_TOTAL_BUDGET);
            long totalBudget = getQueueTotalBudget();
            queue.setTotalBudget(totalBudget);
            queue.unpeek(contextUri); // don't insist on that URI being next released
        } finally {
            KeyedProperties.clearOverridesFrom(contextUri); 
        }
    }

    /**
     * Enqueue the given queue to either readyClassQueues or inactiveQueues,
     * as appropriate.
     * 
     * @param wq
     */
    private void reenqueueQueue(WorkQueue wq) { 
        //TODO:SPRINGY set overrides by queue? 
        getQueuePrecedencePolicy().queueReevaluate(wq);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("queue reenqueued: " +
                wq.getClassKey());
        }
        if(highestPrecedenceWaiting < wq.getPrecedence() 
            || (wq.isOverBudget() && highestPrecedenceWaiting <= wq.getPrecedence())
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
     * Wake any queues sitting in the snoozed queue whose time has come.
     */
    protected void wakeQueues() {
        DelayedWorkQueue waked; 
        while((waked = snoozedClassQueues.poll())!=null) {
            WorkQueue queue = waked.getWorkQueue();
            queue.setWakeTime(0);
            reenqueueQueue(queue);
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
     *  (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#finished(org.archive.crawler.datamodel.CrawlURI)
     */
    protected void processFinish(CrawlURI curi) {
        assert Thread.currentThread() == managerThread;
        
        long now = System.currentTimeMillis();

        curi.incrementFetchAttempts();
        logNonfatalErrors(curi);
        WorkQueue wq = (WorkQueue) curi.getHolder();
        assert (wq.peek(this) == curi) : "unexpected peek " + wq;
        inProcessQueues.remove(wq, 1);

        if(includesRetireDirective(curi)) {
            // CrawlURI is marked to trigger retirement of its queue
            curi.processingCleanup();
            wq.unpeek(curi);
            wq.update(this, curi); // rewrite any changes
            retireQueue(wq);
            return;
        }
        
        if (needsRetrying(curi)) {
            // Consider errors which can be retried, leaving uri atop queue
            if(curi.getFetchStatus()!=S_DEFERRED) {
                wq.expend(getCost(curi)); // all retries but DEFERRED cost
            }
            long delay_sec = retryDelayFor(curi);
            curi.processingCleanup(); // lose state that shouldn't burden retry

                wq.unpeek(curi);
                // TODO: consider if this should happen automatically inside unpeek()
                wq.update(this, curi); // rewrite any changes
                if (delay_sec > 0) {
                    long delay_ms = delay_sec * 1000;
                    snoozeQueue(wq, now, delay_ms);
                } else {
                    reenqueueQueue(wq);
                }

            // Let everyone interested know that it will be retried.
            appCtx.publishEvent(
                new CrawlURIDispositionEvent(this,curi,DEFERRED_FOR_RETRY));
            doJournalRescheduled(curi);
            return;
        }

        // Curi will definitely be disposed of without retry, so remove from queue
        wq.dequeue(this,curi);
        decrementQueuedCount(1);
        log(curi);

        if (curi.isSuccess()) {
            totalProcessedBytes += curi.getRecordedSize();
            incrementSucceededFetchCount();
            // Let everyone know in case they want to do something before we strip the curi.
            appCtx.publishEvent(
                new CrawlURIDispositionEvent(this,curi,SUCCEEDED));
            doJournalFinishedSuccess(curi);
            wq.expend(getCost(curi)); // successes cost
        } else if (isDisregarded(curi)) {
            // Check for codes that mean that while we the crawler did
            // manage to schedule it, it must be disregarded for some reason.
            incrementDisregardedUriCount();
            // Let interested listeners know of disregard disposition.
            appCtx.publishEvent(
                new CrawlURIDispositionEvent(this,curi,DISREGARDED));
            doJournalDisregarded(curi);
            // if exception, also send to crawlErrors
            if (curi.getFetchStatus() == S_RUNTIME_EXCEPTION) {
                Object[] array = { curi };
                loggerModule.getRuntimeErrors().log(Level.WARNING, curi.getUURI()
                        .toString(), array);
            }
            // TODO: consider reinstating forget-uri
        } else {
            // In that case FAILURE, note & log
            //Let interested listeners know of failed disposition.
            appCtx.publishEvent(
                new CrawlURIDispositionEvent(this,curi,FAILED));
            // if exception, also send to crawlErrors
            if (curi.getFetchStatus() == S_RUNTIME_EXCEPTION) {
                Object[] array = { curi };
                this.loggerModule.getRuntimeErrors().log(Level.WARNING, curi.getUURI()
                        .toString(), array);
            }
            incrementFailedFetchCount();
            // let queue note error
            //TODO:SPRINGY set overrides by curi or wq?
            assert KeyedProperties.overridesActiveFrom(curi);
            
            wq.noteError(getErrorPenaltyAmount());
            doJournalFinishedFailure(curi);
            wq.expend(getCost(curi)); // failures cost
        }

        long delay_ms = politenessDelayFor(curi);

            if (delay_ms > 0) {
                snoozeQueue(wq,now,delay_ms);
            } else {
                reenqueueQueue(wq);
            }


        curi.stripToMinimal();
        curi.processingCleanup();

    }

    private boolean includesRetireDirective(CrawlURI curi) {
        return curi.containsDataKey(A_FORCE_RETIRE) 
         && (Boolean)curi.getData().get(A_FORCE_RETIRE);
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
//        long snoozeToInactiveDelayMs = get(SNOOZE_DEACTIVATE_MS);
//        if (delay_ms > snoozeToInactiveDelayMs && !inactiveQueues.isEmpty()) {
//            deactivateQueue(wq);
//        } else {
            snoozedClassQueues.add(new DelayedWorkQueue(wq));
//        }
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
        uriUniqFilter.forget(canonicalize(curi.getUURI()), curi);
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
        // TODO: DANGER/ values() may not work right from CachedBdbMap
        Pattern queuePat = Pattern.compile(queueRegex);
        for (String qname: allQueues.keySet()) {
            if (queuePat.matcher(qname).matches()) {
                WorkQueue wq = getQueueFor(qname);
                wq.unpeek(null);
                count += wq.deleteMatching(this, uriRegex);
            }
        }
        decrementQueuedCount(count);
        return count;
    }

    //
    // Reporter implementation
    //
    
    public static String STANDARD_REPORT = "standard";
    public static String ALL_NONEMPTY = "nonempty";
    public static String ALL_QUEUES = "all";
    protected static String[] REPORTS = {STANDARD_REPORT,ALL_NONEMPTY,ALL_QUEUES};
    
    public String[] getReports() {
        return REPORTS;
    }
    
    /**
     * @param w Where to write to.
     */
    public void singleLineReportTo(PrintWriter w) {
        if (this.allQueues == null) {
            return;
        }
        int allCount = allQueues.size();
        int inProcessCount = inProcessQueues.uniqueSet().size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = snoozedClassQueues.size();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int inactiveCount = getTotalEligibleInactiveQueues();
        int ineligibleCount = getTotalIneligibleInactiveQueues();
        int retiredCount = getRetiredQueues().size();
        int exhaustedCount = 
            allCount - activeCount - inactiveCount - retiredCount;
        int inCount = inbound.size();
        int outCount = outbound.size();
        State last = lastReachedState;
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
        w.print(" ["+last+ ": "+inCount+" in, "+outCount+" out]");        
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
    public String singleLineLegend() {
        return "total active in-process ready snoozed inactive retired exhausted";
    }

    /**
     * This method compiles a human readable report on the status of the frontier
     * at the time of the call.
     * @param name Name of report.
     * @param writer Where to write to.
     */
    public synchronized void reportTo(String name, PrintWriter writer) {
        if(ALL_NONEMPTY.equals(name)) {
            allNonemptyReportTo(writer);
            return;
        }
        if(ALL_QUEUES.equals(name)) {
            allQueuesReportTo(writer);
            return;
        }
        if(name!=null && !STANDARD_REPORT.equals(name)) {
            writer.print(name);
            writer.print(" unavailable; standard report:\n");
        }
        standardReportTo(writer);
    }   
    
    /** Compact report of all nonempty queues (one queue per line)
     * 
     * @param writer
     */
    private void allNonemptyReportTo(PrintWriter writer) {
        ArrayList<WorkQueue> inProcessQueuesCopy;
        synchronized(this.inProcessQueues) {
            // grab a copy that will be stable against mods for report duration 
            @SuppressWarnings("unchecked")
            Collection<WorkQueue> inProcess = this.inProcessQueues;
            inProcessQueuesCopy = new ArrayList<WorkQueue>(inProcess);
        }
        writer.print("\n -----===== IN-PROCESS QUEUES =====-----\n");
        queueSingleLinesTo(writer, inProcessQueuesCopy.iterator());

        writer.print("\n -----===== READY QUEUES =====-----\n");
        queueSingleLinesTo(writer, this.readyClassQueues.iterator());

        writer.print("\n -----===== SNOOZED QUEUES =====-----\n");
        queueSingleLinesTo(writer, this.snoozedClassQueues.iterator());
        
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
    private void allQueuesReportTo(PrintWriter writer) {
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
                q = ((DelayedWorkQueue)obj).getWorkQueue();
            } else {
                try {
                    q = (WorkQueue)this.allQueues.get(obj);
                } catch (ClassCastException cce) {
                    logger.log(Level.SEVERE,"not convertible to workqueue:"+obj,cce);
                    q = null; 
                }
            }
                
            if(q == null) {
                writer.print(" ERROR: "+obj);
            }
            if(!legendWritten) {
                writer.println(q.singleLineLegend());
                legendWritten = true;
            }
            q.singleLineReportTo(writer);
        }       
    }

    /**
     * @param w Writer to print to.
     */
    private void standardReportTo(PrintWriter w) {
        int allCount = allQueues.size();
        int inProcessCount = inProcessQueues.uniqueSet().size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = snoozedClassQueues.size();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int inactiveCount = getTotalInactiveQueues();
        int retiredCount = getRetiredQueues().size();
        int exhaustedCount = 
            allCount - activeCount - inactiveCount - retiredCount;

        w.print("Frontier report - ");
        w.print(ArchiveUtils.get12DigitDate());
        w.print("\n");
        w.print(" Job being crawled: ");
        w.print(controller.getMetadata().getJobName());
        w.print("\n");
        w.print("\n -----===== STATS =====-----\n");
        w.print(" Discovered:    ");
        w.print(Long.toString(discoveredUriCount()));
        w.print("\n");
        w.print(" Queued:        ");
        w.print(Long.toString(queuedUriCount()));
        w.print("\n");
        w.print(" Finished:      ");
        w.print(Long.toString(finishedUriCount()));
        w.print("\n");
        w.print("  Successfully: ");
        w.print(Long.toString(succeededFetchCount()));
        w.print("\n");
        w.print("  Failed:       ");
        w.print(Long.toString(failedFetchCount()));
        w.print("\n");
        w.print("  Disregarded:  ");
        w.print(Long.toString(disregardedUriCount()));
        w.print("\n");
        w.print("\n -----===== QUEUES =====-----\n");
        w.print(" Already included size:     ");
        w.print(Long.toString(uriUniqFilter.count()));
        w.print("\n");
        w.print("               pending:     ");
        w.print(Long.toString(uriUniqFilter.pending()));
        w.print("\n");
        w.print("\n All class queues map size: ");
        w.print(Long.toString(allCount));
        w.print("\n");
        w.print( "             Active queues: ");
        w.print(activeCount);
        w.print("\n");
        w.print("                    In-process: ");
        w.print(inProcessCount);
        w.print("\n");
        w.print("                         Ready: ");
        w.print(readyCount);
        w.print("\n");
        w.print("                       Snoozed: ");
        w.print(snoozedCount);
        w.print("\n");
        w.print("           Inactive queues: ");
        w.print(inactiveCount);
        w.print(" (");
        Map<Integer,Queue<String>> inactives = getInactiveQueuesByPrecedence();
        boolean betwixt = false; 
        for(Integer k : inactives.keySet()) {
            if(betwixt) {
                w.print("; ");
            }
            w.print("p");
            w.print(k);
            w.print(": ");
            w.print(inactives.get(k).size());
            betwixt = true; 
        }
        w.print(")\n");
        w.print("            Retired queues: ");
        w.print(retiredCount);
        w.print("\n");
        w.print("          Exhausted queues: ");
        w.print(exhaustedCount);
        w.print("\n");
        
        w.print("\n -----===== MANAGER THREAD =====-----\n");
        ToeThread.reportThread(managerThread, w);
        
        w.print("\n -----===== IN-PROCESS QUEUES =====-----\n");
        @SuppressWarnings("unchecked")
        Collection<WorkQueue> inProcess = inProcessQueues;
        ArrayList<WorkQueue> copy = extractSome(inProcess, REPORT_MAX_QUEUES);
        appendQueueReports(w, copy.iterator(), copy.size(), REPORT_MAX_QUEUES);
        
        w.print("\n -----===== READY QUEUES =====-----\n");
        appendQueueReports(w, this.readyClassQueues.iterator(),
            this.readyClassQueues.size(), REPORT_MAX_QUEUES);

        w.print("\n -----===== SNOOZED QUEUES =====-----\n");
        Transformer<DelayedWorkQueue,WorkQueue> ter = 
            new Transformer<DelayedWorkQueue, WorkQueue>() {
                public WorkQueue transform(DelayedWorkQueue dwq) {
                    return dwq.getWorkQueue();
                }
            };
        Transform<DelayedWorkQueue, WorkQueue> t = 
            new Transform<DelayedWorkQueue,WorkQueue>(snoozedClassQueues, ter);
        copy = extractSome(t, REPORT_MAX_QUEUES);
        appendQueueReports(w, copy.iterator(), copy.size(), REPORT_MAX_QUEUES);
        
        WorkQueue longest = longestActiveQueue;
        if (longest != null) {
            w.print("\n -----===== LONGEST QUEUE =====-----\n");
            longest.reportTo(w);
        }

        w.print("\n -----===== INACTIVE QUEUES =====-----\n");
        for(Queue<String> inactiveQueues : getInactiveQueuesByPrecedence().values()) {
            appendQueueReports(w, inactiveQueues.iterator(),
                    inactiveQueues.size(), REPORT_MAX_QUEUES);
        }
        
        w.print("\n -----===== RETIRED QUEUES =====-----\n");
        appendQueueReports(w, getRetiredQueues().iterator(),
            getRetiredQueues().size(), REPORT_MAX_QUEUES);

        w.flush();
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
    protected void appendQueueReports(PrintWriter w, Iterator<?> iterator,
            int total, int max) {
        Object obj;
        WorkQueue q;
        for(int count = 0; iterator.hasNext() && (count < max); count++) {
            obj = iterator.next();
            if (obj ==  null) {
                continue;
            }
            q = (obj instanceof WorkQueue)?
                (WorkQueue)obj:
                (WorkQueue)this.allQueues.get(obj);
            if(q == null) {
                w.print("WARNING: No report for queue "+obj);
            }
            q.reportTo(w);
        }
        if(total > max) {
            w.print("...and " + (total - max) + " more.\n");
        }
    }

    /**
     * Force logging, etc. of operator- deleted CrawlURIs
     * 
     * @see org.archive.crawler.framework.Frontier#deleted(org.archive.crawler.datamodel.CrawlURI)
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

    public void considerIncluded(UURI u) {
        this.uriUniqFilter.note(canonicalize(u));
        CrawlURI temp = new CrawlURI(u);
        applyOverridesTo(temp);
        try {
            KeyedProperties.loadOverridesFrom(temp);
            temp.setClassKey(getClassKey(temp));
            getQueueFor(temp).expend(getCost(temp));
        } finally {
            KeyedProperties.clearOverridesFrom(temp); 
        }
        
    }
    
    protected abstract void closeQueue() throws IOException;
    
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
    
    
    public FrontierGroup getGroup(CrawlURI curi) {
        return getQueueFor(curi);
    }
    
    
    public long averageDepth() {
        if(inProcessQueues==null || readyClassQueues==null || snoozedClassQueues==null) {
            return 0; 
        }
        int inProcessCount = inProcessQueues.uniqueSet().size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = snoozedClassQueues.size();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int inactiveCount = getTotalInactiveQueues();
        int totalQueueCount = (activeCount+inactiveCount);
        return (totalQueueCount == 0) ? 0 : queuedUriCount.get() / totalQueueCount;
    }
    public float congestionRatio() {
        if(inProcessQueues==null || readyClassQueues==null || snoozedClassQueues==null) {
            return 0; 
        }
        int inProcessCount = inProcessQueues.uniqueSet().size();
        int readyCount = readyClassQueues.size();
        int snoozedCount = snoozedClassQueues.size();
        int activeCount = inProcessCount + readyCount + snoozedCount;
        int eligibleInactiveCount = getTotalEligibleInactiveQueues();
        return (float)(activeCount + eligibleInactiveCount) / (inProcessCount + snoozedCount);
    }
    public long deepestUri() {
        return longestActiveQueue==null ? -1 : longestActiveQueue.getCount();
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
            && (inbound == null || inbound.isEmpty());
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.AbstractFrontier#getInProcessCount()
     */
    @Override
    protected int getInProcessCount() {
        return inProcessQueues.size();
    }
    
    /**
     * Custom deserialization: bring in snoozed queues as array of
     * their names (aka 'classKeys').
     * @param stream
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(java.io.ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        DelayedWorkQueue[] snoozed = (DelayedWorkQueue[])stream.readObject();
        snoozedClassQueues = new DelayQueue<DelayedWorkQueue>(
                Arrays.asList(snoozed));
    }
    
    /**
     * Custom serialization: write snoozed queues as array of their
     * names (aka 'classKeys').
     * 
     * @param stream
     * @throws IOException
     */
    private void writeObject(java.io.ObjectOutputStream stream)
    throws IOException {
        stream.defaultWriteObject();
        DelayedWorkQueue[] snoozed = snoozedClassQueues.toArray(
                new DelayedWorkQueue[0]);
        stream.writeObject(snoozed);
    }


    class DelayedWorkQueue implements Delayed, Serializable {

        private static final long serialVersionUID = 1L;

        public String classKey;
        public long wakeTime;
        
        /**
         * Something can become a WorkQueue instance.  This can be three things:
         * 
         * <ol>
         * <li>null, if this DelayedWorkQueue instance was recently 
         *   deserialized;
         * <li>A SoftReference&lt;WorkQueue&gt;, if the WorkQueue's waitTime
         *   exceeded SNOOZE_LONG_MS 
         * <li>A hard WorkQueue reference, if the WorkQueue's waitTime did not
         *   exceed SNOOZE_LONG_MS.  Idea here is that we thought we 
         *   needed the WorkQueue soon and didn't want to risk losing the 
         *   instance.
         * </ol>
         * 
         * The {@link #getWorkQueue()} method figures out what to return in
         * all three of the above cases.
         */
        private transient Object workQueue;
        
        public DelayedWorkQueue(WorkQueue queue) {
            this.classKey = queue.getClassKey();
            this.wakeTime = queue.getWakeTime();
            
            this.workQueue = queue;
        }
        
        private void setWorkQueue(WorkQueue queue) {
            long wakeTime = queue.getWakeTime();
            long delay = wakeTime - System.currentTimeMillis();
            if (delay > getSnoozeLongMs()) {
                this.workQueue = new SoftReference<WorkQueue>(queue);
            } else {
                this.workQueue = queue;
            }
        }
        
        
        public WorkQueue getWorkQueue() {
            if (workQueue == null) {
                // This is a recently deserialized DelayedWorkQueue instance
                WorkQueue result = getQueueFor(classKey);
                setWorkQueue(result);
                return result;
            }
            if (workQueue instanceof SoftReference) {
                @SuppressWarnings("unchecked")
                SoftReference<WorkQueue> ref = (SoftReference)workQueue;
                WorkQueue result = ref.get();
                if (result == null) {
                    result = getQueueFor(classKey);
                }
                setWorkQueue(result);
                return result;
            }
            return (WorkQueue)workQueue;
        }

        public long getDelay(TimeUnit unit) {
            return unit.convert(
                    wakeTime - System.currentTimeMillis(),
                    TimeUnit.MILLISECONDS);
        }
        
        public String getClassKey() {
            return classKey;
        }
        
        public long getWakeTime() {
            return wakeTime;
        }
        
        public void setWakeTime(long time) {
            this.wakeTime = time;
        }
        
        public int compareTo(Delayed obj) {
            if (this == obj) {
                return 0; // for exact identity only
            }
            DelayedWorkQueue other = (DelayedWorkQueue) obj;
            if (wakeTime > other.getWakeTime()) {
                return 1;
            }
            if (wakeTime < other.getWakeTime()) {
                return -1;
            }
            // at this point, the ordering is arbitrary, but still
            // must be consistent/stable over time
            return this.classKey.compareTo(other.getClassKey());        
        }
        
    }

}

