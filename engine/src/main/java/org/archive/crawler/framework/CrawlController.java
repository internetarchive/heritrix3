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

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.logging.Logger;

import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.reporting.AlertThreadGroup;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.ProcessorChain;
import org.archive.modules.net.ServerCache;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
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
           ApplicationContextAware {
    private static final long serialVersionUID = 1L;
    
    // ApplicationContextAware implementation, for eventing
    AbstractApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = (AbstractApplicationContext)applicationContext;
    }
    
    CrawlMetadata metadata;
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
     * Checkpoints directory
     */
    protected ConfigPath checkpointsDir = 
        new ConfigPath("checkpoints subdirectory","checkpoints");
    public ConfigPath getCheckpointsDir() {
        return checkpointsDir;
    }
    public void setCheckpointsDir(ConfigPath checkpointsDir) {
        this.checkpointsDir = checkpointsDir;
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

    
    /**
     * Processor chain
     */
    protected ProcessorChain processorChain;
    public ProcessorChain getProcessorChain() {
        return this.processorChain;
    }
    @Autowired
    public void setProcessorChain(ProcessorChain processorChain) {
        this.processorChain = processorChain;
    }

    /**
     * Maximum number of threads processing URIs at the same time.
     */
    int maxToeThreads = 25; 
    public int getMaxToeThreads() {
        return maxToeThreads;
    }
    public void setMaxToeThreads(int maxToeThreads) {
        this.maxToeThreads = maxToeThreads;
        if(toePool!=null) {
            toePool.setSize(this.maxToeThreads);
        }
    }
    
    /** whether to pause, rather than finish, when crawl appears done */
    boolean pauseAtFinish = false; 
    public boolean getPauseAtFinish() {
        return pauseAtFinish;
    }
    public void setPauseAtFinish(boolean pauseAtFinish) {
        this.pauseAtFinish = pauseAtFinish;
    }

    /** whether to pause at crawl start */
    boolean pauseAtStart = false; 
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
    int recorderOutBufferBytes = 4096;
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
    int recorderInBufferBytes = 65536;
    public int getRecorderInBufferBytes() {
        return recorderInBufferBytes;
    }
    public void setRecorderInBufferBytes(int recorderInBufferBytes) {
        this.recorderInBufferBytes = recorderInBufferBytes;
    }
    
    /**
     * Period at which to create automatic checkpoints; -1 means
     * no auto checkpointing. 
     */
    int checkpointerPeriod = -1;
    public int getCheckpointerPeriod() {
        return checkpointerPeriod;
    }
    public void setCheckpointerPeriod(int checkpointerPeriod) {
        this.checkpointerPeriod = checkpointerPeriod;
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
    private transient LinkedList<char[]> reserveMemory;
    private static final int RESERVE_BLOCKS = 1;
    private static final int RESERVE_BLOCK_SIZE = 6*2^20; // 6MB

    /**
     * Crawl exit status.
     */
    private transient CrawlStatus sExit = CrawlStatus.CREATED;

    public static enum State {
        NASCENT, RUNNING, PAUSED, PAUSING, CHECKPOINTING, 
        STOPPING, FINISHED, PREPARING 
    }

    transient private State state = State.NASCENT;
    
    /**
     * Checkpointer.
     * Knows if checkpoint in progress and what name of checkpoint is.  Also runs
     * checkpoints.
     */
    private Checkpointer checkpointer;

    public CrawlController() {
    }
    
    transient AlertThreadGroup alertThreadGroup;
    
    public void start() {
        // cache AlertThreadGroup for later ToePool launch
        AlertThreadGroup atg = AlertThreadGroup.current();
        if(atg!=null) {
            alertThreadGroup = atg;
        }
        
        if(isRunning) {
            return; 
        }
        this.checkpointer = new Checkpointer(
                this, getCheckpointsDir().getFile());

        sExit = CrawlStatus.FINISHED_ABNORMAL;

        // force creation of DNS Cache now -- avoids CacheCleaner in toe-threads group
        // also cap size at 1 (we never wanta cached value; 0 is non-operative)
        Lookup.getDefaultCache(DClass.IN).setMaxEntries(1);
        
        reserveMemory = new LinkedList<char[]>();
        for(int i = 1; i < RESERVE_BLOCKS; i++) {
            reserveMemory.add(new char[RESERVE_BLOCK_SIZE]);
        }
        isRunning = true; 
    }
    
    boolean isRunning = false; 
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
     * @see #sendCheckpointEvent(File) for special case event sending
     * telling listeners to checkpoint.
     */
    @SuppressWarnings("unchecked")
    protected void sendCrawlStateChangeEvent(State newState, 
            CrawlStatus status) {
        if(this.state == newState) {
            // suppress duplicate state-reports
            return;
        }
        this.state = newState; 
        CrawlStateEvent event = new CrawlStateEvent(this,newState,status.getDescription());
        appCtx.publishEvent(event); 
    }
    

    // TODO: provide better knowledge/guard against twice-starting
    boolean hasStarted = false; 
    public boolean hasStarted() {
        return hasStarted; 
    }
    
    /** 
     * Operator requested crawl begin
     */
    public void requestCrawlStart() {
        hasStarted = true; 
        sendCrawlStateChangeEvent(State.PREPARING, CrawlStatus.PREPARING);
        frontier.loadSeeds();
        
        setupToePool();

        // A proper exit will change this value.
        this.sExit = CrawlStatus.FINISHED_ABNORMAL;
        
        if (getPauseAtStart()) {
            requestCrawlPause();
        } else {
            getFrontier().run();
        }
    }

    /**
     * Called when the last toethread exits.
     */
    protected void completeStop() {
        LOGGER.fine("Entered complete stop.");

        loggerModule.closeLogFiles();
        this.reserveMemory = null;
        if (this.checkpointer != null) {
            this.checkpointer.cleanup();
            this.checkpointer = null;
        }
        if (this.toePool != null) {
            this.toePool.cleanup();
        }
        this.toePool = null;

        LOGGER.fine("Finished crawl.");

        appCtx.stop(); 
        // Ok, now we are ready to exit.
        sendCrawlStateChangeEvent(State.FINISHED, this.sExit);
    }
    
    synchronized void completePause() {
        // Send a notifyAll. At least checkpointing thread may be waiting on a
        // complete pause.
        notifyAll();
        sendCrawlStateChangeEvent(State.PAUSED, CrawlStatus.PAUSED);
    }

    private boolean shouldContinueCrawling() {
        Frontier frontier = getFrontier();
        if (frontier.isEmpty()) {
            this.sExit = CrawlStatus.FINISHED;
            return false;
        }
        return state == State.RUNNING;
    }

    /**
     * Request a checkpoint.
     * Sets a checkpointing thread running.
     * @throws IllegalStateException Thrown if crawl is not in paused state
     * (Crawl must be first paused before checkpointing).
     */
    public synchronized void requestCrawlCheckpoint()
    throws IllegalStateException {
        if (this.checkpointer == null) {
            return;
        }
        if (this.checkpointer.isCheckpointing()) {
            throw new IllegalStateException("Checkpoint already running.");
        }
        this.checkpointer.checkpoint();
    }   
    
    /**
     * @return True if checkpointing.
     */
    public boolean isCheckpointing() {
        return this.state == State.CHECKPOINTING;
    }


    /**
     * Operator requested for crawl to stop.
     */
    public synchronized void requestCrawlStop() {
        requestCrawlStop(CrawlStatus.ABORTED);
    }
    
    /**
     * Operator requested for crawl to stop.
     * @param message 
     */
    public synchronized void requestCrawlStop(CrawlStatus message) {
        if (state == State.STOPPING || state == State.FINISHED || state == State.NASCENT) {
            return;
        }
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null.");
        }
        this.sExit = message;
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
    
    public boolean isStateRunning() {
        return state == State.RUNNING;
    }

    /**
     * Resume crawl from paused state
     */
    public void requestCrawlResume() {
        if (this.toePool == null) {
            this.setupToePool();
        }

        if (state != State.PAUSING && state != State.PAUSED && state != State.CHECKPOINTING) {
            // Can't resume if not been told to pause or if we're in middle of
            // a checkpoint.
            return;
        }
        
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
     * Evaluate if the crawl should stop because it is finished.
     */
    public void checkFinish() {
        if(atFinish()) {
            beginCrawlStop();
        }
    }

    /**
     * Evaluate if the crawl should stop because it is finished,
     * without actually stopping the crawl.
     * 
     * @return true if crawl is at a finish-possible state
     */
    public boolean atFinish() {
        return state == State.RUNNING && !shouldContinueCrawling();
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
        return (toePool == null) ? "" : ArchiveUtils.singleLineReport(toePool);
    }

    public String getFrontierReport() {
        StringWriter sw = new StringWriter();
        try {
            getFrontier().reportTo(new PrintWriter(sw));
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return sw.toString();
    }

    public String getFrontierReportShort() {
        return ArchiveUtils.singleLineReport(getFrontier());
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
            LOGGER.info("Crawl resumed.");
            sendCrawlStateChangeEvent(State.RUNNING, CrawlStatus.RUNNING);
        case PAUSE:
            if (state == State.PAUSING) {
                completePause();
                break;
            }
            if(atFinish()) { // really, "just reached finish"
                if (getPauseAtFinish()) {
                    requestCrawlPause();
                } else {
                    beginCrawlStop();
                }
                break;
            }
            if(state == State.STOPPING || state == State.FINISHED) {
                frontier.requestState(Frontier.State.FINISH);
            }
            break;
        case FINISH:
            completeStop();
            break;
        default:
            // do nothing
        }
    }
}//EOC
