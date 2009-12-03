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
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.comparator.LastModifiedFileComparator;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.crawler.reporting.CrawlStatSnapshot;
import org.archive.spring.ConfigPath;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * Executes checkpoints, and offers convenience methods for enumerating 
 * available Checkpoints and injecting a recovery-Checkpoint after 
 * build and before launch (setRecoveryCheckpointByName).
 * 
 * Offers optional automatic checkpointing at a configurable interval 
 * in minutes. 
 * 
 * @contributor stack
 * @contributor gojomo
 * @contributor pjack
 */
public class CheckpointService implements Lifecycle, ApplicationContextAware {
    private final static Logger LOGGER =
        Logger.getLogger(CheckpointService.class.getName());
        
    /** Next overall series checkpoint number */
    protected int nextCheckpointNumber = 1;
    
    Checkpoint checkpointInProgress;
    
    CrawlStatSnapshot lastCheckpointSnapshot = null;
    
    /**Setup in constructor or on a call to recovery */
    protected transient Timer timerThread = null;

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
     * Period at which to create automatic checkpoints; -1 means
     * no auto checkpointing. 
     */
    int checkpointIntervalMinutes = -1;
    public int getCheckpointIntervalMinutes() {
        return checkpointIntervalMinutes;
    }
    public void setCheckpointIntervalMinutes(int checkpointIntervalMinutes) {
        this.checkpointIntervalMinutes = checkpointIntervalMinutes;
    }
    
