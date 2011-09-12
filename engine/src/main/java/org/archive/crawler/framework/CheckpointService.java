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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
import org.archive.spring.ConfigPathConfigurer;
import org.archive.spring.HasValidator;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.validation.Validator;

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
public class CheckpointService implements Lifecycle, ApplicationContextAware, HasValidator {
    private final static Logger LOGGER =
        Logger.getLogger(CheckpointService.class.getName());
        
    /** Next overall series checkpoint number */
    protected int nextCheckpointNumber = 1;
    
    Checkpoint checkpointInProgress;
    
    CrawlStatSnapshot lastCheckpointSnapshot = null;
    
    /** service for auto-checkpoint tasks at an interval */
    protected Timer timer = new Timer(true);
    protected TimerTask checkpointTask = null; 
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
    long checkpointIntervalMinutes = -1;
    public long getCheckpointIntervalMinutes() {
        return checkpointIntervalMinutes;
    }
    public void setCheckpointIntervalMinutes(long interval) {
        long oldVal = checkpointIntervalMinutes; 
        this.checkpointIntervalMinutes = interval;
        if(checkpointIntervalMinutes!=oldVal) {
            setupCheckpointTask();
        }
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
    
    public synchronized void start() { 
        if (isRunning) {
            return;
        }
        // report if checkpoint incomplete/invalid
        if(getRecoveryCheckpoint()!=null) {
            File cpDir = getRecoveryCheckpoint().getCheckpointDir().getFile();
            if(!Checkpoint.hasValidStamp(cpDir)) {
                LOGGER.severe(
                    "checkpoint '"+cpDir.getAbsolutePath()
                    +"' missing validity stamp file; checkpoint data "
                    +"may be missing or otherwise corrupt.");
            }
        }
        this.isRunning = true; 
        setupCheckpointTask();
    }
    
    /**
     * Setup checkpointTask according to current interval. (An already-scheduled
     * task, if any, is canceled.)
     */
    protected synchronized void setupCheckpointTask() {
        if(checkpointTask!=null) {
            checkpointTask.cancel();
        }
        if(!isRunning) {
            // don't setup before start (or after finish), even if
            // triggered by interval change
            return; 
        }
        // Convert period from minutes to milliseconds.
        long periodMs = getCheckpointIntervalMinutes() * (60L * 1000L);
        if(periodMs<=0) {
            return;
        }
        checkpointTask = new TimerTask() {
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
        this.timer.schedule(checkpointTask, periodMs, periodMs);
        LOGGER.info("Installed Checkpoint TimerTask to checkpoint every " +
                periodMs + " milliseconds.");
    }
    
    boolean isRunning = false; 
    public synchronized boolean isRunning() {
        return isRunning; 
    }
    
    
    public synchronized void stop() {
        LOGGER.info("Cleaned up Checkpoint TimerThread.");
        this.timer.cancel();
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
        
        Map<String,Checkpointable> toCheckpoint = appCtx.getBeansOfType(Checkpointable.class);
        
        checkpointInProgress = new Checkpoint();
        try {
            checkpointInProgress.generateFrom(getCheckpointsDir(),getNextCheckpointNumber());
            
            // pre (incl. acquire necessary locks)
//            long startMs = System.currentTimeMillis();
            for(Checkpointable c : toCheckpoint.values()) {
                c.startCheckpoint(checkpointInProgress);
            }
//            long duration = System.currentTimeMillis() - startMs; 
//            System.err.println("all startCheckpoint() completed in "+duration+"ms");
            
            // flush/write
            for(Checkpointable c : toCheckpoint.values()) {
//                long doMs = System.currentTimeMillis();
                c.doCheckpoint(checkpointInProgress);
//                long doDuration = System.currentTimeMillis() - doMs; 
//                System.err.println("doCheckpoint() "+c+" in "+doDuration+"ms");
            }
            checkpointInProgress.setSuccess(true); 
            appCtx.publishEvent(new CheckpointSuccessEvent(this,checkpointInProgress));
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
        LOGGER.log(Level.SEVERE, " Checkpoint failed", e);
    }
    
    protected void checkpointFailed(final String message) {
        LOGGER.warning(message);
    }

    public boolean hasAvailableCheckpoints() {
        if(getRecoveryCheckpoint()!=null || isRunning()) {
            return false;
        }
        return (findAvailableCheckpointDirectories() != null 
                && findAvailableCheckpointDirectories().size() > 0);
    }

    /**
     * Returns a list of available, valid (contains 'valid' file) 
     * checkpoint directories, as File instances, with the more 
     * recently-written appearing first. 
     * 
     * @return List of valid checkpoint directory File instances
     */
    @SuppressWarnings("unchecked")
    public List<File> findAvailableCheckpointDirectories() {
        File[] dirs = getCheckpointsDir().getFile().listFiles((FileFilter)FileFilterUtils.directoryFileFilter());
        if (dirs == null) {
            return Collections.EMPTY_LIST;
        }
        Arrays.sort(dirs, LastModifiedFileComparator.LASTMODIFIED_REVERSE);
        LinkedList<File> dirsList = new LinkedList<File>(Arrays.asList(dirs));
        Iterator<File> iter = dirsList.iterator();
        while(iter.hasNext()) {
            File cpDir = iter.next();
            if(!Checkpoint.hasValidStamp(cpDir)) {
                LOGGER.warning("checkpoint '"+cpDir+"' missing validity stamp file; ignoring");
                iter.remove();
            }
        }
        return dirsList;
    }
    
    /**
     * Given the name of a valid checkpoint subdirectory in the checkpoints
     * directory, create a Checkpoint instance, and insert it into all 
     * Checkpointable beans. 
     * 
     * @param selectedCheckpoint
     */
    public synchronized void setRecoveryCheckpointByName(String selectedCheckpoint) {
        if(isRunning) {
            throw new RuntimeException("may not set recovery Checkpoint after launch");
        }
        Checkpoint recoveryCheckpoint = new Checkpoint();
        recoveryCheckpoint.getCheckpointDir().setBase(getCheckpointsDir());
        recoveryCheckpoint.getCheckpointDir().setPath(selectedCheckpoint);
        recoveryCheckpoint.getCheckpointDir().setConfigurer(appCtx.getBean(ConfigPathConfigurer.class));
        recoveryCheckpoint.afterPropertiesSet();
        setRecoveryCheckpoint(recoveryCheckpoint);
        Map<String,Checkpointable> toSetRecovery = appCtx.getBeansOfType(Checkpointable.class);
        
        for(Checkpointable c : toSetRecovery.values()) {
            c.setRecoveryCheckpoint(recoveryCheckpoint);
        }
    }
    
    static Validator VALIDATOR = new CheckpointValidator();
    @Override
    public Validator getValidator() {
        return VALIDATOR;
    }
} //EOC
