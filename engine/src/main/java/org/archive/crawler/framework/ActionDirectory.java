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
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.modules.seeds.SeedModule;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.FilesystemLinkMaker;
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
    protected int initialDelaySeconds = 10;
    public int getInitialDelaySeconds() {
        return initialDelaySeconds;
    }
    public void setInitialDelaySeconds(int initialDelay) {
        this.initialDelaySeconds = initialDelay;
    }
    /** delay between scans of actionDirectory for new files */
    protected int delaySeconds = 30;
    public int getDelaySeconds() {
        return delaySeconds;
    }
    public void setDelaySeconds(int delay) {
        this.delaySeconds = delay;
    }
    
    protected ConfigPath actionDir = 
        new ConfigPath("ActionDirectory source directory","action");
    public ConfigPath getActionDir() {
        return actionDir;
    }
    public void setActionDir(ConfigPath actionDir) {
        this.actionDir = actionDir;
    }
    
    protected ConfigPath doneDir = 
        new ConfigPath("ActionDirectory done directory","${launchId}/actions-done");
    public ConfigPath getDoneDir() {
        return doneDir;
    }
    public void setDoneDir(ConfigPath doneDir) {
        this.doneDir = doneDir;
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
        try {
            // create directories
            org.archive.util.FileUtils.ensureWriteableDirectory(getActionDir().getFile());
            org.archive.util.FileUtils.ensureWriteableDirectory(getDoneDir().getFile());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // start background executor
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(this, getInitialDelaySeconds(), getDelaySeconds(), TimeUnit.SECONDS);
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
        String timestamp = ArchiveUtils.get17DigitDate();
        
        if(corename.endsWith(".seeds")) {
            // import seeds
            getSeeds().actOn(actionFile); 
        } else if (corename.endsWith(".recover")) {
            // apply recovery-log
            boolean alsoScope = corename.endsWith(".s.recover");
            try {
                // consider-included all successes and explicit-includes...
                getFrontier().importRecoverFormat(actionFile, alsoScope, true, false, "F[si] ");
                // then retry all adds...
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
        } else if(!tryAsScript(actionFile,timestamp)) {   
            LOGGER.warning("action file ignored: "+actionFile);
        }
        
        // move file to 'done' area with timestamp prefix
        while(actionFile.exists()) {
            try {
                File doneFile = new File(doneDir.getFile(),timestamp+"."+actionFile.getName());
                FileUtils.moveFile(actionFile, doneFile);
                
                // attempt to symlink from action/done/ to done file
                File actionDoneDirFile = new File(actionDir.getFile(), "done");
                if (!actionDoneDirFile.equals(doneDir.getFile())) {
                    actionDoneDirFile.mkdirs();
                    File doneSymlinkFile = new File(actionDoneDirFile, doneFile.getName());
                    boolean success = FilesystemLinkMaker.makeSymbolicLink(doneFile.getPath(), doneSymlinkFile.getPath());
                    if (!success) {
                        LOGGER.warning("failed to create symlink from " + doneSymlinkFile + " to " + doneFile);
                    }
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE,"unable to move "+actionFile,e);
            }
        }
    }
    
    
    /** shared ScriptEngineManager */
    static ScriptEngineManager MANAGER = new ScriptEngineManager();

    /**
     * Try the actionFile as a script, deducing the proper scripting
     * language from its file extension. Return true if evaluation was
     * tried with a known script engine. 
     * 
     * Provides 'appCtx' and 'rawOut' to script for accessing crawl
     * and outputting text to a '.out' file paired with the 'done/' 
     * action file. If an exception occurs, it will be logged to an
     * '.ex' file alongside the script file in 'done/'. 
     * 
     * @param actionFile file to try
     * @param timestamp timestamp correlating out/ex files with done script
     * @return true if engine evaluation began (even if an error occurred)
     */
    protected boolean tryAsScript(File actionFile, String timestamp) {
        int i = actionFile.getName().lastIndexOf(".");
        if(i<0) {
            return false; 
        }
        
        // deduce language/engine from extension
        String extension = actionFile.getName().substring(i+1);
        ScriptEngine engine = MANAGER.getEngineByExtension(extension);
        if(engine==null) {
            return false; 
        }
        
        // prepare engine
        StringWriter rawString = new StringWriter(); 
        PrintWriter rawOut = new PrintWriter(rawString);
        Exception ex = null;
        engine.put("rawOut", rawOut);
        engine.put("appCtx",appCtx);
        
        // evaluate and record any exception
        try {
            String script = FileUtils.readFileToString(actionFile);
            engine.eval(script);
        } catch (IOException e) {
            ex = e;
        } catch (ScriptException e) {
            ex = e;
        } catch (RuntimeException e) {
            ex = e;
        } finally {
            engine.put("rawOut", null);
            engine.put("appCtx", null);
        }

        // report output/exception to files paired with script in done dir
        rawOut.flush();
        String allOut = rawString.toString();
        if(StringUtils.isNotBlank(allOut)) {
            File outFile = new File(doneDir.getFile(),timestamp+"."+actionFile.getName()+".out");
            try {
                FileUtils.writeStringToFile(outFile, rawString.toString());
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE,"problem during action file: "+actionFile,ioe);
            }
        }
        if(ex!=null) {
            File exFile = new File(doneDir.getFile(),timestamp+"."+actionFile.getName()+".exception");
            try {
                FileUtils.writeStringToFile(exFile, ex.toString());
            } catch (IOException ioe) {
                LOGGER.log(Level.SEVERE,"problem during action file: "+actionFile,ioe);
            }
        }
        
        return true;        
    }

}