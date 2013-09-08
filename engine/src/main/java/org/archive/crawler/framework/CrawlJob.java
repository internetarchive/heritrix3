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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections.ListUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.framework.CrawlController.StopCompleteEvent;
import org.archive.crawler.reporting.AlertThreadGroup;
import org.archive.crawler.reporting.CrawlStatSnapshot;
import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.spring.ConfigPath;
import org.archive.spring.ConfigPathConfigurer;
import org.archive.spring.PathSharingContext;
import org.archive.util.ArchiveUtils;
import org.archive.util.TextUtils;
import org.joda.time.DateTime;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.validation.Errors;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * CrawlJob represents a crawl configuration, including its 
 * configuration files, instantiated/running ApplicationContext, and 
 * disk output, potentially across multiple runs.
 * 
 * CrawlJob provides convenience methods for an administrative 
 * interface to assemble, launch, monitor, and manage crawls. 
 * 
 * @contributor gojomo
 */
public class CrawlJob implements Comparable<CrawlJob>, ApplicationListener<ApplicationEvent> {
    private final static Logger LOGGER =
        Logger.getLogger(CrawlJob.class.getName());

    protected File primaryConfig; 
    protected PathSharingContext ac; 
    protected int launchCount; 
    protected boolean isLaunchInfoPartial;
    protected DateTime lastLaunch;
    protected AlertThreadGroup alertThreadGroup;
    
    protected DateTime xmlOkAt = new DateTime(0L);
    protected Logger jobLogger;
    
    public CrawlJob(File cxml) {
        primaryConfig = cxml; 
        isLaunchInfoPartial = false;
        scanJobLog(); // XXX look at launch directories instead/first? 
    }
    
    public File getPrimaryConfig() {
        return primaryConfig;
    }
    public File getJobDir() {
        return getPrimaryConfig().getParentFile();
    }
    public String getShortName() {
        return getJobDir().getName();
    }
    public File getJobLog() {
        return new File(getJobDir(),"job.log");
    }
    
    public synchronized PathSharingContext getJobContext() {
        return ac; 
    }

    public boolean isLaunchInfoPartial() {
        return isLaunchInfoPartial;
    }
    
