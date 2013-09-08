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
 
package org.archive.crawler.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.reporting.AlertThreadGroup;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.modules.CandidateChain;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.DispositionChain;
import org.archive.modules.FetchChain;
import org.archive.modules.net.ServerCache;
import org.archive.modules.seeds.SeedModule;
import org.archive.spring.ConfigPath;
import org.archive.util.ReportUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.Lifecycle;
import org.springframework.context.support.AbstractApplicationContext;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;

/**
 * CrawlController collects all the classes which cooperate to
 * perform a crawl and provides a high-level interface to the
 * running crawl.
 *
 * As the "global context" for a crawl, subcomponents will
 * often reach each other through the CrawlController.
 *
 * @contributor gojomo
 */
public class CrawlController 
implements Serializable, 
           Lifecycle,
           ApplicationContextAware,
           Checkpointable {
    private static final long serialVersionUID = 1L;
    
    // ApplicationContextAware implementation, for eventing
    protected AbstractApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = (AbstractApplicationContext)applicationContext;
    }
    
    protected CrawlMetadata metadata;
    public CrawlMetadata getMetadata() {
        return metadata;
    }
    @Autowired
    public void setMetadata(CrawlMetadata provider) {
        this.metadata = provider;
    }
    
    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    /**
     * The frontier to use for the crawl.
     */
    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    /**
     * Scratch directory for temporary overflow-to-disk
     */
    protected ConfigPath scratchDir = 
        new ConfigPath("scratch subdirectory","scratch");
    public ConfigPath getScratchDir() {
        return scratchDir;
    }
    public void setScratchDir(ConfigPath scratchDir) {
        this.scratchDir = scratchDir;
    }

    /**
     * Statistics tracking modules.  Any number of specialized statistics 
     * trackers that monitor a crawl and write logs, reports and/or provide 
     * information to the user interface.
     */
    protected StatisticsTracker statisticsTracker;
    public StatisticsTracker getStatisticsTracker() {
        return this.statisticsTracker;
    }
    @Autowired
    public void setStatisticsTracker(StatisticsTracker statisticsTracker) {
        this.statisticsTracker = statisticsTracker;
    }

    protected SeedModule seeds;
    public SeedModule getSeeds() {
        return this.seeds;
    }
    @Autowired
    public void setSeeds(SeedModule seeds) {
        this.seeds = seeds;
    }
    
    /**
     * Fetch chain
     */
    protected FetchChain fetchChain;
    public FetchChain getFetchChain() {
        return this.fetchChain;
    }
    @Autowired
    public void setFetchChain(FetchChain fetchChain) {
        this.fetchChain = fetchChain;
    }
    
    /**
     * Disposition chain
     */
    protected DispositionChain dispositionChain;
    public DispositionChain getDispositionChain() {
        return this.dispositionChain;
    }
    @Autowired
    public void setDispositionChain(DispositionChain dispositionChain) {
        this.dispositionChain = dispositionChain;
    }
    
    /**
     * Candidate chain
     */
    protected CandidateChain candidateChain;
    public CandidateChain getCandidateChain() {
        return this.candidateChain;
    }
    @Autowired
    public void setCandidateChain(CandidateChain candidateChain) {
        this.candidateChain = candidateChain;
    }

    /**
     * Maximum number of threads processing URIs at the same time.
     */
    protected int maxToeThreads; 
    public int getMaxToeThreads() {
        return maxToeThreads;
    }
    @Value("25")
    public void setMaxToeThreads(int maxToeThreads) {
        this.maxToeThreads = maxToeThreads;
        if(toePool!=null) {
            toePool.setSize(this.maxToeThreads);
        }
    }
    
    /** whether to keep running (without pause or finish) when frontier is empty */
    protected boolean runWhileEmpty = false; 
    public boolean getRunWhileEmpty() {
        return runWhileEmpty;
    }
    public void setRunWhileEmpty(boolean runWhileEmpty) {
        this.runWhileEmpty = runWhileEmpty;
    }

    /** whether to pause at crawl start */
    protected boolean pauseAtStart = true; 
    public boolean getPauseAtStart() {
        return pauseAtStart;
    }
    public void setPauseAtStart(boolean pauseAtStart) {
        this.pauseAtStart = pauseAtStart;
    }
    
    /**
     * Size in bytes of in-memory buffer to record outbound traffic. One such 
     * buffer is reserved for every ToeThread. 
     */
    protected int recorderOutBufferBytes = 16 * 1024; // 16KiB
    public int getRecorderOutBufferBytes() {
        return recorderOutBufferBytes;
    }
    public void setRecorderOutBufferBytes(int recorderOutBufferBytes) {
        this.recorderOutBufferBytes = recorderOutBufferBytes;
    }
    
    /**
     * Size in bytes of in-memory buffer to record inbound traffic. One such 
     * buffer is reserved for every ToeThread.
     */
    protected int recorderInBufferBytes = 512 * 1024; // 512KiB
    public int getRecorderInBufferBytes() {
        return recorderInBufferBytes;
    }
    public void setRecorderInBufferBytes(int recorderInBufferBytes) {
        this.recorderInBufferBytes = recorderInBufferBytes;
    }

    protected CrawlerLoggerModule loggerModule;
    public CrawlerLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(CrawlerLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }
    
    /**
     * Messages from the crawlcontroller.
     *
     * They appear on console.
     */
    private final static Logger LOGGER =
        Logger.getLogger(CrawlController.class.getName());

    private transient ToePool toePool;

    // emergency reserve of memory to allow some progress/reporting after OOM
    private transient LinkedList<byte[]> reserveMemory;
    private static final int RESERVE_BLOCKS = 1;
    private static final int RESERVE_BLOCK_SIZE = 12*1024*1024; // 12 MB

    /**
     * Crawl exit status.
     */
    private transient CrawlStatus sExit = CrawlStatus.CREATED;

    public static enum State {
        NASCENT, RUNNING, EMPTY, PAUSED, PAUSING, 
        STOPPING, FINISHED, PREPARING 
    }

    transient private State state = State.NASCENT;
    
    public CrawlController() {
    }
    
    transient protected AlertThreadGroup alertThreadGroup;
    
    public void start() {
        // cache AlertThreadGroup for later ToePool launch
        AlertThreadGroup atg = AlertThreadGroup.current();
        if(atg!=null) {
            alertThreadGroup = atg;
        }
        
        if(isRunning) {
            return; 
        }
       
        sExit = CrawlStatus.FINISHED_ABNORMAL;

        // force creation of DNS Cache now -- avoids CacheCleaner in toe-threads group
        // also cap size at 1 (we never wanta cached value; 0 is non-operative)
        Lookup.getDefaultCache(DClass.IN).setMaxEntries(1);
        
        reserveMemory = new LinkedList<byte[]>();
        for(int i = 0; i < RESERVE_BLOCKS; i++) {
            reserveMemory.add(new byte[RESERVE_BLOCK_SIZE]);
        }
        isRunning = true; 
    }
    
    protected boolean isRunning = false; 
    public boolean isRunning() {
        return isRunning; 
    }

    public void stop() {
        // TODO: more stop/cleanup?
        isRunning = false; 
    }

    /**
     * Send crawl change event to all listeners.
     * @param newState State change we're to tell listeners' about.
     * @param message Message on state change.
     */
    protected void sendCrawlStateChangeEvent(State newState, 
            CrawlStatus status) {
        if(this.state == newState) {
            // suppress duplicate state-reports
            return;
        }
        this.state = newState;
        LOGGER.fine("reached CrawlController.State " + this.state + ", notifying listeners");
        CrawlStateEvent event = new CrawlStateEvent(this,newState,status.getDescription());
        appCtx.publishEvent(event); 
    }
    

    // TODO: provide better knowledge/guard against twice-starting
    protected boolean hasStarted = false;

    public boolean hasStarted() {
        return hasStarted; 
    }

    protected boolean isStopComplete = false;
    public boolean isStopComplete() {
        return isStopComplete;
    }
    
    /** 
     * Operator requested crawl begin
     */
    public void requestCrawlStart() {
        hasStarted = true; 
        sendCrawlStateChangeEvent(State.PREPARING, CrawlStatus.PREPARING);
        
        if(recoveryCheckpoint==null) {
            // only announce (trigger scheduling of) seeds
            // when doing a cold (non-recovery) start
            getSeeds().announceSeeds();
        }
        
        setupToePool();

        // A proper exit will change this value.
        this.sExit = CrawlStatus.FINISHED_ABNORMAL;
        
        if (getPauseAtStart()) {
            // frontier is already paused unless started, so just 
            // 'complete'/ack pause
            completePause();
        } else {
            getFrontier().run();
        }
    }

    /**
     * Called when the last toethread exits.
     */
    protected void completeStop() {
        if (!isRunning) {
            return;
        }
        
        LOGGER.fine("Entered complete stop.");

        statisticsTracker.getSnapshot(); // ???
        
        this.reserveMemory = null;
        if (this.toePool != null) {
            this.toePool.cleanup();
        }
        this.toePool = null;

        LOGGER.fine("Finished crawl.");

        try {
            if (appCtx.isRunning()) {
                appCtx.stop();
            }
        } catch (RuntimeException re) {
            LOGGER.log(Level.SEVERE,re.getMessage(),re);
        }
        
        sendCrawlStateChangeEvent(State.FINISHED, this.sExit);

        // CrawlJob needs to be sure all beans have received FINISHED signal before teardown
        this.isStopComplete = true;
        appCtx.publishEvent(new StopCompleteEvent(this)); 
    }
    
    public static class StopCompleteEvent extends ApplicationEvent {
        private static final long serialVersionUID = 1L;
        public StopCompleteEvent(Object source) {
            super(source);
        }
    }
    
    protected synchronized void completePause() {
        sendCrawlStateChangeEvent(State.PAUSED, CrawlStatus.PAUSED);
    }

    private boolean shouldContinueCrawling() {
        Frontier frontier = getFrontier();
        if (frontier.isEmpty() && !getRunWhileEmpty()) {
            this.sExit = CrawlStatus.FINISHED;
            return false;
        }
        // unsure this is correct; perhaps should be constant true
        return isActive();
    }

    /**
     * Operator requested for crawl to stop.
     */
    public synchronized void requestCrawlStop() {
        if(state == State.STOPPING) {
            // second stop request; nudge the threads with interrupts
            getToePool().cleanup();
        }
        requestCrawlStop(CrawlStatus.ABORTED);
    }
    
    /**
     * Operator requested for crawl to stop.
     * @param message 
     */
    public synchronized void requestCrawlStop(CrawlStatus message) {
        if (state == State.NASCENT) {
            this.sExit = message;
            this.state = State.FINISHED;
            this.isStopComplete = true;
        }
        if (state == State.STOPPING || state == State.FINISHED ) {
            return;
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null.");
        }
        if(this.sExit != CrawlStatus.FINISHED) {
            // don't clobber an already-FINISHED with alternate status
            this.sExit = message;
        }
        beginCrawlStop();
    }

    /**
     * Start the process of stopping the crawl. 
     */
    public void beginCrawlStop() {
        LOGGER.fine("Started.");
        sendCrawlStateChangeEvent(State.STOPPING, this.sExit);
        Frontier frontier = getFrontier();
        if (frontier != null) {
            frontier.terminate();
        }
        LOGGER.fine("Finished."); 
    }
    
    /**
     * Stop the crawl temporarly.
     */
    public synchronized void requestCrawlPause() {
        if (state == State.PAUSING || state == State.PAUSED) {
            // Already about to pause
            return;
        }
        sExit = CrawlStatus.WAITING_FOR_PAUSE;
        getFrontier().pause();
        sendCrawlStateChangeEvent(State.PAUSING, this.sExit);
        // wait for pause to come via frontier changes
    }

    /**
     * Tell if the controller is paused
     * @return true if paused
     */
    public boolean isPaused() {
        return state == State.PAUSED;
    }
    
    public boolean isPausing() {
        return state == State.PAUSING;
    }
    
    /**
     * Is this crawl actively able/trying to crawl? Includes both 
     * states RUNNING and EMPTY.
     * @return
     */
    public boolean isActive() {
        return state == State.RUNNING || state == State.EMPTY;
    }

    public boolean isFinished() {
        return state == State.FINISHED;
    }
    
    /**
     * Resume crawl from paused state
     */
    public void requestCrawlResume() {
        if (state != State.PAUSING && state != State.PAUSED) {
            // Can't resume if not been told to pause
            return;
        }
        
        assert toePool != null;
        
        Frontier f = getFrontier();
        f.unpause();
        sendCrawlStateChangeEvent(State.RUNNING, CrawlStatus.RUNNING);
    }

    /**
     * @return Active toe thread count.
     */
    public int getActiveToeCount() {
        if (toePool == null) {
            return 0;
        }
        return toePool.getActiveToeCount();
    }

    protected void setupToePool() {
        toePool = new ToePool(alertThreadGroup,this);
        // TODO: make # of toes self-optimizing
        toePool.setSize(getMaxToeThreads());
        toePool.waitForAll();
    }

    /**
     * @return The number of ToeThreads
     *
     * @see ToePool#getToeCount()
     */
    public int getToeCount() {
        return this.toePool == null? 0: this.toePool.getToeCount();
    }

    /**
     * @return The ToePool
     */
    public ToePool getToePool() {
        return toePool;
    }

    /**
     * Kills a thread. For details see
     * {@link org.archive.crawler.framework.ToePool#killThread(int, boolean)
     * ToePool.killThread(int, boolean)}.
     * @param threadNumber Thread to kill.
     * @param replace Should thread be replaced.
     * @see org.archive.crawler.framework.ToePool#killThread(int, boolean)
     */
    public void killThread(int threadNumber, boolean replace){
        toePool.killThread(threadNumber, replace);
    }

    /**
     * Evaluate if the crawl should stop because it is finished,
     * without actually stopping the crawl.
     * 
     * @return true if crawl is at a finish-possible state
     */
    public boolean atFinish() {
        return isActive() && !shouldContinueCrawling();
    }

    private void readObject(ObjectInputStream stream)
    throws IOException, ClassNotFoundException {
        this.state = State.PAUSED;
        stream.defaultReadObject();
    }
  
    public void freeReserveMemory() {
        if(!reserveMemory.isEmpty()) {
            reserveMemory.removeLast();
            System.gc();
        }
    }

    /**
     * Log to the progress statistics log.
     * @param msg Message to write the progress statistics log.
     */
    public void logProgressStatistics(final String msg) {
        loggerModule.getProgressStats().info(msg);
    }

    /**
     * @return CrawlController state.
     */
    public Object getState() {
        return this.state;
    }
    
    public CrawlStatus getCrawlExitStatus() {
        return this.sExit;
    }

    public String getToeThreadReport() {
        if(toePool==null) {
            return "no ToeThreads";
        }
        StringWriter sw = new StringWriter();
        toePool.reportTo(new PrintWriter(sw));
        return sw.toString();
    }

    public String getToeThreadReportShort() {
        return (toePool == null) ? "" : ReportUtils.shortReportLine(toePool);
    }

    public Map<String,Object> getToeThreadReportShortData() {
        return toePool == null ? null : toePool.shortReportMap();
        
    }

    public String getFrontierReportShort() {
        return ReportUtils.shortReportLine(getFrontier());
    }

    /**
     * Receive notification from the frontier, in the frontier's own 
     * manager thread, that the frontier has reached a new state. 
     * 
     * @param reachedState the state the frontier has reached
     */
    public void noteFrontierState(Frontier.State reachedState) {
        switch (reachedState) {
        case RUN: 
            LOGGER.info("Crawl running.");
            sendCrawlStateChangeEvent(State.RUNNING, CrawlStatus.RUNNING);
            break;
        case EMPTY: 
            LOGGER.info("Crawl empty.");
            if(!getRunWhileEmpty()) {
                this.sExit = CrawlStatus.FINISHED;
                beginCrawlStop();
            }
            sendCrawlStateChangeEvent(State.EMPTY, CrawlStatus.RUNNING);
            break; 
        case PAUSE:
            if (state == State.PAUSING) {
                completePause();
            }
            break;
        case FINISH:
            completeStop();
            break;
        default:
            // do nothing
        }
    }
    
    // Checkpointable
    // CrawlController's only interest is in knowing that a Checkpoint is
    // being recovered
    public void startCheckpoint(Checkpoint checkpointInProgress) {}
    public void doCheckpoint(Checkpoint checkpointInProgress) throws IOException {}
    public void finishCheckpoint(Checkpoint checkpointInProgress) {}
    protected Checkpoint recoveryCheckpoint;
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        this.recoveryCheckpoint = recoveryCheckpoint;
    }
    
}//EOC
