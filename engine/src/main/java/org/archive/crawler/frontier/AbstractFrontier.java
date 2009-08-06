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

import static org.archive.modules.CoreAttributeConstants.A_FETCH_COMPLETED_TIME;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringUtils;
import org.archive.checkpointing.CheckpointRecovery;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Frontier;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.crawler.spring.SheetOverlaysManager;
import org.archive.modules.CrawlURI;
import org.archive.modules.ModuleAttributeConstants;
import org.archive.modules.ProcessorURI;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.canonicalize.UriCanonicalizationPolicy;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.extractor.ExtractorParameters;
import org.archive.modules.fetcher.UserAgentProvider;
import org.archive.modules.fetcher.FetchStats.Stage;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.RobotsExclusionPolicy;
import org.archive.modules.net.ServerCache;
import org.archive.modules.net.ServerCacheUtil;
import org.archive.modules.seeds.SeedListener;
import org.archive.modules.seeds.SeedModule;
import org.archive.net.UURI;
import org.archive.spring.ConfigPath;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;
import org.archive.util.ArchiveUtils;
import org.archive.util.iterator.LineReadingIterator;
import org.archive.util.iterator.RegexpLineIterator;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;

/**
 * Shared facilities for Frontier implementations.
 * 
 * @author gojomo
 */