    /**
     * Get a logger to a distinguished file, job.log in the job's
     * directory, into which job-specific events may be reported.
     * 
     * @return Logger writing to the job-specific log
     */
    public Logger getJobLogger() {
        if(jobLogger == null) {
            jobLogger = Logger.getLogger(getShortName());
            try {
                Handler h = new FileHandler(getJobLog().getAbsolutePath(),true);
                h.setFormatter(new JobLogFormatter());
                jobLogger.addHandler(h);
            } catch (SecurityException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            jobLogger.setLevel(Level.INFO);
        }
        return jobLogger;
    }
    
    public DateTime getLastLaunch() {
        return lastLaunch;
    }
    public int getLaunchCount() {
        return launchCount;
    }
    /**
     * Refresh knowledge of total launched and last launch by scanning
     * the job.log. 
     */
    protected void scanJobLog() {
        File jobLog = getJobLog();
        launchCount = 0; 
        if(!jobLog.exists()) return;
        
        try {
            Pattern launchLine = Pattern.compile("(\\S+) (\\S+) Job launched");
            long startPosition = 0; 
            if (jobLog.length() > FileUtils.ONE_KB * 100) {
                isLaunchInfoPartial = true;
                startPosition = jobLog.length()-(FileUtils.ONE_KB * 100);
            }
            FileInputStream jobLogIn = new FileInputStream(jobLog);
            jobLogIn.getChannel().position(startPosition);
            BufferedReader jobLogReader = new BufferedReader(
                    new InputStreamReader(jobLogIn));
            String line;
            while ((line = jobLogReader.readLine()) != null) {
                Matcher m = launchLine.matcher(line);
                if (m.matches()) {
                    launchCount++;
                    lastLaunch = new DateTime(m.group(1));
                }
            }
            jobLogReader.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    
    /**
     * Is this job a 'profile' (or template), meaning it may be editted
     * or copied to another jobs, but should not be launched. Profiles
     * are marked with the convention that their short name 
     * (job directory name) begins "profile-".
     * 
     * @return true if this job is a 'profile'
     */
    public boolean isProfile() {
        return primaryConfig.getName().startsWith("profile-");
    }

    //
    // writing a basic HTML representation
    //

    public void writeHtmlTo(PrintWriter pw) {
        writeHtmlTo(pw,"./");
    }
    public void writeHtmlTo(PrintWriter pw, String uriPrefix) {
        pw.println("<div>");
        pw.println("<a href='"+uriPrefix+TextUtils.urlEscape(getShortName())+"'>"+getShortName()+"</a>");
        if(isProfile()) {
            pw.println("(profile)");
        }
        if(hasApplicationContext()) {
            pw.println("&laquo;"+getJobStatusDescription()+"&raquo;");
        }
        if (true == isLaunchInfoPartial) {
            pw.print(" at least ");
        } else {
            pw.print(" ");
        }
        pw.println(getLaunchCount() + " launches");
        pw.println("</div>");
        pw.println("<div style='color:#666'>");
        pw.println(getPrimaryConfig());
        pw.println("</div>");
        if(lastLaunch!=null) {
            pw.println("<div>(last at "+lastLaunch+")</div>");
        }
    }

    /**
     * Is the primary XML config minimally well-formed? 
     */
    public void checkXML() {
        // TODO: suppress check if XML unchanged? job.log when XML changed? 

        DateTime testTime = new DateTime(getPrimaryConfig().lastModified());
        Document doc = getDomDocument(getPrimaryConfig());
        // TODO: check for other minimal requirements, like
        // presence of a few key components (CrawlController etc.)? 
        if(doc!=null) {
            xmlOkAt = testTime; 
        } else {
            xmlOkAt = new DateTime(0L);
        }

    }

    /**
     * Read a file to a DOM Document; return null if this isn't possible
     * for any reason.
     * 
     * @param f File of XML
     * @return org.w3c.dom.Document or null if problems encountered
     */
    protected Document getDomDocument(File f) {
        try {
            DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
            return docBuilder.parse(f);
        } catch (ParserConfigurationException e) {
            return null; 
        } catch (SAXException e) {
            return null; 
        } catch (IOException e) {
            return null; 
        }
    }
    
    /**
     * Is the primary config file legal XML?
     * 
     * @return true if the primary configuration file passed XML testing
     */
    public boolean isXmlOk() {
        return xmlOkAt.getMillis() >= getPrimaryConfig().lastModified();
    }
    
    
    /**
     * Can the configuration yield an assembled ApplicationContext? 
     */
    public synchronized void instantiateContainer() {
        checkXML(); 
        if(ac==null) {
            try {
                ac = new PathSharingContext(new String[] {primaryConfig.toURI().toString()}, false, null);
                ac.addApplicationListener(this);
                ac.refresh();
                getCrawlController(); // trigger NoSuchBeanDefinitionException if no CC
                getJobLogger().log(Level.INFO,"Job instantiated");
            } catch (BeansException be) {
                // Calling doTeardown() and therefore ac.close() here sometimes
                // triggers an IllegalStateException and logs stack trace from
                // within spring, even if ac.isActive(). So, just null it.
                ac = null;
                beansException(be);
            }
        }
    }
    
    /**
     * Report a BeansException during instantiation; report chain in 
     * reverse order (so root cause is first); ignore non-BeansExceptions
     * or messages without a useful compact message. 
     * @param be BeansException
     */
    protected void beansException(BeansException be) {
        LinkedList<String> beMsgs = new LinkedList<String>();
        Throwable t = be; 
        while (t!=null) {
            if(t instanceof BeansException) {
                String msg = shortMessage((BeansException)t);
                if(msg!=null) {
                    beMsgs.add(msg);
                }
            }
            t = t.getCause();
        }
        Collections.reverse(beMsgs);
        String shortMessage = StringUtils.join(beMsgs,"; ");
        
        getJobLogger().log(Level.SEVERE,shortMessage,be);
    }
    
    /**
     * Return a short useful message for common BeansExceptions. 
     * @param ex BeansException
     * @return String short descriptive message
     */
    protected String shortMessage(BeansException ex) {
        if(ex instanceof NoSuchBeanDefinitionException) {
            NoSuchBeanDefinitionException nsbde = (NoSuchBeanDefinitionException)ex;
            return "Missing required bean: "
                + (nsbde.getBeanName()!=null ? "\""+nsbde.getBeanName()+"\" " : "")
                + (nsbde.getBeanType()!=null ? "\""+nsbde.getBeanType()+"\" " : "");
        }
        if(ex instanceof BeanCreationException) {
            BeanCreationException bce = (BeanCreationException)ex;
            return bce.getBeanName()== null 
                    ? ""
                    : "Can't create bean '"+bce.getBeanName()+"'";
        }
        return ex.getMessage().replace('\n', ' ');
    }

    public synchronized boolean hasApplicationContext() {
        return ac!=null;
    }
    
    /**
     * Does the assembled ApplicationContext self-validate? Any failures
     * are reported as WARNING log events in the job log. 
     * 
     * TODO: make these severe? 
     */
    public synchronized void validateConfiguration() {
        instantiateContainer();
        if(ac==null) {
            // fatal errors already encountered and reported
            return; 
        }
        ac.validate();
        HashMap<String,Errors> allErrors = ac.getAllErrors();
        for(String name : allErrors.keySet()) {
            for(Object err : allErrors.get(name).getAllErrors()) {
               LOGGER.log(Level.WARNING,err.toString());
            }
        }
    }

    /**
     * Did the ApplicationContext self-validate? 
     * return true if validation passed without errors
     */
    public synchronized boolean hasValidApplicationContext() {
        if(ac==null) {
            return false;
        }
        HashMap<String,Errors> allErrors = ac.getAllErrors();
        return allErrors != null && allErrors.isEmpty();
    }
    
    //
    // Valid job lifecycle operations
    //
    
    /**
     * Launch a crawl into 'running' status, assembling if necessary. 
     * 
     * (Note the crawl may have been configured to start in a 'paused'
     * state.) 
     */
    public synchronized void launch() {
        if (isProfile()) {
            throw new IllegalArgumentException("Can't launch profile" + this);
        }
        
        if(isRunning()) {
            getJobLogger().log(Level.SEVERE,"Can't relaunch running job");
            return;
        } else {
            CrawlController cc = getCrawlController();
            if(cc!=null && cc.hasStarted()) {
                getJobLogger().log(Level.SEVERE,"Can't relaunch previously-launched assembled job");
                return;
            }
        }
        
        validateConfiguration();
        if(!hasValidApplicationContext()) {
            getJobLogger().log(Level.SEVERE,"Can't launch problem configuration");
            return;
        }

        //final String job = changeState(j, ACTIVE);
        
        // this temporary thread ensures all crawl-created threads
        // land in the AlertThreadGroup, to assist crawl-wide 
        // logging/alerting
        alertThreadGroup = new AlertThreadGroup(getShortName());
        alertThreadGroup.addLogger(getJobLogger());
        Thread launcher = new Thread(alertThreadGroup, getShortName()+" launchthread") {
            public void run() {
                CrawlController cc = getCrawlController();
                startContext();
                if(cc!=null) {
                    cc.requestCrawlStart();
                }
            }
        };
        getJobLogger().log(Level.INFO,"Job launched");
        scanJobLog();
        launcher.start();
        // look busy (and give startContext/crawlStart a chance)
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            // do nothing
        }
    }
    
    protected transient Handler currentLaunchJobLogHandler;

    protected boolean needTeardown = false;

    /**
     * Start the context, catching and reporting any BeansExceptions.
     */
    protected synchronized void startContext() {
        try {
            ac.start(); 
            
            // job log file covering just this launch
            getJobLogger().removeHandler(currentLaunchJobLogHandler);
            File f = new File(ac.getCurrentLaunchDir(), "job.log");
            currentLaunchJobLogHandler = new FileHandler(f.getAbsolutePath(), true);
            currentLaunchJobLogHandler.setFormatter(new JobLogFormatter());
            getJobLogger().addHandler(currentLaunchJobLogHandler);
            
        } catch (BeansException be) {
            doTeardown();
            beansException(be);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,e.getClass().getSimpleName()+": "+e.getMessage(),e);
            try {
                doTeardown();
            } catch (Exception e2) {
                e2.printStackTrace(System.err);
            }        
        }
    }

    /** 
     * Sort for reverse-chronological listing.
     * 
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(CrawlJob o) {
        // prefer reverse-chronological ordering
        return -((Long)getLastActivityTime()).compareTo(o.getLastActivityTime());
    }
    
    public long getLastActivityTime() {
        return Math.max(getPrimaryConfig().lastModified(), getJobLog().lastModified());
    }
    
    public synchronized boolean isRunning() {
        return this.ac != null && this.ac.isActive() && this.ac.isRunning();
    }

    public synchronized CrawlController getCrawlController() {
        if(ac==null) {
            return null;
        }
        return (CrawlController) ac.getBean("crawlController");
    }

    public boolean isPausable() {
        CrawlController cc = getCrawlController(); 
        if(cc==null) {
            return false;
        }
        return cc.isActive(); 
    }
    
    public boolean isUnpausable() {
        CrawlController cc = getCrawlController(); 
        if(cc==null) {
            return false;
        }
        return cc.isPaused() || cc.isPausing();
    }
    
    /**
     * Return the configured Checkpointer instance, if there is exactly
     * one, otherwise null.
     * 
     * @return Checkpointer
     */
    public synchronized CheckpointService getCheckpointService() {
        if(ac==null) {
            return null;
        }
        Map<String, CheckpointService> beans = 
            getJobContext().getBeansOfType(CheckpointService.class);
        return (beans.size() == 1) ? beans.values().iterator().next() : null;
    }
    /**
     * Ensure a fresh start for any configuration changes or relaunches,
     * by stopping and discarding an existing ApplicationContext.
     * 
     * @return true if teardown is complete when method returns, false if still in progress
     */
    public synchronized boolean teardown() {
        CrawlController cc = getCrawlController();
        if (cc != null) {
            cc.requestCrawlStop();
            needTeardown = true;
            
            // wait up to 3 seconds for stop
            for(int i = 0; i < 11; i++) {
                if(cc.isStopComplete()) {
                    break;
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
            
            if (cc.isStopComplete()) {
                doTeardown();
            }
        }
        
        assert needTeardown == (ac != null);
        return !needTeardown; 
    }

    // ac guaranteed to be null after this method is called
    protected synchronized void doTeardown() {
        needTeardown = false;

        try {
            if (ac != null) { 
                ac.close();
            }
        } finally {
            // all this stuff should happen even in case ac.close() bugs out
            ac = null;
            
            xmlOkAt = new DateTime(0);
            
            if (currentLaunchJobLogHandler != null) {
                getJobLogger().removeHandler(currentLaunchJobLogHandler);
                currentLaunchJobLogHandler.close();
                currentLaunchJobLogHandler = null;
            }

            getJobLogger().log(Level.INFO,"Job instance discarded");
        }
    }

    /**
     * Formatter for job.log
     */
    public class JobLogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb
              .append(new DateTime(record.getMillis()))
              .append(" ")
              .append(record.getLevel())
              .append(" ")
              .append(record.getMessage())
              .append("\n");
            return  sb.toString();
        }
    }

    /**
     * Return all config files included via 'import' statements in the
     * primary config (or other included configs). 
     * 
     * @param xml File to examine
     * @return List<File> of all transitively-imported Files
     */
    @SuppressWarnings("unchecked")
    public List<File> getImportedConfigs(File xml) {
        List<File> imports = new LinkedList<File>(); 
        Document doc = getDomDocument(xml);
        if(doc==null) {
            return ListUtils.EMPTY_LIST;
        }
        NodeList importElements = doc.getElementsByTagName("import");
        for(int i = 0; i < importElements.getLength(); i++) {
            File imported = new File(
                    getJobDir(),
                    importElements.item(i).getAttributes().getNamedItem("resource").getTextContent());
            imports.add(imported);
            imports.addAll(getImportedConfigs(imported));
        }
        return imports; 
    }
    
    /**
     * Return all known ConfigPaths, as an aid to viewing or editting. 
     * 
     * @return all ConfigPaths known to the ApplicationContext, in a 
     * map by name, or an empty map if no ApplicationContext
     */
    @SuppressWarnings("unchecked")
    public synchronized Map<String, ConfigPath> getConfigPaths() {
        if(ac==null) {
            return MapUtils.EMPTY_MAP;
        }
        ConfigPathConfigurer cpc = 
            (ConfigPathConfigurer)ac.getBean("configPathConfigurer");
        return cpc.getAllConfigPaths();        
    }

    /**
     * Compute a path relative to the job directory for all contained 
     * files, or null if the File is not inside the job directory. 
     * 
     * @param f File
     * @return path relative to the job directory, or null if File not 
     * inside job dir
     */
    public String jobDirRelativePath(File f) {
        try {
            String filePath = f.getCanonicalPath();
            String jobPath = getJobDir().getCanonicalPath();
            if(filePath.startsWith(jobPath)) {
                String jobRelative = filePath.substring(jobPath.length()).replace(File.separatorChar, '/');
                if(jobRelative.startsWith("/")) {
                    jobRelative = jobRelative.substring(1); 
                }
                return jobRelative;
            }
        } catch (IOException e) {
            getJobLogger().log(Level.WARNING,"bad file: "+f);
        }
        return null; 
    }

    /** 
     * Log note of all ApplicationEvents.
     * 
     * @see org.springframework.context.ApplicationListener#onApplicationEvent(org.springframework.context.ApplicationEvent)
     */
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof CrawlStateEvent) {
            getJobLogger().log(Level.INFO, ((CrawlStateEvent)event).getState() + 
                    (ac.getCurrentLaunchId() != null ? " " + ac.getCurrentLaunchId() : ""));
        }
        
