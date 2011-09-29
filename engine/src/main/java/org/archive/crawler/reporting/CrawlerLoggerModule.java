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

package org.archive.crawler.reporting;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.httpclient.URIException;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.io.NonFatalErrorFormatter;
import org.archive.crawler.io.RuntimeErrorFormatter;
import org.archive.crawler.io.StatisticsLogFormatter;
import org.archive.crawler.io.UriErrorFormatter;
import org.archive.crawler.io.UriProcessingFormatter;
import org.archive.crawler.util.Logs;
import org.archive.io.GenerationFileHandler;
import org.archive.modules.SimpleFileLoggerProvider;
import org.archive.modules.extractor.UriErrorLoggerModule;
import org.archive.net.UURI;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

/**
 * Module providing all expected whole-crawl logging facilities
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class CrawlerLoggerModule 
    implements 
        UriErrorLoggerModule, Lifecycle, InitializingBean,
        Checkpointable, SimpleFileLoggerProvider, DisposableBean {
    private static final long serialVersionUID = 1L;

    protected ConfigPath path = new ConfigPath(Engine.LOGS_DIR_NAME,"${launchId}/logs"); 
    public ConfigPath getPath() {
        return path;
    }
    public void setPath(ConfigPath cp) {
        this.path.merge(cp);
    }

    /**
     * Whether to include the "extra info" field for each entry in crawl.log.
     * "Extra info" is arbitrary JSON. It is the last field of the log line.
     */
    boolean logExtraInfo = false;
    public boolean getLogExtraInfo() {
        return logExtraInfo;
    }
    public void setLogExtraInfo(boolean logExtraInfo) {
        this.logExtraInfo = logExtraInfo;
    }
    
    // manifest support
    /** abbreviation label for config files in manifest */
    public static final char MANIFEST_CONFIG_FILE = 'C';
    /** abbreviation label for report files in manifest */
    public static final char MANIFEST_REPORT_FILE = 'R';
    /** abbreviation label for log files in manifest */
    public static final char MANIFEST_LOG_FILE = 'L';
    
    // key log names
    private static final String LOGNAME_CRAWL = "crawl";
    private static final String LOGNAME_ALERTS = "alerts";
    private static final String LOGNAME_PROGRESS_STATISTICS =
        "progress-statistics";
    private static final String LOGNAME_URI_ERRORS = "uri-errors";
    private static final String LOGNAME_RUNTIME_ERRORS = "runtime-errors";
    private static final String LOGNAME_NONFATAL_ERRORS = "nonfatal-errors";


    protected ConfigPath crawlLogPath = 
        new ConfigPath(Logs.CRAWL.getFilename(),Logs.CRAWL.getFilename()); 
    public ConfigPath getCrawlLogPath() {
        return crawlLogPath;
    }
    public void setCrawlLogPath(ConfigPath cp) {
        this.crawlLogPath.merge(cp);
    }
    
    protected ConfigPath alertsLogPath = 
        new ConfigPath(Logs.ALERTS.getFilename(),Logs.ALERTS.getFilename()); 
    public ConfigPath getAlertsLogPath() {
        return alertsLogPath;
    }
    public void setAlertsLogPath(ConfigPath cp) {
        this.alertsLogPath.merge(cp);
    }
    
    protected ConfigPath progressLogPath = 
        new ConfigPath(Logs.PROGRESS_STATISTICS.getFilename(),Logs.PROGRESS_STATISTICS.getFilename()); 
    public ConfigPath getProgressLogPath() {
        return progressLogPath;
    }
    public void setProgressLogPath(ConfigPath cp) {
        this.progressLogPath.merge(cp);
    }
    
    protected ConfigPath uriErrorsLogPath = 
        new ConfigPath(Logs.URI_ERRORS.getFilename(),Logs.URI_ERRORS.getFilename()); 
    public ConfigPath getUriErrorsLogPath() {
        return uriErrorsLogPath;
    }
    public void setUriErrorsLogPath(ConfigPath cp) {
        this.uriErrorsLogPath.merge(cp);
    }
    
    protected ConfigPath runtimeErrorsLogPath = 
        new ConfigPath(Logs.RUNTIME_ERRORS.getFilename(),Logs.RUNTIME_ERRORS.getFilename()); 
    public ConfigPath getRuntimeErrorsLogPath() {
        return runtimeErrorsLogPath;
    }
    public void setRuntimeErrorsLogPath(ConfigPath cp) {
        this.runtimeErrorsLogPath.merge(cp);
    }
    
    protected ConfigPath nonfatalErrorsLogPath = 
        new ConfigPath(Logs.NONFATAL_ERRORS.getFilename(),Logs.NONFATAL_ERRORS.getFilename()); 
    public ConfigPath getNonfatalErrorsLogPath() {
        return nonfatalErrorsLogPath;
    }
    public void setNonfatalErrorsLogPath(ConfigPath cp) {
        this.nonfatalErrorsLogPath.merge(cp);
    }
    
    /** suffix to use on active logs */
