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

import static org.archive.modules.CoreAttributeConstants.A_NONFATAL_ERRORS;
import static org.archive.modules.fetcher.FetchStatusCodes.S_BLOCKED_BY_CUSTOM_PROCESSOR;
import static org.archive.modules.fetcher.FetchStatusCodes.S_BLOCKED_BY_USER;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_FAILED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_CONNECT_LOST;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DEFERRED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DELETED_BY_USER;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_UNRESOLVABLE;
import static org.archive.modules.fetcher.FetchStatusCodes.S_OUT_OF_SCOPE;
import static org.archive.modules.fetcher.FetchStatusCodes.S_ROBOTS_PRECLUDED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_TOO_MANY_EMBED_HOPS;
import static org.archive.modules.fetcher.FetchStatusCodes.S_TOO_MANY_LINK_HOPS;
import static org.archive.modules.fetcher.FetchStatusCodes.S_UNATTEMPTED;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URIException;
import org.archive.crawler.datamodel.UriUniqFilter.CrawlUriReceiver;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.prefetch.FrontierPreparer;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.crawler.spring.SheetOverlaysManager;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.extractor.ExtractorParameters;
import org.archive.modules.fetcher.FetchStats.Stage;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.modules.seeds.SeedListener;
import org.archive.modules.seeds.SeedModule;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;
import org.archive.util.ArchiveUtils;
import org.archive.util.ReportUtils;
import org.archive.util.iterator.LineReadingIterator;
import org.archive.util.iterator.RegexLineIterator;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * Shared facilities for Frontier implementations.
 * 
 * @author gojomo
 */
