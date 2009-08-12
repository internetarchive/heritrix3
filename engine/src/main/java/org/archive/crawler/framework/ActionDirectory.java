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
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.archive.modules.seeds.SeedModule;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.Lifecycle;

/**
 * Directory watched for new files. Depending on their extension, will
 * process with regard to current crawl, and rename with a datestamp 
 * into the 'done' directory. 
 * 
 * Currently supports:
 *  - .seeds(.gz)
 *      add each URI found in file as a new seed (to be crawled
 *      if not already; to affect scope if appropriate).
 *  - (.s).recover(.gz)
 *      treat as traditional recovery log: consider all 'Fs'-tagged lines 
 *      included, then try-rescheduling all 'F+'-tagged lines. (If ".s." 
 *      present, try scoping URIs before including/scheduling.)
 *  - (.s).include(.gz) 
 *      add each URI found in a recover-log like file (regardless of its
 *      tagging) to the frontier's alreadyIncluded filter, preventing them
 *      from being recrawled. ('.s.' indicates to apply scoping.)
 *  - (.s).schedule(.gz)
 *      add each URI found in a recover-log like file (regardless of its
 *      tagging) to the frontier's queues. ('.s.' indicates to apply 
 *      scoping.)
 *      
 * Future support planned:
 *  - .robots: invalidate robots ASAP
 *  - (?) .block: block-all on named site(s)
 *  -  .overlay: add new overlay settings
 *  - .js .rb .bsh .rb etc - execute arbitrary script (a la ScriptedProcessor)
 * 
 * @contributor gojomo
 */
public class ActionDirectory implements ApplicationContextAware, Lifecycle, Runnable {
    final private static Logger LOGGER = 
        Logger.getLogger(ActionDirectory.class.getName()); 

    ScheduledExecutorService executor;

    /** how long after crawl start to first scan action directory */
    protected int initialDelay = 30;
    public int getInitialDelay() {
        return initialDelay;
    }
    public void setInitialDelay(int initialDelay) {
        this.initialDelay = initialDelay;
    }
    /** delay between scans of actionDirectory for new files */
    protected int delay = 30;
    public int getDelay() {
        return delay;
    }
    public void setDelay(int delay) {
        this.delay = delay;
    }
    
    /**
     * Scratch directory for temporary overflow-to-disk
     */
    protected ConfigPath actionDir = 
        new ConfigPath("ActionDirectory source directory","action");
    public ConfigPath getActionDir() {
        return actionDir;
    }
    public void setActionDir(ConfigPath actionDir) {
        this.actionDir = actionDir;
    }
    
    /**
     * Scratch directory for temporary overflow-to-disk
     */
    protected ConfigPath doneDir = 
        new ConfigPath("ActionDirectory done directory","action/done");
    public ConfigPath getDoneDir() {
        return doneDir;
    }
    public void setDoneDir(ConfigPath scratchDir) {
        this.doneDir = scratchDir;
    }
    
    ApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = applicationContext;
    }
    
    protected SeedModule seeds;
    public SeedModule getSeeds() {
        return this.seeds;
    }
    @Autowired
    public void setSeeds(SeedModule seeds) {
        this.seeds = seeds;
    }
    
    /** autowired frontier for actions */
    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }
    
    public boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }
    
    public void start() {
        if (isRunning()) {
            return;
        }
        // create directories
        getActionDir().getFile().mkdirs();
        getDoneDir().getFile().mkdirs();
        // start background executor
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this, getInitialDelay(), getDelay(), TimeUnit.SECONDS);
    }
    
    public void stop() {
        executor.shutdown();
        try {
            while (!executor.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            // do nothing
        }
    }
    
    /** 
     * Action taken at scheduled intervals
     * @see java.lang.Runnable#run()
     */
    public void run() {
        scanActionDirectory();
    }
    
    /**
     * Find any new files in the 'action' directory; process each in
     * order. 
     */
    protected void scanActionDirectory() {
        File dir = actionDir.getFile();
        File[] files = dir.listFiles((FileFilter)FileFilterUtils.fileFileFilter());
        Arrays.sort(files); 
        for(File f : files) {
            try {
                actOn(f);
            } catch (Throwable e) {
                LOGGER.log(Level.SEVERE,"unhandled exception from actifile: "+f,e);
            }
        }
    }
    
    /**
     * Process an individual action file found 
     * 
     * @param actionFile File to process
     */
    protected void actOn(File actionFile) {
        LOGGER.info("processing action file: "+actionFile);
        String filename = actionFile.getName(); 
        boolean isGzip = filename.endsWith(".gz");
        String corename = isGzip ? filename.substring(0,filename.length()-3) : filename;
        
        if(corename.endsWith(".seeds")) {
            // import seeds
            getSeeds().actOn(actionFile); 
        } else if (corename.endsWith(".recover")) {
            // apply recovery-log
            boolean alsoScope = corename.endsWith(".s.recover");
            try {
                getFrontier().importRecoverFormat(actionFile, alsoScope, true, false, "Fs ");
                getFrontier().importRecoverFormat(actionFile, alsoScope, false, false, "F\\+ ");
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE,"problem with action file: "+actionFile,ioe);
            }
        } else if (corename.endsWith(".include")) {
            // consider-included-only (do not schedule)
            boolean alsoScope = corename.endsWith(".s.include");
            try {
                getFrontier().importRecoverFormat(actionFile, alsoScope, true, false, ".*");
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE,"problem with action file: "+actionFile,ioe);
            }
        } else if (corename.endsWith(".schedule")) {
            // schedule to queues
            boolean alsoScope = corename.endsWith(".s.schedule");
            try {
                getFrontier().importRecoverFormat(actionFile, alsoScope, false, false, ".*");
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE,"problem with action file: "+actionFile,ioe);
            }
        } else if (corename.endsWith(".force")) {
            // schedule to queues
            boolean alsoScope = corename.endsWith(".s.force");
            try {
                getFrontier().importRecoverFormat(actionFile, alsoScope, false, true, ".*");
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE,"problem with action file: "+actionFile,ioe);
            }
//        } else if (filename.endsWith(".robots")) {
//            // force refresh of robots
//            // TODO
//        } else {
//            // try as script
//            // TODO
        } else {
            LOGGER.warning("action file ignored: "+actionFile);
        }
        
        // move file to 'done' area with timestamp prefix
        String timestamp = ArchiveUtils.get17DigitDate();
        while(actionFile.exists()) {
            try {
                FileUtils.moveFile(actionFile, new File(doneDir.getFile(),timestamp+"."+actionFile.getName()));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"unable to move "+actionFile,e);
            }
        }
    }
    
}