//    public static final String CURRENT_LOG_SUFFIX = ".log";
    
    /**
     * Crawl progress logger.
     *
     * No exceptions.  Logs summary result of each url processing.
     */
    private transient Logger uriProcessing;

    /**
     * This logger contains unexpected runtime errors.
     *
     * Would contain errors trying to set up a job or failures inside
     * processors that they are not prepared to recover from.
     */
    private transient Logger runtimeErrors;

    /**
     * This logger is for job-scoped logging, specifically recoverable 
     * errors which happen and are handled within a particular processor.
     *
     * Examples would be socket timeouts, exceptions thrown by 
     * extractors, etc.
     */
    private transient Logger nonfatalErrors;

    /**
     * Special log for URI format problems, wherever they may occur.
     */
    private transient Logger uriErrors;

    /**
     * Statistics tracker writes here at regular intervals.
     */
    private transient Logger progressStats;

    /**
     * Record of fileHandlers established for loggers,
     * assisting file rotation.
     */
    transient private Map<Logger,FileHandler> fileHandlers;

    private StringBuffer manifest = new StringBuffer();
    
    private transient AlertThreadGroup atg;

    public CrawlerLoggerModule() {
        
    }
    
    public void start() {
        if(isRunning) {
            return; 
        }
        this.atg = AlertThreadGroup.current();
        try {
            FileUtils.ensureWriteableDirectory(getPath().getFile());
            setupLogs();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        isRunning = true; 
    }
    
    boolean isRunning = false; 
    public boolean isRunning() {
        return this.isRunning; 
    }
    
    public void stop() {
        isRunning = false; 
    }
    
    public void destroy() {
        closeLogFiles();
    }
    
    private void setupLogs() throws IOException {
        String logsPath = getPath().getFile().getAbsolutePath() + File.separatorChar;
        uriProcessing = Logger.getLogger(LOGNAME_CRAWL + "." + logsPath);
        runtimeErrors = Logger.getLogger(LOGNAME_RUNTIME_ERRORS + "." +
            logsPath);
        nonfatalErrors = Logger.getLogger(LOGNAME_NONFATAL_ERRORS + "." + logsPath);
        uriErrors = Logger.getLogger(LOGNAME_URI_ERRORS + "." + logsPath);
        progressStats = Logger.getLogger(LOGNAME_PROGRESS_STATISTICS + "." +
            logsPath);

        this.fileHandlers = new HashMap<Logger,FileHandler>();
        setupLogFile(uriProcessing,
            getCrawlLogPath().getFile().getAbsolutePath(),
            new UriProcessingFormatter(getLogExtraInfo()), true);

        setupLogFile(runtimeErrors,
            getRuntimeErrorsLogPath().getFile().getAbsolutePath(),
            new RuntimeErrorFormatter(getLogExtraInfo()), true);

        setupLogFile(nonfatalErrors,
            getNonfatalErrorsLogPath().getFile().getAbsolutePath(),
            new NonFatalErrorFormatter(getLogExtraInfo()), true);

        setupLogFile(uriErrors,
            getUriErrorsLogPath().getFile().getAbsolutePath(),
            new UriErrorFormatter(), true);

        setupLogFile(progressStats,
            getProgressLogPath().getFile().getAbsolutePath(),
            new StatisticsLogFormatter(), true);

        setupAlertLog(logsPath);
    }

    private void setupLogFile(Logger logger, String filename, Formatter f,
            boolean shouldManifest) throws IOException, SecurityException {
        logger.setLevel(Level.INFO); // set all standard loggers to INFO
        GenerationFileHandler fh = GenerationFileHandler.makeNew(filename, false,
            shouldManifest);
        fh.setFormatter(f);
        logger.addHandler(fh);
        addToManifest(filename, MANIFEST_LOG_FILE, shouldManifest);
        logger.setUseParentHandlers(false);
        this.fileHandlers.put(logger, fh);
    }
    
    public Logger setupSimpleLog(String logName) {
        Logger logger = Logger.getLogger(logName + ".log");
        
        Formatter f = new Formatter() {
            public String format(java.util.logging.LogRecord record) {
                return ArchiveUtils.getLog17Date(record.getMillis()) + " " + record.getMessage() + '\n';
            }
        };

        ConfigPath logPath = new ConfigPath(logName + ".log", logName + ".log");
        logPath.setBase(getPath());
        try {
            setupLogFile(logger, logPath.getFile().getAbsolutePath(), f, true);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        
        return logger;
    }

    private void setupAlertLog(String logsPath) throws IOException {
        Logger logger = Logger.getLogger(LOGNAME_ALERTS + "." + logsPath);
        String filename = getAlertsLogPath().getFile().getAbsolutePath();
        GenerationFileHandler fh = 
            GenerationFileHandler.makeNew(filename, false, true);
        fh.setFormatter(new SimpleFormatter());
        AlertThreadGroup.current().addLogger(logger);
        AlertHandler.ensureStaticInitialization(); 
        logger.addHandler(fh);
        addToManifest(filename, MANIFEST_LOG_FILE, true);
        logger.setUseParentHandlers(false);
        this.fileHandlers.put(logger, fh);  
    }

    
    public void rotateLogFiles() throws IOException {
        rotateLogFiles("." + ArchiveUtils.get14DigitDate());
    }
    
    protected void rotateLogFiles(String generationSuffix)
    throws IOException {
        for (Logger l: fileHandlers.keySet()) {
            GenerationFileHandler gfh =
                (GenerationFileHandler)fileHandlers.get(l);
            GenerationFileHandler newGfh =
                gfh.rotate(generationSuffix, "");
            if (gfh.shouldManifest()) {
                addToManifest((String) newGfh.getFilenameSeries().get(1),
                    MANIFEST_LOG_FILE, newGfh.shouldManifest());
            }
            l.removeHandler(gfh);
            l.addHandler(newGfh);
            fileHandlers.put(l, newGfh);
        }
    }

    /**
     * Close all log files and remove handlers from loggers.
     */
    public void closeLogFiles() {
        if (fileHandlers != null) {
            for (Logger l: fileHandlers.keySet()) {
                GenerationFileHandler gfh =
                        (GenerationFileHandler)fileHandlers.get(l);
                gfh.close();
                l.removeHandler(gfh);
            }
        }
    }

    
    /**
     * Add a file to the manifest of files used/generated by the current
     * crawl.
     * 
     * TODO: Its possible for a file to be added twice if reports are
     * force generated midcrawl.  Fix.
     *
     * @param file The filename (with absolute path) of the file to add
     * @param type The type of the file
     * @param bundle Should the file be included in a typical bundling of
     *           crawler files.
     *
     * @see #MANIFEST_CONFIG_FILE
     * @see #MANIFEST_LOG_FILE
     * @see #MANIFEST_REPORT_FILE
     */
    public void addToManifest(String file, char type, boolean bundle) {
        manifest.append(type + (bundle? "+": "-") + " " + file + "\n");
    }
    
    public void startCheckpoint(Checkpoint checkpointInProgress) {}

    /**
     * Run checkpointing.
     * 
     * <p>Default access only to be called by Checkpointer.
     * @throws Exception
     */
    public void doCheckpoint(Checkpoint checkpointInProgress) throws IOException {
        // Rotate off crawler logs.
        rotateLogFiles("." + checkpointInProgress.getName());
    }

    public void finishCheckpoint(Checkpoint checkpointInProgress) {}

    Checkpoint recoveryCheckpoint;
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint checkpoint) {
        this.recoveryCheckpoint = checkpoint; 
    }
    
    public Logger getNonfatalErrors() {
        return nonfatalErrors;
    }


    public Logger getProgressStats() {
        return progressStats;
    }

    public Logger getRuntimeErrors() {
        return runtimeErrors;
    }


    public Logger getUriErrors() {
        return uriErrors;
    }


    public Logger getUriProcessing() {
        return uriProcessing;
    }

    
    public int getAlertCount() {
        if (atg != null) {
            return atg.getAlertCount();
        } else {
            return -1;
        }
    }
    
    
    public void resetAlertCount() {
        if (atg != null) {
            atg.resetAlertCount();
        }
    }


    /**
     * Log a URIException from deep inside other components to the crawl's
     * shared log.
     *
     * @param e URIException encountered
     * @param u CrawlURI where problem occurred
     * @param l String which could not be interpreted as URI without exception
     */
    public void logUriError(URIException e, UURI u, CharSequence l) {
        Object[] array = {u, l};
        uriErrors.log(Level.INFO, e.getMessage(), array);
    }
    
    
    private void readObject(ObjectInputStream in) 
    throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        getPath().getFile().mkdirs();
        this.atg = AlertThreadGroup.current();
        this.setupLogs();
    }
    
    
    public void afterPropertiesSet() throws Exception {
        ConfigPath[] paths = { 
                crawlLogPath, alertsLogPath, progressLogPath, 
                uriErrorsLogPath, runtimeErrorsLogPath, nonfatalErrorsLogPath };
        for(ConfigPath cp : paths) {
            if(cp.getBase()==null) {
                cp.setBase(getPath());
            }
        }
    }
}