        if (event instanceof StopCompleteEvent) {
            synchronized (this) {
                if (needTeardown) {
                    doTeardown();
                }
            }
        }
        
        if(event instanceof CheckpointSuccessEvent) {
            getJobLogger().log(Level.INFO, "CHECKPOINTED "+((CheckpointSuccessEvent)event).getCheckpoint().getName());
        }
    }

    /**
     * Is it reasonable to offer a launch button
     * @return true if launchable
     */
    public boolean isLaunchable() {
        if (!hasApplicationContext()) {
            // ok to try launch if not yet built
            return true; 
        }
        if (!hasValidApplicationContext()) {
            // never launch if specifically invalid
            return false;
        }
        // launchable if cc not yet instantiated or not yet started
        CrawlController cc = getCrawlController();        
        return cc == null || !cc.hasStarted();
    }

    public int getAlertCount() {
        if (alertThreadGroup != null) {
            return alertThreadGroup.getAlertCount();
        } else {
            return 0;
        }
    }
    
    protected StatisticsTracker getStats() {
        CrawlController cc = getCrawlController();
        return cc!=null ? cc.getStatisticsTracker() : null;
    }

    public Map<String,Number> rateReportData() {
        StatisticsTracker stats = getStats();
        if (stats == null) {
            return null;
        }
        
        CrawlStatSnapshot snapshot = stats.getSnapshot();
        Map<String,Number> map = new LinkedHashMap<String,Number>();
        map.put("currentDocsPerSecond", snapshot.currentDocsPerSecond);
        map.put("averageDocsPerSecond", snapshot.docsPerSecond);
        map.put("currentKiBPerSec", snapshot.currentKiBPerSec);
        map.put("averageKiBPerSec", snapshot.totalKiBPerSec);
        return map;
    }

    public Object rateReport() {
        StatisticsTracker stats = getStats();
        if(stats==null) {
            return "<i>n/a</i>";
        }
        CrawlStatSnapshot snapshot = stats.getSnapshot();
        StringBuilder sb = new StringBuilder();
        sb
         .append(ArchiveUtils.doubleToString(snapshot.currentDocsPerSecond,2))
         .append(" URIs/sec (")
         .append(ArchiveUtils.doubleToString(snapshot.docsPerSecond,2))
         .append(" avg); ")
         .append(snapshot.currentKiBPerSec)
         .append(" KB/sec (")
         .append(snapshot.totalKiBPerSec)
         .append(" avg)");
        return sb.toString();
    }

    public Map<String,Number> loadReportData() {
        StatisticsTracker stats = getStats();
        if (stats == null) {
            return null;
        }
        
        CrawlStatSnapshot snapshot = stats.getSnapshot();
        Map<String,Number> map = new LinkedHashMap<String,Number>();
        
        map.put("busyThreads", snapshot.busyThreads);
        map.put("totalThreads", stats.threadCount());
        map.put("congestionRatio", snapshot.congestionRatio);
        map.put("averageQueueDepth", snapshot.averageDepth);
        map.put("deepestQueueDepth", snapshot.deepestUri);
        return map;
    }

    public Object loadReport() {
        StatisticsTracker stats = getStats();
        if(stats==null) {
            return "<i>n/a</i>";
        }
        CrawlStatSnapshot snapshot = stats.getSnapshot();
        StringBuilder sb = new StringBuilder();
        sb
         .append(snapshot.busyThreads)
         .append(" active of ")
         .append(stats.threadCount())
         .append(" threads; ")
         .append(ArchiveUtils.doubleToString(snapshot.congestionRatio,2))
         .append(" congestion ratio; ")
         .append(snapshot.deepestUri)
         .append("  deepest queue; ")
         .append(snapshot.averageDepth)
         .append("  average depth");
        return sb.toString();
    }

    public Map<String,Long> uriTotalsReportData() {
        StatisticsTracker stats = getStats();
        if (stats == null) {
            return null;
        }

        CrawlStatSnapshot snapshot = stats.getSnapshot();

        Map<String,Long> totals = new LinkedHashMap<String,Long>();
        totals.put("downloadedUriCount", snapshot.downloadedUriCount);
        totals.put("queuedUriCount", snapshot.queuedUriCount);
        totals.put("totalUriCount", snapshot.totalCount());
        totals.put("futureUriCount", snapshot.futureUriCount);

        return totals;
    }
    
    public String uriTotalsReport() {
        Map<String,Long> uriTotals = uriTotalsReportData();
        if (uriTotals == null) {
            return "<i>n/a</i>";
        }

        StringBuilder sb = new StringBuilder(64); 
        sb
         .append(uriTotals.get("downloadedUriCount"))
         .append(" downloaded + ")
         .append(uriTotals.get("queuedUriCount"))
         .append(" queued = ")
         .append(uriTotals.get("totalUriCount"))
         .append(" total");
        if(uriTotals.get("futureUriCount") >0) {
            sb
             .append(" (")
             .append(uriTotals.get("futureUriCount"))
             .append(" future)");
        }
        return sb.toString(); 
    }

    public Map<String,Long> sizeTotalsReportData() {
        StatisticsTracker stats = getStats();
        if(stats==null) {
            return null;
        }
        
        // stats.crawledBytesSummary() also includes totals, so add those in here
        TreeMap<String, Long> map = new TreeMap<String,Long>(stats.getCrawledBytes());
        map.put("total", stats.getCrawledBytes().getTotalBytes());
        map.put("totalCount", stats.getCrawledBytes().getTotalUrls());
        return map;
    }

    public String sizeTotalsReport() {
        StatisticsTracker stats = getStats();
        if(stats==null) {
            return "<i>n/a</i>";
        }
        return stats.crawledBytesSummary();
    }

    public Map<String,Object> elapsedReportData() {
        StatisticsTracker stats = getStats();
        if(stats==null) {
            return null;
        }
        
        Map<String,Object> map = new LinkedHashMap<String,Object>();
        long timeElapsed = stats.getCrawlElapsedTime();
        map.put("elapsedMilliseconds", timeElapsed);
        map.put("elapsedPretty", ArchiveUtils.formatMillisecondsToConventional(timeElapsed));
        
        return map;
    }

    public String elapsedReport() {
        StatisticsTracker stats = getStats();
        if(stats==null) {
            return "<i>n/a</i>";
        }
        long timeElapsed = stats.getCrawlElapsedTime();
        return ArchiveUtils.formatMillisecondsToConventional(timeElapsed);
    }

    public Map<String,Object> threadReportData() {
        CrawlController cc = getCrawlController();
        if (cc == null) {
            return null;
        }
        return cc.getToeThreadReportShortData();
    }

    public String threadReport() {
        CrawlController cc = getCrawlController();
        if(cc==null) {
            return "<i>n/a</i>";
        }
        return cc.getToeThreadReportShort();
    }

    public Map<String,Object> frontierReportData() {
        CrawlController cc = getCrawlController();
        if (cc == null) {
            return null;
        }
        return cc.getFrontier().shortReportMap();
    }

    public String frontierReport() {
        CrawlController cc = getCrawlController();
        if(cc==null) {
            return "<i>n/a</i>";
        }
        return cc.getFrontierReportShort();
    }

    public void terminate() {
        if (getCrawlController() != null) {
            getCrawlController().requestCrawlStop();
        }
    }

    /**
     * Utility method for getting a bean or any other object addressable
     * with a 'bean path' -- a property-path string (with dots and 
     * []indexes) starting with a bean name.
     * 
     * TODO: move elsewhere? on the appContext? a util class?
     * 
     * @param beanPath String 'property-path' with bean name as first segment
     * @return Object targeted by beanPath, or null if nont
     */
    public Object getBeanpathTarget(String beanPath) {
        try {
            int i = beanPath.indexOf(".");
            String beanName = i<0?beanPath:beanPath.substring(0,i);
            Object namedBean = ac.getBean(beanName);
            if (i<0) {
                return namedBean;
            } else {
                BeanWrapperImpl bwrap = new BeanWrapperImpl(namedBean);
                String propPath = beanPath.substring(i+1);
                return bwrap.getPropertyValue(propPath);
            }       
        } catch (BeansException e) {
            return null;
        }
    }
    
    public String getJobStatusDescription() {
        if(!hasApplicationContext()) {
            return "Unbuilt";
        } else if(isRunning()) {
            return "Active: "+getCrawlController().getState();
        } else if(isLaunchable()){
            return "Ready";
        } else {
            return "Finished: "+getCrawlController().getCrawlExitStatus();
        }
    }
}//EOC
