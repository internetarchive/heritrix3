package org.archive.crawler.restlet.models;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.archive.checkpointing.Checkpoint;
import org.archive.crawler.framework.CrawlController.State;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.reporting.Report;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;

@SuppressWarnings("serial")
public class CrawlJobModel extends LinkedHashMap<String, Object> implements Serializable{

    private CrawlJob crawlJob;
    public static final IOFileFilter EDIT_FILTER = FileUtils
            .getRegexFileFilter(".*\\.((c?xml)|(txt))$");
    
    public CrawlJobModel(CrawlJob crawlJob, String urlBaseRef){
        super();
        this.crawlJob=crawlJob;
        this.put("shortName",crawlJob.getShortName());
        if (crawlJob.getCrawlController() != null) {
            this.put("crawlControllerState", crawlJob.getCrawlController().getState());
            if (crawlJob.getCrawlController().getState() == State.FINISHED) {
                this.put("crawlExitStatus", crawlJob.getCrawlController().getCrawlExitStatus());
            }
        }

        this.put("statusDescription", crawlJob.getJobStatusDescription());
        Set<String> actions = new LinkedHashSet<String>();
        this.put("availableActions",actions);
    
        this.put("launchCount", crawlJob.getLaunchCount());
        this.put("lastLaunch",crawlJob.getLastLaunch());
        this.put("isProfile", crawlJob.isProfile());

        File primaryConfig = FileUtils.tryToCanonicalize(crawlJob.getPrimaryConfig());
        this.put("primaryConfig", primaryConfig.getAbsolutePath());
        this.put("primaryConfigUrl", urlBaseRef + "jobdir/" + primaryConfig.getName());
        this.put("url",urlBaseRef+"job/"+crawlJob.getShortName());

        this.put("jobLogTail", generateJobLogTail());
        this.put("uriTotalsReport", crawlJob.uriTotalsReportData());
        
        
        Map<String, Long> sizeTotalsReportData = crawlJob.sizeTotalsReportData();
        if (sizeTotalsReportData == null) {
            sizeTotalsReportData = new LinkedHashMap<String, Long>();
        }
        if (!sizeTotalsReportData.containsKey("dupByHash")) {
            sizeTotalsReportData.put("dupByHash", 0L);
        }
        if (!sizeTotalsReportData.containsKey("dupByHashCount")) {
            sizeTotalsReportData.put("dupByHashCount", 0L);
        }
        if (!sizeTotalsReportData.containsKey("novel")) {
            sizeTotalsReportData.put("novel", 0L);
        }
        if (!sizeTotalsReportData.containsKey("novelCount")) {
            sizeTotalsReportData.put("novelCount", 0L);
        }
        if (!sizeTotalsReportData.containsKey("notModified")) {
            sizeTotalsReportData.put("notModified", 0L);
        }
        if (!sizeTotalsReportData.containsKey("notModifiedCount")) {
            sizeTotalsReportData.put("notModifiedCount", 0L);
        }
        if (!sizeTotalsReportData.containsKey("total")) {
            sizeTotalsReportData.put("total", 0L);
        }
        if (!sizeTotalsReportData.containsKey("totalCount")) {
            sizeTotalsReportData.put("totalCount", 0L);
        }
        this.put("sizeTotalsReport", sizeTotalsReportData);
        
        this.put("rateReport", crawlJob.rateReportData());
        this.put("loadReport", crawlJob.loadReportData());
        this.put("elapsedReport", crawlJob.elapsedReportData()); 
        this.put("threadReport", crawlJob.threadReportData()); 
        this.put("frontierReport", crawlJob.frontierReportData());
        this.put("crawlLogTail", generateCrawlLogTail());
        this.put("configFiles",generateConfigReferencedPaths(urlBaseRef));


        this.put("isLaunchInfoPartial", crawlJob.isLaunchInfoPartial());
        this.put("isRunning", crawlJob.isRunning());
        this.put("isLaunchable",crawlJob.isLaunchable());
        this.put("hasApplicationContext",crawlJob.hasApplicationContext());
        this.put("alertCount", crawlJob.getAlertCount());        

        
        if (!crawlJob.hasApplicationContext())
            actions.add("build");

        if (!crawlJob.isProfile() && crawlJob.isLaunchable())
            actions.add("launch");
        if (crawlJob.isPausable())
            actions.add("pause");
        if (crawlJob.isUnpausable())
            actions.add("unpause");

        if (crawlJob.getCheckpointService() != null && crawlJob.isRunning())
            actions.add("checkpoint");
        if (crawlJob.isRunning())
            actions.add("terminate");
        if (crawlJob.hasApplicationContext())
            actions.add("teardown");

        if (crawlJob.getCheckpointService() != null) {
            Checkpoint recoveryCheckpoint = crawlJob.getCheckpointService().getRecoveryCheckpoint();
            if (recoveryCheckpoint != null)
                this.put("checkpointName", recoveryCheckpoint.getName());
        }
        
        List<String> checkpointFiles = new ArrayList<String>();
        if (crawlJob.getCheckpointService() != null) {
            if (crawlJob.getCheckpointService().hasAvailableCheckpoints() && crawlJob.isLaunchable()) {
                for (File f : crawlJob.getCheckpointService().findAvailableCheckpointDirectories()) {
                    checkpointFiles.add(f.getName()); 
                }
            }
        }
        this.put("checkpointFiles",checkpointFiles);
        if (crawlJob.hasApplicationContext())
            this.put("alertLogFilePath",crawlJob.getCrawlController().getLoggerModule().getAlertsLogPath().getFile().getAbsolutePath());
        if(crawlJob.isRunning() || (crawlJob.hasApplicationContext() && !crawlJob.isLaunchable()))
            this.put("crawlLogFilePath",crawlJob.getCrawlController().getLoggerModule().getCrawlLogPath().getFile().getAbsolutePath());
        this.put("reports", generateReports());
    }
    public String formatBytes(Long bytes){
        return ArchiveUtils.formatBytesForDisplay(bytes);
    }
    public String doubleToString(double number, int digits){
        return ArchiveUtils.doubleToString(number, digits);
    }
    public String getLastLaunchTime(){
        long ago = System.currentTimeMillis()
                - crawlJob.getLastLaunch().getMillis();
        return ArchiveUtils.formatMillisecondsToConventional(ago, 2);
    }
    /*
     * Alternative access to the file object, full name stored in base data map.
     */
    public File getConfigurationFilePath(){
        return crawlJob.getPrimaryConfig();
    }
    