public abstract class AbstractFrontier 
    implements Frontier,
               SeedListener, 
               HasKeyedProperties,
               ExtractorParameters,
               CrawlUriReceiver,
               ApplicationListener<ApplicationEvent> {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 555881755284996860L;
    private static final Logger logger = Logger
            .getLogger(AbstractFrontier.class.getName());

    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    /** for retryable problems, seconds to wait before a retry */
    {
        setRetryDelaySeconds(900);
    }
    public int getRetryDelaySeconds() {
        return (Integer) kp.get("retryDelaySeconds");
    }
    public void setRetryDelaySeconds(int delay) {
        kp.put("retryDelaySeconds",delay);
    }
    
    /** maximum times to emit a CrawlURI without final disposition */
    {
        setMaxRetries(30);
    }
    public int getMaxRetries() {
        return (Integer) kp.get("maxRetries");
    }
    public void setMaxRetries(int maxRetries) {
        kp.put("maxRetries",maxRetries);
    }
    
    /**
     * Recover log on or off attribute.
     */
    {
        setRecoveryLogEnabled(true);
    }
    public boolean getRecoveryLogEnabled() {
        return (Boolean) kp.get("recoveryLogEnabled");
    }
    public void setRecoveryLogEnabled(boolean enabled) {
        kp.put("recoveryLogEnabled",enabled);
    }

    {
        setMaxOutlinks(6000);
    }
    public int getMaxOutlinks() {
        return (Integer) kp.get("maxOutlinks");
    }
    public void setMaxOutlinks(int max) {
        kp.put("maxOutlinks", max);
    }
    
    {
        setExtractIndependently(false);
    }
    public boolean getExtractIndependently() {
        return (Boolean) kp.get("extractIndependently");
    }
    public void setExtractIndependently(boolean extractIndependently) {
        kp.put("extractIndependently", extractIndependently);
    }
    
    {
        setExtract404s(true);
    }
    public boolean getExtract404s() {
        return (Boolean) kp.get("extract404s");
    }
    public void setExtract404s(boolean extract404s) {
        kp.put("extract404s", extract404s);
    }
    
    public boolean isRunning() {
        return managerThread!=null && managerThread.isAlive();
    }
    
    public void stop() {
        terminate();
        
        // XXX this happens at finish; move to teardown?
        ArchiveUtils.closeQuietly(this.recover);
    }


    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }
    
    protected SheetOverlaysManager sheetOverlaysManager;
    public SheetOverlaysManager getSheetOverlaysManager() {
        return sheetOverlaysManager;
    }
    @Autowired
    public void setSheetOverlaysManager(SheetOverlaysManager sheetOverlaysManager) {
        this.sheetOverlaysManager = sheetOverlaysManager;
    }
    
    protected CrawlerLoggerModule loggerModule;
    public CrawlerLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(CrawlerLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }

    protected SeedModule seeds;
    public SeedModule getSeeds() {
        return this.seeds;
    }
    @Autowired
    public void setSeeds(SeedModule seeds) {
        this.seeds = seeds;
    }
    
    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    /** ordinal numbers to assign to created CrawlURIs */
    protected AtomicLong nextOrdinal = new AtomicLong(1);

    protected DecideRule scope;
    public DecideRule getScope() {
        return this.scope;
    }
    @Autowired
    public void setScope(DecideRule scope) {
        this.scope = scope;
    }

    protected FrontierPreparer preparer;
    public FrontierPreparer getFrontierPreparer() {
        return this.preparer;
    }
    @Autowired
    public void setFrontierPreparer(FrontierPreparer prep) {
        this.preparer = prep;
    }
    
    /**
     * @param cauri CrawlURI we're to get a key for.
     * @return a String token representing a queue
     */
    public String getClassKey(CrawlURI curi) {
        assert KeyedProperties.overridesActiveFrom(curi); 
        return preparer.getClassKey(curi);
    }
   
    // top-level stats
    /** total URIs queued to be visited */
    protected AtomicLong queuedUriCount = new AtomicLong(0); 

    protected AtomicLong futureUriCount = new AtomicLong(0); 

    protected AtomicLong succeededFetchCount = new AtomicLong(0);

    protected AtomicLong failedFetchCount = new AtomicLong(0);

    /** URIs that are disregarded (for example because of robot.txt rules */
    protected AtomicLong disregardedUriCount = new AtomicLong(0);
    
    /**
     * Used when bandwidth constraint are used.
     */
    protected AtomicLong totalProcessedBytes = new AtomicLong(0);

    /**
     * Crawl replay logger.
     * 
     * Currently captures Frontier/URI transitions.
     * Can be null if user chose not to run a recovery.log.
     */
    protected FrontierJournal recover = null;
    
    /**
     * @param name Name of this frontier.
     * @param description Description for this frontier.
     */
    public AbstractFrontier() {

    }

    /** 
     * lock to allow holding all worker ToeThreads from taking URIs already
     * on the outbound queue; they acquire read permission before take()ing;
     * frontier can acquire write permission to hold threads */
    protected ReentrantReadWriteLock outboundLock = 
        new ReentrantReadWriteLock(true);
    
    
    /**
     * Distinguished frontier manager thread which handles all juggling
     * of URI queues and queues/maps of queues for proper ordering/delay of
     * URI processing. 
     */
    protected Thread managerThread;
    
    /** last Frontier.State reached; used to suppress duplicate notifications */
    protected State lastReachedState = null;
    /** Frontier.state that manager thread should seek to reach */
    protected volatile State targetState = State.PAUSE;

    /**
     * Start the dedicated thread with an independent view of the frontier's
     * state. 
     */
    protected void startManagerThread() {
        managerThread = new Thread(this+".managerThread") {
            public void run() {
                AbstractFrontier.this.managementTasks();
            }
        };
        managerThread.setPriority(Thread.NORM_PRIORITY+1); 
        managerThread.start();
    }
    
    public void start() {
        if(isRunning()) {
            return; 
        }
        
        if (getRecoveryLogEnabled()) try {
            initJournal(loggerModule.getPath().getFile().getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        pause();
        startManagerThread();
    }
    
    /**
     * Main loop of frontier's managerThread. Only exits when State.FINISH 
     * is requested (perhaps automatically at URI exhaustion) and reached. 
     * 
     * General strategy is to try to fill outbound queue, then process an
     * item from inbound queue, and repeat. A HOLD (to be implemented) or 
     * PAUSE puts frontier into a stable state that won't be changed
     * asynchronously by worker thread activity. 
     */
    protected void managementTasks() {
        assert Thread.currentThread() == managerThread;
        try {
            loop: while (true) {
                try {
                    State reachedState = null; 
                    switch (targetState) {
                    case EMPTY:
                        reachedState = State.EMPTY; 
                    case RUN:
                        // enable outbound takes if previously locked
                        while(outboundLock.isWriteLockedByCurrentThread()) {
                            outboundLock.writeLock().unlock();
                        }
                        if(reachedState==null) {
                            reachedState = State.RUN; 
                        }
                        reachedState(reachedState);
                        
                        Thread.sleep(1000);
                        
                        if(isEmpty()&&targetState==State.RUN) {
                            requestState(State.EMPTY); 
                        } else if (!isEmpty()&&targetState==State.EMPTY) {
                            requestState(State.RUN); 
                        }
                        break;
                    case HOLD:
                        // TODO; for now treat same as PAUSE
                    case PAUSE:
                        // pausing
                        // prevent all outbound takes
                        outboundLock.writeLock().lock();
                        // process all inbound
                        while (targetState == State.PAUSE) {
                            if (getInProcessCount()==0) {
                                reachedState(State.PAUSE);
                            }
                            
                            Thread.sleep(1000);
                        }
                        break;
                    case FINISH:
                        logger.fine("FINISH requested, waiting for in process urls to finish");
                        // prevent all outbound takes
                        outboundLock.writeLock().lock();
                        // process all inbound
                        while (getInProcessCount()>0) {
                            Thread.sleep(1000);
                        }
                        logger.fine("0 urls in process, running final tasks");
                        finalTasks(); 
                        // TODO: more cleanup?
                        reachedState(State.FINISH);
                        break loop;
                    }
                } catch (RuntimeException e) {
                    // log, try to pause, continue
                    logger.log(Level.SEVERE,"",e);
                    if(targetState!=State.PAUSE && targetState!=State.FINISH) {
                        requestState(State.PAUSE);
                    }
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } 
        
        // try to leave in safely restartable state: 
        targetState = State.PAUSE;
        while(outboundLock.isWriteLockedByCurrentThread()) {
            outboundLock.writeLock().unlock();
        }
        //TODO: ensure all other structures are cleanly reset on restart
        
        logger.log(Level.FINE,"ending frontier mgr thread");
    }


    /**
     * Perform any tasks necessary before entering 
     * FINISH frontier state/FINISHED crawl state
     */
    protected void finalTasks() {
        // by default; nothing
    }

    /**
     * The given state has been reached; if it is a new state, generate
     * a notification to the CrawlController. 
     * 
     * TODO: evaluate making this a generic notification others can sign up for
     */
    protected void reachedState(State justReached) {
        if(justReached != lastReachedState) {
            logger.fine("reached Frontier.State " + this.lastReachedState + ", notifying listeners");
            controller.noteFrontierState(justReached);
            lastReachedState = justReached;
        }
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#next()
     */
    public CrawlURI next() throws InterruptedException {
        CrawlURI crawlable = null;
        while(crawlable==null) {
            outboundLock.readLock().lockInterruptibly();
            // try filling outbound until we get something to work on
            crawlable = findEligibleURI();
            outboundLock.readLock().unlock();
        }
        return crawlable;
    }

    /**
     * Find a CrawlURI eligible to be put on the outbound queue for 
     * processing. If none, return null. 
     * @return the eligible URI, or null
     */
    abstract protected CrawlURI findEligibleURI();
    
    
    /**
     * Schedule the given CrawlURI regardless of its already-seen status. Only
     * to be called inside the managerThread, as by an InEvent. 
     * 
     * @param caUri CrawlURI to schedule
     */
    abstract protected void processScheduleAlways(CrawlURI caUri);
    
    /**
     * Schedule the given CrawlURI if not already-seen. Only
     * to be called inside the managerThread, as by an InEvent. 
     * 
     * @param caUri CrawlURI to schedule
     */
    abstract protected void processScheduleIfUnique(CrawlURI caUri);
    
    /**
     * Handle the given CrawlURI as having finished a worker ToeThread 
     * processing attempt. May result in the URI being rescheduled or
     * logged as successful or failed. Only to be called inside the 
     * managerThread, as by an InEvent. 
     * 
     * @param caUri CrawlURI to finish
     */
    abstract protected void processFinish(CrawlURI caUri);
    
    /**
     * The number of CrawlURIs 'in process' (passed to the outbound
     * queue and not yet finished by returning through the inbound
     * queue.)
     * 
     * @return number of in-process CrawlURIs
     */
    abstract protected int getInProcessCount();
    
    
    /**
     * Maximum amount of time to wait for an inbound update event before 
     * giving up and rechecking on the ability to further fill the outbound
     * queue. If any queues are waiting out politeness/retry delays ('snoozed'),
     * the maximum wait should be no longer than the shortest sch delay. 
     * @return maximum time to wait, in milliseconds
     */
    abstract protected long getMaxInWait();
    
    /**
     * Arrange for the given CrawlURI to be visited, if it is not
     * already scheduled/completed.
     * 
     * This implementation defers uniqueness-testing into the frontier 
     * managerThread with a ScheduleIfUnique InEvent; this may cause 
     * unnecessary contention/single-threading. WorkQueueFrontier currently
     * overrides as an experiment in decreasing contention. TODO: settle on
     * one approach. 
     *
     * @see org.archive.crawler.framework.Frontier#schedule(org.archive.modules.CrawlURI)
     */
    public void schedule(CrawlURI curi) {
        sheetOverlaysManager.applyOverlaysTo(curi);
        if(curi.getClassKey()==null) {
            // remedial processing
            try {
                KeyedProperties.loadOverridesFrom(curi);
                preparer.prepare(curi);
                processScheduleIfUnique(curi);
            } finally {
                KeyedProperties.clearOverridesFrom(curi); 
            }
        }
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
    public void receive(CrawlURI curi) {
        sheetOverlaysManager.applyOverlaysTo(curi);
        // prefer doing asap if already in manager thread
        try {
            KeyedProperties.loadOverridesFrom(curi);
            processScheduleAlways(curi);
        } finally {
            KeyedProperties.clearOverridesFrom(curi); 
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
     * @see org.archive.crawler.framework.Frontier#finished(org.archive.modules.CrawlURI)
     */
    public void finished(CrawlURI curi) {
        try {
            KeyedProperties.loadOverridesFrom(curi);
            processFinish(curi);
        } finally {
            KeyedProperties.clearOverridesFrom(curi); 
        }
    }
    
    private void initJournal(String logsDisk) throws IOException {
        if (logsDisk != null) {
            String logsPath = logsDisk + File.separatorChar;
            this.recover = new FrontierJournal(logsPath,
                    FrontierJournal.LOGNAME_RECOVER);
        }
    }

    public void run() {
        requestState(State.RUN);
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#requestState(org.archive.crawler.framework.Frontier.State)
     */
    public void requestState(State target) {
        targetState = target;
    }
    
    public void pause() {
        requestState(State.PAUSE);
    }

    public void unpause() {
        requestState(State.RUN);
    }

    public void terminate() {
        requestState(State.FINISH);
    }
    
    /**
     * Report CrawlURI to each of the three 'substats' accumulators
     * (group/queue, server, host) for a given stage.
     * 
     * @param curi
     * @param stage
     */
    protected void tally(CrawlURI curi, Stage stage) {
        // Tally per-server, per-host, per-frontier-class running totals
        CrawlServer server = getServerCache().getServerFor(curi.getUURI());
        if (server != null) {
            server.getSubstats().tally(curi, stage);
            server.makeDirty(); 
        }
        try {
            CrawlHost host = getServerCache().getHostFor(curi.getUURI());
            if (host != null) {
                host.getSubstats().tally(curi, stage);
                host.makeDirty();
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "unable to tally host stats for " + curi, e);
        }
        FrontierGroup group = getGroup(curi);
        group.tally(curi, stage);
        group.makeDirty(); 
    }

    protected void doJournalFinishedSuccess(CrawlURI c) {
        tally(c,Stage.SUCCEEDED);
        if (this.recover != null) {
            this.recover.finishedSuccess(c);
        }
    }

    protected void doJournalAdded(CrawlURI c) {
        tally(c,Stage.SCHEDULED);
        if (this.recover != null) {
            this.recover.added(c);
        }
    }
    
    protected void doJournalRelocated(CrawlURI c) {
        tally(c,Stage.RELOCATED);
        if (this.recover != null) {
            // TODO: log dequeue from original location somehow
            // this.recover.relocated(c);
        }
    }

    protected void doJournalReenqueued(CrawlURI c) {
        tally(c,Stage.RETRIED);
        if (this.recover != null) {
            this.recover.reenqueued(c);
        }
    }

    protected void doJournalFinishedFailure(CrawlURI c) {
        tally(c,Stage.FAILED);
        if (this.recover != null) {
            this.recover.finishedFailure(c);
        }
    }

    protected void doJournalDisregarded(CrawlURI c) {
        tally(c, Stage.DISREGARDED);
        if (this.recover != null) {
            this.recover.finishedDisregard(c);
        }
    }
    
    protected void doJournalEmitted(CrawlURI c) {
        if (this.recover != null) {
            this.recover.emitted(c);
        }
    }

    /**
     * Frontier is empty only if all queues are empty and no URIs are in-process
     * 
     * @return True if queues are empty.
     */
    public boolean isEmpty() {
        return queuedUriCount.get() == 0;
    }

    /**
     * Increment the running count of queued URIs. 
     */
    protected void incrementQueuedUriCount() {
        queuedUriCount.incrementAndGet();
    }

    /**
     * Increment the running count of queued URIs.
     * 
     * @param increment
     *            amount to increment the queued count
     */
    protected void incrementQueuedUriCount(long increment) {
        queuedUriCount.addAndGet(increment);
    }

    /**
     * Note that a number of queued Uris have been deleted.
     * 
     * @param numberOfDeletes
     */
    protected void decrementQueuedCount(long numberOfDeletes) {
        queuedUriCount.addAndGet(-numberOfDeletes);
    }

    /**
     * (non-Javadoc)
     * 
     * @see org.archive.crawler.framework.Frontier#queuedUriCount()
     */
    public long queuedUriCount() {
        return queuedUriCount.get();
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#futureUriCount()
     */
    public long futureUriCount() {
        return futureUriCount.get(); 
    }
    
    /**
     * (non-Javadoc)
     * 
     * @see org.archive.crawler.framework.Frontier#finishedUriCount()
     */
    public long finishedUriCount() {
        return succeededFetchCount.get() + failedFetchCount.get() + disregardedUriCount.get();
    }

    /**
     * Increment the running count of successfully fetched URIs. 
     */
    protected void incrementSucceededFetchCount() {
        succeededFetchCount.incrementAndGet();
    }

    /**
     * (non-Javadoc)
     * 
     * @see org.archive.crawler.framework.Frontier#succeededFetchCount()
     */
    public long succeededFetchCount() {
        return succeededFetchCount.get();
    }

    /**
     * Increment the running count of failed URIs.
     */
    protected void incrementFailedFetchCount() {
        failedFetchCount.incrementAndGet();
    }

    /**
     * (non-Javadoc)
     * 
     * @see org.archive.crawler.framework.Frontier#failedFetchCount()
     */
    public long failedFetchCount() {
        return failedFetchCount.get();
    }

    /**
     * Increment the running count of disregarded URIs.
     */
    protected void incrementDisregardedUriCount() {
        disregardedUriCount.incrementAndGet();
    }

    public long disregardedUriCount() {
        return disregardedUriCount.get();
    }

    /**
     * When notified of a seed via the SeedListener interface, 
     * schedule it.
     * 
     * @see org.archive.modules.seeds.SeedListener#addedSeed(org.archive.modules.CrawlURI)
     */
    public void addedSeed(CrawlURI puri) {
        schedule(puri);
    }
    
    /** 
     * Do nothing with non-seed lines
     * @see org.archive.modules.seeds.SeedListener#nonseedLine(java.lang.String)
     */
    public boolean nonseedLine(String line) {
        return false; 
    }
    
    public void concludedSeedBatch() {
        // do nothing;
    }

    protected void prepForFrontier(CrawlURI curi) {
        if (curi.getOrdinal() == 0) {
            curi.setOrdinal(nextOrdinal.getAndIncrement());
        }
    }

    /**
     * Perform fixups on a CrawlURI about to be returned via next().
     * 
     * @param curi
     *            CrawlURI about to be returned by next()
     * @param q
     *            the queue from which the CrawlURI came
     */
    protected void noteAboutToEmit(CrawlURI curi, WorkQueue q) {
        curi.setHolder(q);
        // if (curi.getServer() == null) {
        //    // TODO: perhaps short-circuit the emit here,
        //    // because URI will be rejected as unfetchable
        // }
        doJournalEmitted(curi);
    }

    /**
     * Return a suitable value to wait before retrying the given URI.
     * 
     * @param curi
     *            CrawlURI to be retried
     * @return millisecond delay before retry
     */
    protected long retryDelayFor(CrawlURI curi) {
        int status = curi.getFetchStatus();
        return (status == S_CONNECT_FAILED || status == S_CONNECT_LOST ||
                status == S_DOMAIN_UNRESOLVABLE)? getRetryDelaySeconds() : 0;
                // no delay for most
    }

    /**
     * Take note of any processor-local errors that have been entered into the
     * CrawlURI.
     * 
     * @param curi
     *  
     */
    protected void logNonfatalErrors(CrawlURI curi) {
        if (curi.containsDataKey(A_NONFATAL_ERRORS)) {
            Collection<Throwable> x = curi.getNonFatalFailures();
            Logger le = loggerModule.getNonfatalErrors();
            for (Throwable e : x) {
                le.log(Level.WARNING, curi.toString(), 
                        new Object[] { curi, e });
            }
            // once logged, discard
            curi.getData().remove(A_NONFATAL_ERRORS);
        }
    }

    protected boolean overMaxRetries(CrawlURI curi) {
        // never retry more than the max number of times
        if (curi.getFetchAttempts() >= getMaxRetries()) {
            return true;
        }
        return false;
    }
    
    //  show import progress every this many lines
    private final static int PROGRESS_INTERVAL = 1000000; 

    /**
     * Import URIs from the given file (in recover-log-like format, with
     * a 3-character 'type' tag preceding a URI with optional hops/via).
     * 
     * If 'includeOnly' is true, the URIs will only be imported into 
     * the frontier's alreadyIncluded structure, without being queued.
     * 
     * Only imports URIs if their first tag field matches the acceptTags 
     * pattern.
     * 
     * @param source File recovery log file to use (may be .gz compressed)
     * @param applyScope whether to apply crawl scope to URIs
     * @param includeOnly whether to only add to included filter, not schedule
     * @param forceFetch whether to force fetching, even if already seen 
     * (ignored if includeOnly is set)
     * @param acceptTags String regex; only lines whose first field 
     * match will be included
     * @return number of lines in recovery log (for reference)
     * @throws IOException
     */
    public long importRecoverFormat(File source, boolean applyScope, 
            boolean includeOnly, boolean forceFetch, String acceptTags) 
    throws IOException {
        DecideRule scope = (applyScope) ? getScope() : null;
        FrontierJournal newJournal = getFrontierJournal();
        Matcher m = Pattern.compile(acceptTags).matcher(""); 
        BufferedReader br = ArchiveUtils.getBufferedReader(source);
        String read;
        int lineCount = 0; 
        try {
            while ((read = br.readLine())!=null) {
                lineCount++;
                if(read.length()<4) {
                    continue;
                }
                String lineType = read.substring(0, 3);
                m.reset(lineType);
                if(m.matches()) {
                    try {
                        String uriHopsViaString = read.substring(3).trim();
                        CrawlURI curi = CrawlURI.fromHopsViaString(uriHopsViaString);
                        if(scope!=null) {
                            sheetOverlaysManager.applyOverlaysTo(curi);
                            try {
                                KeyedProperties.loadOverridesFrom(curi);
                                if(!scope.accepts(curi)) {
                                    // skip out-of-scope URIs if so configured
                                    continue;
                                }
                            } finally {
                                KeyedProperties.clearOverridesFrom(curi); 
                            }
                        }
                        if(includeOnly) {
                            considerIncluded(curi);
                            newJournal.included(curi);
                        } else {
                            curi.setForceFetch(forceFetch);
                            schedule(curi);
                        }
                    } catch (URIException e) {
                        logger.log(Level.WARNING,"Problem line: "+read, e);
                    }
                }
                if((lineCount%PROGRESS_INTERVAL)==0) {
                    // every 1 million lines, print progress
                    logger.info(
                            "at line " + lineCount + (includeOnly?" (include-only)":"")
                            + " alreadyIncluded count = " +
                            discoveredUriCount());
                }
            }
        } catch (EOFException e) {
            // expected in some uncleanly-closed recovery logs; ignore
        } finally {
            br.close();
        }
        return lineCount;
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#importURIs(java.util.Map)
     */
    public void importURIs(String jsonParams)
            throws IOException {
        JSONObject params;
        try {
            params = new JSONObject(jsonParams);
        } catch (JSONException e) {
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
        if("recoveryLog".equals(params.optString("format"))) {
            FrontierJournal.importRecoverLog(params, this);
            return;
        }
        // otherwise, do a 'simple' import
        importURIsSimple(params);
    }
    
    /**
     * Import URIs from either a simple (one URI per line) or crawl.log
     * format.
     * 
     * @param params JSONObject of options to control import
     * @see org.archive.crawler.framework.Frontier#importURIs(java.util.Map)
     */
    protected void importURIsSimple(JSONObject params) {
        // Figure the regex to use parsing each line of input stream.
        String extractor;
        String output;
        String format = params.optString("format");
        if("crawlLog".equals(format)) {
            // Skip first 3 fields
            extractor = "\\S+\\s+\\S+\\s+\\S+\\s+(\\S+\\s+\\S+\\s+\\S+\\s+).*";
            output = "$1";
        } else {
            extractor =
                RegexLineIterator.NONWHITESPACE_ENTRY_TRAILING_COMMENT;
            output = RegexLineIterator.ENTRY;
        }
        
        // Read the input stream.
        BufferedReader br = null;
        String path = params.optString("path");
        boolean forceRevisit = !params.isNull("forceRevisit");
        boolean asSeeds = !params.isNull("asSeeds");
        boolean scopeScheduleds = !params.isNull("scopeScheduleds");
        DecideRule scope = scopeScheduleds ? getScope() : null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(path)));
            Iterator<String> iter = new RegexLineIterator(new LineReadingIterator(br),
                RegexLineIterator.COMMENT_LINE, extractor, output);
            while(iter.hasNext()) {
                try {
                    
                    CrawlURI curi = CrawlURI.fromHopsViaString(((String)iter.next()));
                    curi.setForceFetch(forceRevisit);
                    if (asSeeds) {
                        curi.setSeed(asSeeds);
                        if (curi.getVia() == null || curi.getVia().length() <= 0) {
                            // Danger of double-add of seeds because of this code here.
                            // Only call addSeed if no via.  If a via, the schedule will
                            // take care of updating scope.
                            getSeeds().addSeed(curi);
                        }
                    }
                    if(scope!=null) {
                        //TODO:SPRINGY
//                        curi.setStateProvider(controller.getSheetManager());
                        if(!scope.accepts(curi)) {
                            continue;
                        }
                    }
                        
                    this.controller.getFrontier().schedule(curi);
                    
                } catch (URIException e) {
                    e.printStackTrace();
                }
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Log to the main crawl.log
     * 
     * @param curi
     */
    protected void log(CrawlURI curi) {
        curi.aboutToLog();
        Object array[] = {curi};
        this.loggerModule.getUriProcessing().log(Level.INFO,
                curi.getUURI().toString(), array);
    }

    protected boolean isDisregarded(CrawlURI curi) {
        switch (curi.getFetchStatus()) {
        case S_ROBOTS_PRECLUDED: // they don't want us to have it
        case S_BLOCKED_BY_CUSTOM_PROCESSOR:
        case S_OUT_OF_SCOPE: // filtered out by scope
        case S_BLOCKED_BY_USER: // filtered out by user
        case S_TOO_MANY_EMBED_HOPS: // too far from last true link
        case S_TOO_MANY_LINK_HOPS: // too far from seeds
        case S_DELETED_BY_USER: // user deleted
            return true;
        default:
            return false;
        }
    }

    /**
     * Checks if a recently processed CrawlURI that did not finish successfully
     * needs to be reenqueued (and thus possibly, processed again after some 
     * time elapses)
     * 
     * @param curi
     *            The CrawlURI to check
     * @return True if we need to retry.
     */
    protected boolean needsReenqueuing(CrawlURI curi) {
        if (overMaxRetries(curi)) {
            return false;
        }

        switch (curi.getFetchStatus()) {
        case HttpStatus.SC_UNAUTHORIZED:
            // We can get here though usually a positive status code is
            // a success. We get here if there is rfc2617 credential data
            // loaded and we're supposed to go around again. See if any
            // rfc2617 credential present and if there, assume it got
            // loaded in FetchHTTP on expectation that we're to go around
            // again. If no rfc2617 loaded, we should not be here.
            boolean loaded = curi.hasRfc2617Credential();
            if (!loaded && logger.isLoggable(Level.FINE)) {
                logger.fine("Have 401 but no creds loaded " + curi);
            }
            return loaded;
        case S_DEFERRED:
        case S_CONNECT_FAILED:
        case S_CONNECT_LOST:
        case S_DOMAIN_UNRESOLVABLE:
            // these are all worth a retry
            // TODO: consider if any others (S_TIMEOUT in some cases?) deserve
            // retry
            return true;
        case S_UNATTEMPTED:
            if(curi.includesRetireDirective()) {
                return true;
            } // otherwise, fall-through: no status is an error without queue-directive
        default:
            return false;
        }
    }
   
    /**
     * @return RecoveryJournal instance.  May be null.
     */
    public FrontierJournal getFrontierJournal() {
        return this.recover;
    }

    public void crawlEnded(String sExitMessage) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info("Closing with " + Long.toString(queuedUriCount()) +
                " urls still in queue.");
        }
    }

    //
    // Reporter implementation
    // 
    public String shortReportLine() {
        return ReportUtils.shortReportLine(this);
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof CrawlStateEvent) {
            CrawlStateEvent event1 = (CrawlStateEvent)event;
            switch(event1.getState()) {
                case FINISHED:
                    this.crawlEnded(event1.getMessage());
                    break;
                default:
                    // ignore;
            }
        }
    }
    
    /** lock allowing steps of outside processing that need to complete 
     * all-or-nothing to signal their in-progress status */
    protected ReentrantReadWriteLock dispositionInProgressLock = 
        new ReentrantReadWriteLock(true);
    /** remembers a disposition-in-progress, so that extra endDisposition()
     *  calls are harmless */
    protected ThreadLocal<CrawlURI> dispositionPending = new ThreadLocal<CrawlURI>(); 
    
    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#beginDisposition(org.archive.modules.CrawlURI)
     */
    @Override
    public void beginDisposition(CrawlURI curi) {
        dispositionPending.set(curi); 
        dispositionInProgressLock.readLock().lock();
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#endDisposition()
     */
    @Override
    public void endDisposition() {
        // avoid a mismatched unlock; allows callers to be less complicated, 
        // calling endDisposition 'just in case' a begin happened
        if(dispositionPending.get()!=null) {
            dispositionInProgressLock.readLock().unlock();
            dispositionPending.set(null); 
        }
    }
} //EOC