    Checkpoint recoveryCheckpoint;
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint checkpoint) {
        this.recoveryCheckpoint = checkpoint; 
        checkpoint.getCheckpointDir().setBase(getCheckpointsDir());
    }
    public Checkpoint getRecoveryCheckpoint() {
        return this.recoveryCheckpoint;
    }
    
    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }
    
    // ApplicationContextAware implementation, for eventing
    AbstractApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = (AbstractApplicationContext)applicationContext;
    }
    
    /**
     * Create a new Checkpointer
     */
    public CheckpointService() {
    }
    
    public void start() { 
        if (isRunning) {
            return;
        }
        this.isRunning = true; 
        // Convert period from hours to milliseconds.
        long periodMs = getCheckpointIntervalMinutes() * (60 * 1000);
        if(periodMs<=0) {
            return;
        }
        TimerTask tt = new TimerTask() {
            public void run() {
                if (isCheckpointing()) {
                    LOGGER.info("CheckpointTimerThread skipping checkpoint, " +
                        "already checkpointing: State: " +
                        controller.getState());
                    return;
                }
                LOGGER.info("TimerThread request checkpoint");
                requestCrawlCheckpoint();
            }
        };
        this.timerThread = new Timer(true);
        this.timerThread.schedule(tt, periodMs, periodMs);
        LOGGER.info("Installed Checkpoint TimerThread to checkpoint every " +
                periodMs + " milliseconds.");
    }
    
    boolean isRunning = false; 
    public boolean isRunning() {
        return isRunning; 
    }
    
    
    public void stop() {
        if (this.timerThread != null) {
            LOGGER.info("Cleaned up Checkpoint TimerThread.");
            this.timerThread.cancel();
            this.timerThread = null;
        }
        this.isRunning = false; 
    }
    
    /**
     * @return Returns the nextCheckpoint index.
     */
    public int getNextCheckpointNumber() {
        return this.nextCheckpointNumber;
    }
    
    /**
     * Run a checkpoint of the crawler
     */
    public synchronized String requestCrawlCheckpoint() throws IllegalStateException {
        if (isCheckpointing()) {
            throw new IllegalStateException("Checkpoint already running.");
        }
        
        // prevent redundant auto-checkpoints when crawler paused
        if(controller.isPaused()) {
            if (controller.getStatisticsTracker().getSnapshot().sameProgressAs(lastCheckpointSnapshot)) {
                LOGGER.info("no progress since last checkpoint; ignoring");
                System.err.println("no progress since last checkpoint; ignoring");
                return null;
            }
        }
        
        checkpointInProgress = new Checkpoint();
        checkpointInProgress.generateFrom(getCheckpointsDir(),getNextCheckpointNumber());
        
        @SuppressWarnings("unchecked")
        Map<String,Checkpointable> toCheckpoint = appCtx.getBeansOfType(Checkpointable.class);
        
        try {
            // pre (incl. acquire necessary locks)
            long startMs = System.currentTimeMillis();
            for(Checkpointable c : toCheckpoint.values()) {
                c.startCheckpoint(checkpointInProgress);
            }
            long duration = System.currentTimeMillis() - startMs; 
//            System.err.println("all startCheckpoint() completed in "+duration+"ms");
            
            // flush/write
            for(Checkpointable c : toCheckpoint.values()) {
                long doMs = System.currentTimeMillis();
                c.doCheckpoint(checkpointInProgress);
                long doDuration = System.currentTimeMillis() - doMs; 
//                System.err.println("doCheckpoint() "+c+" in "+doDuration+"ms");
            }
            checkpointInProgress.setSuccess(true); 
            
        } catch (Exception e) {
            checkpointFailed(e);
        } finally {
            checkpointInProgress.writeValidity(
                controller.getStatisticsTracker().getProgressStamp());
            lastCheckpointSnapshot = controller.getStatisticsTracker().getSnapshot();
            // close (incl. release locks)
            for(Checkpointable c : toCheckpoint.values()) {
                c.finishCheckpoint(checkpointInProgress);
            }
        }

        this.nextCheckpointNumber++;
        LOGGER.info("finished checkpoint "+checkpointInProgress.getName());
        String nameToReport = checkpointInProgress.getSuccess() ? checkpointInProgress.getName() : null;
        this.checkpointInProgress = null;
        return nameToReport;
    }

    
    /**
     * @return True if a checkpoint is in progress.
     */
    public boolean isCheckpointing() {
        return this.checkpointInProgress != null;
    }

    /**
     * Note that a checkpoint failed
     *
     * @param e Exception checkpoint failed on.
     */
    protected void checkpointFailed(Exception e) {
        LOGGER.log(Level.WARNING, " Checkpoint failed", e);
    }
    
    protected void checkpointFailed(final String message) {
        LOGGER.warning(message);
    }

    public boolean hasAvailableCheckpoints() {
        if(getRecoveryCheckpoint()!=null || isRunning()) {
            return false;
        }
        return (getAvailableCheckpointDirectories() != null 
                && getAvailableCheckpointDirectories().length > 0);
    }

    @SuppressWarnings("unchecked")
    public File[] getAvailableCheckpointDirectories() {
        File[] dirs = getCheckpointsDir().getFile().listFiles((FileFilter)FileFilterUtils.directoryFileFilter());
        Arrays.sort(dirs, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
        return dirs;
    }
    
    /**
     * Given the name of a valid checkpoint subdirectory in the checkpoints
     * directory, create a Checkpoint instance, and insert it into all 
     * Checkpointable beans. 
     * 
     * @param selectedCheckpoint
     */
    @SuppressWarnings("unchecked")
    public void setRecoveryCheckpointByName(String selectedCheckpoint) {
        Checkpoint recoveryCheckpoint = new Checkpoint();
        recoveryCheckpoint.getCheckpointDir().setBase(getCheckpointsDir());
        recoveryCheckpoint.getCheckpointDir().setPath(selectedCheckpoint);
        recoveryCheckpoint.afterPropertiesSet();
        setRecoveryCheckpoint(recoveryCheckpoint);
        Map<String,Checkpointable> toSetRecovery = appCtx.getBeansOfType(Checkpointable.class);
        
        for(Checkpointable c : toSetRecovery.values()) {
            c.setRecoveryCheckpoint(recoveryCheckpoint);
        }
    }
} //EOC