    public List<String> generateJobLogTail(){
        List<String> jobLog = new ArrayList<String>();
        if (crawlJob.getJobLog().exists()) {
            try {
                FileUtils.pagedLines(crawlJob.getJobLog(), -1, -5, jobLog);
                Collections.reverse(jobLog);
            } catch (IOException ioe) {
                return null;
            }
        }
        return jobLog;
    }
    public List<String> generateCrawlLogTail() {
        List<String> logLines = new LinkedList<String>();
        if ((crawlJob.isRunning() || (crawlJob.hasApplicationContext() && !crawlJob.isLaunchable()))
                && crawlJob.getCrawlController().getLoggerModule()
                        .getCrawlLogPath().getFile().exists()) {
            try {
                FileUtils.pagedLines(crawlJob.getCrawlController()
                        .getLoggerModule().getCrawlLogPath().getFile(), -1,
                        -10, logLines);
                Collections.reverse(logLines);
            } catch (IOException ioe) {
                return null;
            }
        }
        return logLines;
    }
    public List<Map<String,String>> generateReports(){
        List<Map<String,String>> reports = new ArrayList<Map<String,String>>();
        if(crawlJob.hasApplicationContext()){
            for (Report report : crawlJob.getCrawlController().getStatisticsTracker().getReports()) {
                if (report.getShouldReportDuringCrawl()) {
                    Map<String,String> reportMap = new LinkedHashMap<String,String>();
                    String className = report.getClass().getSimpleName();
                    reportMap.put("className", className);
                    reportMap.put("shortName",className.substring(0,className.length() - "Report".length()));
                    reports.add(reportMap);
                }
            }
        }
        return reports;
    }
    private List<Map<String,Object>> generateConfigReferencedPaths(String baseRef){
        List<Map<String,Object>> referencedPaths = new ArrayList<Map<String,Object>>();
        for (String key : crawlJob.getConfigPaths().keySet()) {
            ConfigPath cp = crawlJob.getConfigPaths().get(key);
            Map<String,Object> configMap = new LinkedHashMap<String,Object>();
            configMap.put("key", key);
            configMap.put("name", cp.getName());
            configMap.put("path",FileUtils.tryToCanonicalize(cp.getFile()).getAbsolutePath());
            configMap.put("url",baseRef+"engine/anypath/"+configMap.get("path"));
            configMap.put("editable", EDIT_FILTER.accept(cp.getFile()));
            referencedPaths.add(configMap);
        }
        return referencedPaths;
    }
}