public abstract class AbstractFrontier 
    implements Frontier, 
               Serializable, 
               SeedListener, 
               HasKeyedProperties,
               ExtractorParameters {
    private static final long serialVersionUID = 555881755284996860L;
    private static final Logger logger = Logger
            .getLogger(AbstractFrontier.class.getName());

    KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    
    public boolean isRunning() {
        return managerThread!=null && managerThread.isAlive();
    }
    
    public void stop() {
        terminate();
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
    
    protected ConfigPath recoveryDir = new ConfigPath("recovery subdirectory","logs");
    public ConfigPath getRecoveryDir() {
        return recoveryDir;
    }
    public void setRecoveryDir(ConfigPath recoveryDir) {
        this.recoveryDir = recoveryDir;
    }

    /**
     * How many multiples of last fetch elapsed time to wait before recontacting
     * same server.
     */
    {
        setDelayFactor(5.0f);
    }
    public float getDelayFactor() {
        return (Float) kp.get("delayFactor");
    }
    public void setDelayFactor(float factor) {
        kp.put("delayFactor",factor);
    }

    /**
     * always wait this long after one completion before recontacting same
     * server, regardless of multiple
     */
    {
        setMinDelayMs(3000);
    }
    public int getMinDelayMs() {
        return (Integer) kp.get("minDelayMs");
    }
    public void setMinDelayMs(int minDelay) {
        kp.put("minDelayMs",minDelay);
    }
    
    /**
     * Whether to respect a 'Crawl-Delay' (in seconds) given in a site's
     * robots.txt
     */
    {
        setRespectCrawlDelayUpToSeconds(300);
    }
    public int getRespectCrawlDelayUpToSeconds() {
        return (Integer) kp.get("respectCrawlDelayUpToSeconds");
    }
    public void setRespectCrawlDelayUpToSeconds(int respect) {
        kp.put("respectCrawlDelayUpToSeconds",respect);
    }

    /** never wait more than this long, regardless of multiple */
    {
        setMaxDelayMs(30000);
    }
    public int getMaxDelayMs() {
        return (Integer) kp.get("maxDelayMs");
    }
    public void setMaxDelayMs(int maxDelay) {
        kp.put("maxDelayMs",maxDelay);
    }    

    /** number of hops of embeds (ERX) to bump to front of host queue */
    {
        setPreferenceEmbedHops(1);
    }
    public int getPreferenceEmbedHops() {
        return (Integer) kp.get("preferenceEmbedHops");
    }
    public void setPreferenceEmbedHops(int pref) {
        kp.put("preferenceEmbedHops",pref);
    }

    /** maximum per-host bandwidth usage */
    {
        setMaxPerHostBandwidthUsageKbSec(0);
    }
    public int getMaxPerHostBandwidthUsageKbSec() {
        return (Integer) kp.get("maxPerHostBandwidthUsageKbSec");
    }
    public void setMaxPerHostBandwidthUsageKbSec(int max) {
        kp.put("maxPerHostBandwidthUsageKbSec",max);
    }

    /** maximum overall bandwidth usage */
    {
        setTotalBandwidthUsageKbSec(0);
    }
    public int getTotalBandwidthUsageKbSec() {
        return (Integer) kp.get("totalBandwidthUsageKbSec");
    }
    public void setTotalBandwidthUsageKbSec(int total) {
        kp.put("totalBandwidthUsageKbSec",total);
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

    /** size of the 'outbound' mediation queue between manager thread 
     * and toethreads */
    int outboundQueueCapacity = 50; 
    public int getOutboundQueueCapacity() {
        return this.outboundQueueCapacity;
    }
    public void setOutboundQueueCapacity(int capacity) {
        this.outboundQueueCapacity = capacity; 
    }
    
    /** size of the inbound queue as multiple of the outbound queue */
    int inboundQueueMultiple = 3;
    public int getInboundQueueMultiple() {
        return this.inboundQueueMultiple;
    }
    public void setInboundQueueMultiple(int multiple) {
        this.inboundQueueMultiple = multiple;
    }
    

    /** queue assignment to force onto CrawlURIs; intended to be overridden */
    {
        setForceQueueAssignment("");
    }
    public String getForceQueueAssignment() {
        return (String) kp.get("forceQueueAssignment");
    }
    public void setForceQueueAssignment(String forceQueueAssignment) {
        kp.put("forceQueueAssignment",forceQueueAssignment);
    }

    // word chars, dash, period, comma, colon
//    protected final static String ACCEPTABLE_FORCE_QUEUE = "[-\\w\\.,:]*";

    /**
     * Whether to tag seeds with their own URI as a heritable 'source' String,
     * which will be carried-forward to all URIs discovered on paths originating
     * from that seed. When present, such source tags appear in the
     * second-to-last crawl.log field.
     */
    {
        setSourceTagSeeds(false);
    }
    public boolean getSourceTagSeeds() {
        return (Boolean) kp.get("sourceTagSeeds");
    }
    public void setSourceTagSeeds(boolean sourceTagSeeds) {
        kp.put("sourceTagSeeds",sourceTagSeeds);
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

    // top-level stats
    /** total URIs queued to be visited */
    protected AtomicLong queuedUriCount = new AtomicLong(0); 

    protected AtomicLong succeededFetchCount = new AtomicLong(0);

    protected AtomicLong failedFetchCount = new AtomicLong(0);

    /** URIs that are disregarded (for example because of robot.txt rules */
    protected AtomicLong disregardedUriCount = new AtomicLong(0);
    
    /**
     * Used when bandwidth constraint are used.
     */
    protected long totalProcessedBytes = 0;

    protected long processedBytesAfterLastEmittedURI = 0;
    
    protected int lastMaxBandwidthKB = 0;

    /**
     * Crawl replay logger.
     * 
     * Currently captures Frontier/URI transitions.
     * Can be null if user chose not to run a recovery.log.
     */
    private transient FrontierJournal recover = null;
    
    /** file collecting report of ignored seed-file entries (if any) */
    public static final String IGNORED_SEEDS_FILENAME = "seeds.ignored";

    /**
     * Ordered list of url canonicalization rules.  Rules are applied in the 
     * order listed from top to bottom.
     */
    
    @SuppressWarnings("unchecked")
    public UriCanonicalizationPolicy getUriCanonicalizationPolicy() {
        return (UriCanonicalizationPolicy) kp.get("uriCanonicalizationRules");
    }
    @Autowired
    public void setCanonicalizationPolicy(UriCanonicalizationPolicy policy) {
        kp.put("uriCanonicalizationRules",policy);
    }

    /**
     * Defines how to assign URIs to queues. Can assign by host, by ip, 
     * by SURT-ordered authority, by SURT-ordered authority truncated to 
     * a topmost-assignable domain, and into one of a fixed set of buckets 
     * (1k).
     */
    {
        setQueueAssignmentPolicy(new SurtAuthorityQueueAssignmentPolicy());
    }
    public QueueAssignmentPolicy getQueueAssignmentPolicy() {
        return (QueueAssignmentPolicy) kp.get("queueAssignmentPolicy");
    }
    @Autowired(required=false)
    public void setQueueAssignmentPolicy(QueueAssignmentPolicy policy) {
        kp.put("queueAssignmentPolicy",policy);
    }

    /**
     * Auto-discovered module providing configured (or overridden)
     * User-Agent value; now necessary in frontier because User-Agent
     * may affect politeness delays via robots.txt Crawl-Delay. 
     */
    public UserAgentProvider getUserAgentProvider() {
        return (UserAgentProvider) kp.get("userAgentProvider");
    }
    @Autowired
    public void setUserAgentProvider(UserAgentProvider provider) {
        kp.put("userAgentProvider",provider);
    }

    
    /**
     * @param name Name of this frontier.
     * @param description Description for this frontier.
     */
    public AbstractFrontier() {

    }

    /** reusable no-op inbound event, to force reeval of state/eligible URIs */
    transient protected InEvent NOOP = new InEvent() { public void process() {} };
    
    /** inbound updates: URIs to be scheduled, finished; requested state changes */
    transient protected ArrayBlockingQueue<InEvent> inbound;
    /** outbound URIs */ 
    transient protected ArrayBlockingQueue<CrawlURI> outbound;
    
    /** Capacity of the inbound queue. */
    private int inboundCapacity;
    
    /** Capacity of the outbound queue. */
    private int outboundCapacity;
    
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
    transient Thread managerThread;
    
    /** last Frontier.State reached; used to suppress duplicate notifications */
    State lastReachedState = null;
    /** Frontier.state that manager thread should seek to reach */
    State targetState = State.PAUSE;

    /**
     * Start the manager thread.  Subclasses should invoke this in a custom
     * readObject() method so that the manager thread begins after checkpoint
     * recovery.
     */
    protected void startManagerThread() {
        managerThread = new Thread(this+".managerThread") {
            public void run() {
                AbstractFrontier.this.managementTasks();
            }
        };
        managerThread.start();
    }
    
    public void start() {
        if(isRunning()) {
            return; 
        }
        seeds.addSeedListener(this);
        
        if (getRecoveryLogEnabled()) try {
            initJournal(loggerModule.getPath().getFile().getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        
        this.outboundCapacity = getOutboundQueueCapacity();
        this.inboundCapacity = outboundCapacity * 
            getInboundQueueMultiple();
        outbound = new ArrayBlockingQueue<CrawlURI>(outboundCapacity, true);
        inbound = new ArrayBlockingQueue<InEvent>(inboundCapacity, true);
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
                    switch (targetState) {
                    case RUN:
                        // enable outbound takes if previously locked
                        while(outboundLock.isWriteLockedByCurrentThread()) {
                            outboundLock.writeLock().unlock();
                        }
                        reachedState(State.RUN);
                        // fill to-do 'on-deck' queue
                        fillOutbound();
                        // process discovered and finished URIs
                        drainInbound();
                        if(isEmpty()) {
                            // pause when frontier exhausted; controller will
                            // determine if this means to finish or not
                            targetState = State.PAUSE;
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
                            if (outbound.size() == getInProcessCount()) {
                                // if all 'in-process' URIs are actually 
                                // waiting in outbound, we are at PAUSE
                                reachedState(State.PAUSE);
                            }
                            // continue to process discovered and finished URIs
                            inbound.take().process();
                        }
                        break;
                    case FINISH:
                        // prevent all outbound takes
                        outboundLock.writeLock().lock();
                        // process all inbound
                        while (outbound.size() != getInProcessCount()) {
                            // continue to process discovered and finished URIs
                            inbound.take().process();
                        }
                        // TODO: more cleanup?
                        reachedState(State.FINISH);
                        break loop;
                    }
                } catch (RuntimeException e) {
                    // log, try to pause, continue
                    logger.log(Level.SEVERE,"",e);
                    if(targetState!=State.PAUSE) {
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
     * Fill the outbound queue with eligible CrawlURIs, to capacity
     * or as much as possible. 
     * 
     * @throws InterruptedException
     */
    protected void fillOutbound() throws InterruptedException {
        while (outbound.remainingCapacity() > 0) {
            CrawlURI crawlable = findEligibleURI();
            if (crawlable != null) {
                outbound.put(crawlable);
            } else {
                break;
            }
        }
    }
    
    /**
     * Drain the inbound queue of update events, or at the very least
     * wait until some additional delayed-queue URI becomes available. 
     * 
     * @throws InterruptedException
     */
    protected void drainInbound() throws InterruptedException {
        int batch = inbound.size();
        for(int i = 0; i < batch; i++) {
            inbound.take().process();
        }
        if(batch==0) {
            // always do at least one timed try
            InEvent toProcess = inbound.poll(getMaxInWait(),
                    TimeUnit.MILLISECONDS);
            if (toProcess != null) {
                toProcess.process();
            }
        }
    }

    /**
     * The given state has been reached; if it is a new state, generate
     * a notification to the CrawlController. 
     * 
     * TODO: evaluate making this a generic notification others can sign up for
     */
    protected void reachedState(State justReached) {
        if(justReached != lastReachedState) {
            controller.noteFrontierState(justReached);
            lastReachedState = justReached;
        }
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Frontier#next()
     */
    public CrawlURI next() throws InterruptedException {
        // perhaps hold without taking ready outbound items
        outboundLock.readLock().lockInterruptibly();
        outboundLock.readLock().unlock();
        
        
        CrawlURI retval = outbound.take();
//      // TODO: consider if following necessary for maintaining throughput
//        if(outbound.size()<=1) {
//            doOrEnqueue(NOOP);
//        }
        return retval;
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
     * @see org.archive.crawler.framework.Frontier#schedule(org.archive.modules.CrawlURI)
     */
    public void schedule(CrawlURI curi) {
        applyOverridesTo(curi);
        enqueueOrDo(new ScheduleIfUnique(curi));
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
        applyOverridesTo(curi);
        // prefer doing asap if already in manager thread
        doOrEnqueue(new ScheduleAlways(curi));
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
        enqueueOrDo(new Finish(curi));
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
        enqueueOrDo(new SetTargetState(target));
    }
    
    /**
     * Actually set a new target Frontier.State. Should only be called in
     * managerThread, as by an InEvent. 
     */
    protected void processSetTargetState(State target) {
        assert Thread.currentThread() == managerThread;
        targetState = target;
    }
    
    public void pause() {
        requestState(State.PAUSE);
    }

    public void unpause() {
        requestState(State.RUN);
    }


    synchronized public void terminate() {
        requestState(State.FINISH);
        // TODO: move this recover-cleanup to manager thread?
        if (this.recover != null) {
            this.recover.close();
            this.recover = null;
        }
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
        CrawlServer server = ServerCacheUtil.getServerFor(
                getServerCache(),curi.getUURI());
        if (server != null) {
            server.getSubstats().tally(curi, stage);
        }
        CrawlHost host = ServerCacheUtil.getHostFor(
                getServerCache(),curi.getUURI());
        if (host != null) {
            host.getSubstats().tally(curi, stage);
        }
        FrontierGroup group = getGroup(curi);
        group.tally(curi, stage);
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

    protected void doJournalRescheduled(CrawlURI c) {
        tally(c,Stage.RETRIED);
        if (this.recover != null) {
            this.recover.rescheduled(c);
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
     * Increment the running count of queued URIs. Synchronized because
     * operations on longs are not atomic.
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

    /** @deprecated misnomer; use StatisticsTracker figures instead */
    public long totalBytesWritten() {
        return totalProcessedBytes;
    }

    /**
     * Load up the seeds.
     * 
     * This method is called on initialize and inside in the crawlcontroller
     * when it wants to force reloading of configuration.
     */
    public void loadSeeds() {
        logger.info("beginning");
        // Get the seeds to refresh.
        Writer ignoredWriter = new StringWriter();
        Iterator<UURI> iter = getSeeds().seedsIterator(ignoredWriter);
        int count = 0; 
        while (iter.hasNext()) {
            UURI u = (UURI)iter.next();
            CrawlURI caUri = new CrawlURI(u);
            caUri.setSeed(true);
            caUri.setSchedulingDirective(SchedulingConstants.MEDIUM);
            if (getSourceTagSeeds()) {
                caUri.setSourceTag(caUri.toString());
            }
            schedule(caUri);
            count++;
            if(count%1000==0) {
                logger.info(count+" seeds");
            }
        }
        // save ignored items (if any) where they can be consulted later
        saveIgnoredItems(ignoredWriter.toString(), getRecoveryDir().getFile());
        logger.info("finished");        
    }

    
    public void seedsRefreshed() {
        loadSeeds();
    }
    
    public void addedSeed(ProcessorURI puri) {
        // TODO: schedule?
    }
    
    /**
     * Dump ignored seed items (if any) to disk; delete file otherwise.
     * Static to allow non-derived sibling classes (frontiers not yet 
     * subclassed here) to reuse.
     * 
     * @param ignoredItems
     * @param dir 
     */
    public static void saveIgnoredItems(String ignoredItems, File dir) {
        File ignoredFile = new File(dir, IGNORED_SEEDS_FILENAME);
        if(ignoredItems==null | ignoredItems.length()>0) {
            try {
                BufferedWriter bw = new BufferedWriter(new FileWriter(ignoredFile));
                bw.write(ignoredItems);
                bw.close();
            } catch (IOException e) {
                // TODO make an alert?
                e.printStackTrace();
            }
        } else {
            // delete any older file (if any)
            ignoredFile.delete();
        }
    }

    protected void prepForFrontier(CrawlURI curi) {
        if (curi.getOrdinal() == 0) {
            curi.setOrdinal(nextOrdinal.getAndIncrement());
        }
        curi.setClassKey(getClassKey(curi));
    }

    /**
     * Perform any special handling of the CrawlURI, such as promoting its URI
     * to seed-status, or preferencing it because it is an embed.
     * 
     * @param curi
     */
    protected void applySpecialHandling(CrawlURI curi) {
        if (curi.isSeed() && curi.getVia() != null
                && curi.flattenVia().length() > 0) {
            // The only way a seed can have a non-empty via is if it is the
            // result of a seed redirect. Add it to the seeds list.
            //
            // This is a feature. This is handling for case where a seed
            // gets immediately redirected to another page. What we're doing is
            // treating the immediate redirect target as a seed.
            getSeeds().addSeed(curi);
            // And it needs rapid scheduling.
	    if (curi.getSchedulingDirective() == SchedulingConstants.NORMAL)
                curi.setSchedulingDirective(SchedulingConstants.MEDIUM);
        }

        // optionally preferencing embeds up to MEDIUM
        int prefHops = getPreferenceEmbedHops(); 
        if (prefHops > 0) {
            int embedHops = curi.getTransHops();
            if (embedHops > 0 && embedHops <= prefHops
                    && curi.getSchedulingDirective() == SchedulingConstants.NORMAL) {
                // number of embed hops falls within the preferenced range, and
                // uri is not already MEDIUM -- so promote it
                curi.setSchedulingDirective(SchedulingConstants.MEDIUM);
            }
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
     * Update any scheduling structures with the new information in this
     * CrawlURI. Chiefly means make necessary arrangements for no other URIs at
     * the same host to be visited within the appropriate politeness window.
     * 
     * @param curi
     *            The CrawlURI
     * @return millisecond politeness delay
     */
    protected long politenessDelayFor(CrawlURI curi) {
        long durationToWait = 0;
        Map<String,Object> cdata = curi.getData();
        if (cdata.containsKey(ModuleAttributeConstants.A_FETCH_BEGAN_TIME)
                && cdata.containsKey(A_FETCH_COMPLETED_TIME)) {

            long completeTime = curi.getFetchCompletedTime();
            long durationTaken = (completeTime - curi.getFetchBeginTime());
            durationToWait = (long)(getDelayFactor() * durationTaken);

            long minDelay = getMinDelayMs();
            if (minDelay > durationToWait) {
                // wait at least the minimum
                durationToWait = minDelay;
            }

            long maxDelay = getMaxDelayMs();
            if (durationToWait > maxDelay) {
                // wait no more than the maximum
                durationToWait = maxDelay;
            }
            
            long respectThreshold = getRespectCrawlDelayUpToSeconds() * 1000;
            if (durationToWait<respectThreshold) {
                // may need to extend wait
                CrawlServer s = ServerCacheUtil.getServerFor(
                        getServerCache(),curi.getUURI());
                UserAgentProvider uap = getUserAgentProvider();
                String ua = curi.getUserAgent();
                if (ua == null) {
                    ua = uap.getUserAgent();
                }
                RobotsExclusionPolicy rep = s.getRobots();
                if (rep != null) {
                    long crawlDelay = (long)(1000 * s.getRobots().getCrawlDelay(ua));
                    crawlDelay = 
                        (crawlDelay > respectThreshold) 
                            ? respectThreshold 
                            : crawlDelay;
                    if (crawlDelay > durationToWait) {
                        // wait at least the directive crawl-delay
                        durationToWait = crawlDelay;
                    }
                }
            }
            
            long now = System.currentTimeMillis();
            int maxBandwidthKB = getMaxPerHostBandwidthUsageKbSec();
            if (maxBandwidthKB > 0) {
                // Enforce bandwidth limit
                ServerCache cache = this.getServerCache();
                CrawlHost host = ServerCacheUtil.getHostFor(cache, curi.getUURI());
                long minDurationToWait = host.getEarliestNextURIEmitTime()
                        - now;
                float maxBandwidth = maxBandwidthKB * 1.024F; // kilo factor
                long processedBytes = curi.getContentSize();
                host
                        .setEarliestNextURIEmitTime((long)(processedBytes / maxBandwidth)
                                + now);

                if (minDurationToWait > durationToWait) {
                    durationToWait = minDurationToWait;
                }
            }
        }
        return durationToWait;
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
            FrontierJournal.importRecoverLog(params, controller);
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
                RegexpLineIterator.NONWHITESPACE_ENTRY_TRAILING_COMMENT;
            output = RegexpLineIterator.ENTRY;
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
            Iterator<String> iter = new RegexpLineIterator(new LineReadingIterator(br),
                RegexpLineIterator.COMMENT_LINE, extractor, output);
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
     * Checks if a recently completed CrawlURI that did not finish successfully
     * needs to be retried (processed again after some time elapses)
     * 
     * @param curi
     *            The CrawlURI to check
     * @return True if we need to retry.
     */
    protected boolean needsRetrying(CrawlURI curi) {
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
            boolean loaded = curi.hasRfc2617CredentialAvatar();
            if (!loaded && logger.isLoggable(Level.INFO)) {
                logger.info("Have 401 but no creds loaded " + curi);
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
        default:
            return false;
        }
    }

    /**
     * Canonicalize passed uuri. Its would be sweeter if this canonicalize
     * function was encapsulated by that which it canonicalizes but because
     * settings change with context -- i.e. there may be overrides in operation
     * for a particular URI -- its not so easy; Each CrawlURI would need a
     * reference to the settings system. That's awkward to pass in.
     * 
     * @param uuri Candidate URI to canonicalize.
     * @return Canonicalized version of passed <code>uuri</code>.
     */
    protected String canonicalize(UURI uuri) {
        return getUriCanonicalizationPolicy().canonicalize(uuri.toString());
    }

    /**
     * Canonicalize passed CrawlURI. This method differs from
     * {@link #canonicalize(UURI)} in that it takes a look at
     * the CrawlURI context possibly overriding any canonicalization effect if
     * it could make us miss content. If canonicalization produces an URL that
     * was 'alreadyseen', but the entry in the 'alreadyseen' database did
     * nothing but redirect to the current URL, we won't get the current URL;
     * we'll think we've already see it. Examples would be archive.org
     * redirecting to www.archive.org or the inverse, www.netarkivet.net
     * redirecting to netarkivet.net (assuming stripWWW rule enabled).
     * <p>Note, this method under circumstance sets the forceFetch flag.
     * 
     * @param cauri CrawlURI to examine.
     * @return Canonicalized <code>cacuri</code>.
     */
    protected String canonicalize(CrawlURI cauri) {
        String canon = canonicalize(cauri.getUURI());
        if (cauri.isLocation()) {
            // If the via is not the same as where we're being redirected (i.e.
            // we're not being redirected back to the same page, AND the
            // canonicalization of the via is equal to the the current cauri, 
            // THEN forcefetch (Forcefetch so no chance of our not crawling
            // content because alreadyseen check things its seen the url before.
            // An example of an URL that redirects to itself is:
            // http://bridalelegance.com/images/buttons3/tuxedos-off.gif.
            // An example of an URL whose canonicalization equals its via's
            // canonicalization, and we want to fetch content at the
            // redirection (i.e. need to set forcefetch), is netarkivet.dk.
            if (!cauri.toString().equals(cauri.getVia().toString()) &&
                    canonicalize(cauri.getVia()).equals(canon)) {
                cauri.setForceFetch(true);
            }
        }
        return canon;
    }

    /**
     * @param cauri CrawlURI we're to get a key for.
     * @return a String token representing a queue
     */
    public String getClassKey(CrawlURI curi) {
        assert KeyedProperties.overridesActiveFrom(curi); 
        
        String queueKey = getForceQueueAssignment();
        if (StringUtils.isEmpty(queueKey)) {
            // Typical case, barring explicit override
            queueKey = getQueueAssignmentPolicy().getClassKey(curi);
        }
        return queueKey;
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
    public String singleLineReport() {
        return ArchiveUtils.singleLineReport(this);
    }

    public void reportTo(PrintWriter writer) {
        reportTo(null, writer);
    }


    private void writeObject(ObjectOutputStream out) 
    throws IOException {
        out.defaultWriteObject();
        boolean recoveryLogEnabled = getRecoveryLogEnabled();
        out.writeBoolean(recoveryLogEnabled);
        if (recoveryLogEnabled) {
            out.writeUTF(loggerModule.getPath().getFile().getAbsolutePath());
        }
    }
    
    
    private void readObject(ObjectInputStream inp) 
    throws IOException, ClassNotFoundException {
        inp.defaultReadObject();
        boolean recoveryLogEnabled = inp.readBoolean();
        if (recoveryLogEnabled) {
            String path = inp.readUTF();
            if (inp instanceof CheckpointRecovery) {
                CheckpointRecovery cr = (CheckpointRecovery)inp;
                path = cr.translatePath(path);
                new File(path).mkdirs();
            }
            initJournal(path);
        }
        targetState = State.PAUSE;
        outbound = new ArrayBlockingQueue<CrawlURI>(outboundCapacity, true);
        inbound = new ArrayBlockingQueue<InEvent>(inboundCapacity, true);
    }
    
    /**
     * Arrange for the given InEvent to be done by the managerThread, via
     * enqueueing with other events if possible, but directly if not possible
     * and this is the managerThread. 
     * @param ev InEvent to be done
     */
    protected void enqueueOrDo(InEvent ev) {
        if(!inbound.offer(ev)) {
            // if can't defer, 
            if(Thread.currentThread()==managerThread) {
                // if can't enqueue, ok to just do
                ev.process();
                return; 
            } else {
                try {
                    inbound.put(ev);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    
    /**
     * Arrange for the given InEvent to be done by the managerThread, 
     * immediately if this is the managerThread, of via enqueueing with
     * other inbound events otherwise.  
     * @param ev InEvent to be done
     */
    protected void doOrEnqueue(InEvent ev) {
        if (Thread.currentThread() == managerThread) {
            // if can't enqueue, ok to just do
            ev.process();
            return;
        } else {
            try {
                inbound.put(ev);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
    
    /**
     * An event/update for the managerThread to process from the inbound queue.
     */
    public abstract class InEvent {
        abstract public void process();
    }
    
    /**
     * A CrawlURI to be scheduled by the managerThread without regard to 
     * whether the CrawlURI was already-seen. 
     */
    public class ScheduleAlways extends InEvent {
        CrawlURI curi;
        public ScheduleAlways(CrawlURI c) {
            this.curi = c;
        }
        public void process() {
            try {
                KeyedProperties.loadOverridesFrom(curi);
                processScheduleAlways(curi);
            } finally {
                KeyedProperties.clearOverridesFrom(curi); 
            }
        } 
    }
    
    /**
     * A CrawlURI to be scheduled by the managerThread if it has not been
     * already-seen. (That is, if it passes the UriUniqFilter.)
     */
    public class ScheduleIfUnique extends InEvent {
        CrawlURI curi;
        public ScheduleIfUnique(CrawlURI c) {
            this.curi = c;
        }
        public void process() {
            try {
                KeyedProperties.loadOverridesFrom(curi);
                processScheduleIfUnique(curi);
            } finally {
                KeyedProperties.clearOverridesFrom(curi); 
            }
        }   
    }
    
    /**
     * A CrawlURI, previously issued via the outbound queue,  that has finished 
     * its processing chain with update implications for the frontier state.
     */
    public class Finish extends InEvent {
        CrawlURI caUri;
        public Finish(CrawlURI c) {
            this.caUri = c;
        }
        public void process() {
            try {
                KeyedProperties.loadOverridesFrom(caUri);
                processFinish(caUri);
            } finally {
                KeyedProperties.clearOverridesFrom(caUri); 
            }
        }   
    }
    
    /**
     * An request that the frontier enter a new Frontier.State. 
     */
    public class SetTargetState extends InEvent {
        State target;
        public SetTargetState(State target) {
            this.target = target;
        }
        @Override
        public void process() {
            processSetTargetState(target);
            // TODO: perhaps null reachedState, because until new state is 
            // reached it's misleading?
        }
    }

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
    
    @SuppressWarnings("unchecked")
    protected void applyOverridesTo(CrawlURI curi) {
        curi.setOverlayMapsSource(sheetOverlaysManager); 
        if(!curi.haveOverlayNamesBeenSet()) {
            sheetOverlaysManager.applyOverlays(curi);
        }
    }
}
