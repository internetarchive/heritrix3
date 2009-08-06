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
 * Initially supports:
 *  - .seeds : add each URI found in file as a new seed (to be crawled
 *             if not already; to affect scope if appropriate).
 *  
 * Future support:
 *  - .include : add each URI found in a recover-log like file to 
 *               the frontier's alreadyIncluded filter (preventing
 *               it from being recrawled)
 *  - .schedule : add each URI found in a recover-log like file to 
 *               the frontier's queues
 *  - .robots: invalidate robots ASAP
 *  - (?) .block: block-all on named site(s)
 *  -  .overlay: 
 *  - .js .rb .bsh .rb etc - execute arbitrary script (a la ScriptedProcessor)
 * 
 * @contributor gojomo
 */
public class ActionDirectory implements ApplicationContextAware, Lifecycle, Runnable {
    final private static Logger LOGGER = 
        Logger.getLogger(ActionDirectory.class.getName()); 

    ScheduledExecutorService executor;

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
    
    public boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }
    
    public void start() {
        if (isRunning()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this, 60, 60, TimeUnit.SECONDS);
    }
    
    public void stop() {
        executor.shutdown();
        try {
            while (!executor.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void run() {
        scanActionDirectory();
    }
    
    protected void scanActionDirectory() {
        File dir = actionDir.getFile();
        File[] files = dir.listFiles((FileFilter)FileFilterUtils.fileFileFilter());
        Arrays.sort(files); 
        for(File f : files) {
            actOn(f);
        }
    }
    
    protected void actOn(File f) {
        LOGGER.info("processing action file: "+f);
        String filename = f.getName(); 
        if(filename.endsWith(".seeds")) {
            getSeeds().actOn(f); 
        } else if (filename.endsWith("recover")||filename.endsWith("recover.gz")) {
            // TODO
//            FrontierJournal.importRecoverLog(params, controller);
        } else if (filename.endsWith(".robots")) {
            // force refresh of robots
            // TODO
        } else {
            // try as script
            
        }
        
        // move file to 'done' area with timestamp
        String timestamp = ArchiveUtils.get17DigitDate();
        while(f.exists()) {
            try {
                FileUtils.moveFile(f, new File(doneDir.getFile(),timestamp+"."+f.getName()));
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"unable to move "+f,e);
            }
        }
    }
}