package org.archive.crawler.restlet.models;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.archive.checkpointing.Checkpoint;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.reporting.Report;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;

@SuppressWarnings("serial")
public class CrawlJobModel extends HashMap<String, Object> implements Serializable{

    private CrawlJob crawlJob;
    public static final IOFileFilter EDIT_FILTER = FileUtils
            .getRegexFileFilter(".*\\.((c?xml)|(txt))$");
    
    public CrawlJobModel(CrawlJob crawlJob){
        super();
        this.crawlJob=crawlJob;
        this.put("shortName",crawlJob.getShortName());
        this.put("status", crawlJob.getJobStatusDescription());
        this.put("launchCount", crawlJob.getLaunchCount());
        this.put("config", crawlJob.getPrimaryConfig());
        this.put("isProfile", crawlJob.isProfile());
        this.put("isLaunchInfoPartial", crawlJob.isLaunchInfoPartial());
        this.put("isLaunchable", crawlJob.isLaunchable());
        this.put("isPausable", crawlJob.isPausable());
        this.put("isUnpausable", crawlJob.isUnpausable());
        this.put("isRunning", crawlJob.isRunning());
        this.put("uriTotalsReport", crawlJob.uriTotalsReport());
        this.put("sizeTotalsReport", crawlJob.sizeTotalsReport());        
        this.put("hasApplicationContext",crawlJob.hasApplicationContext());
        this.put("lastLaunch",crawlJob.getLastLaunch());
        this.put("alertCount", crawlJob.getAlertCount());        
        this.put("rateReport", crawlJob.rateReport()); 
        this.put("loadReport", crawlJob.loadReport());
        this.put("elapsedReport", crawlJob.elapsedReport()); 
        this.put("threadReport", crawlJob.threadReport()); 
        this.put("frontierReport", crawlJob.frontierReport()); 
        this.put("loadReport", crawlJob.loadReport()); 
        
        this.put("key", "");
    }
    public String getLastLaunchTime(){
        long ago = System.currentTimeMillis()
                - crawlJob.getLastLaunch().getMillis();
        return ArchiveUtils.formatMillisecondsToConventional(ago, 2);
    }
    public String getCheckpointName(){
        if (crawlJob.getCheckpointService() != null) {
            Checkpoint recoveryCheckpoint = crawlJob.getCheckpointService().getRecoveryCheckpoint();
            if (recoveryCheckpoint != null)
                return recoveryCheckpoint.getName();
        }
        return null;
    }
    public List<String> getCheckpointFiles(){
        List<String> checkpointFiles = new ArrayList<String>();
        if (crawlJob.getCheckpointService() != null) {
            if (crawlJob.getCheckpointService().hasAvailableCheckpoints() && crawlJob.isLaunchable()) {
                for (File f : crawlJob.getCheckpointService().findAvailableCheckpointDirectories()) {
                    checkpointFiles.add(f.getName()); 
                }
            }
        }
        return checkpointFiles;
    }
    public File getConfigurationFilePath(){
        return crawlJob.getPrimaryConfig();
    }
    public File getAlertLogFilePath(){
        return crawlJob.getCrawlController().getLoggerModule().getAlertsLogPath().getFile();
    }
    public File getCrawlLogFilePath(){
        return crawlJob.getCrawlController().getLoggerModule().getCrawlLogPath().getFile();
    }
    public List<File> getImportedConfigurationFilePaths(){
//        List<String> configList = new ArrayList<String>();
//        for( File f : crawlJob.getImportedConfigs(crawlJob.getPrimaryConfig())){
//            configList.add(f);
//        }
//        return configList;
        return crawlJob.getImportedConfigs(crawlJob.getPrimaryConfig());
    }
    public List<String> getJobLogTail(){
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
    public List<String> getCrawlLogTail() {
        List<String> logLines = new LinkedList<String>();
        if(crawlJob.getCrawlController().getLoggerModule().getCrawlLogPath().getFile().exists()) {
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
    public List<Map<String,String>> getReports(){
        List<Map<String,String>> reports = new ArrayList<Map<String,String>>();
        for (Report report : crawlJob.getCrawlController().getStatisticsTracker().getReports()) {
            if (report.getShouldReportDuringCrawl()) {
                Map<String,String> reportMap = new HashMap<String,String>();
                String className = report.getClass().getSimpleName();
                reportMap.put("className", className);
                reportMap.put("shortName",className.substring(0,className.length() - "Report".length()));
                reports.add(reportMap);
            }
        }
        return reports;
    }
    public List<Map<String,Object>> getConfigReferencedPaths(){
        List<Map<String,Object>> referencedPaths = new ArrayList<Map<String,Object>>();
        for (String key : crawlJob.getConfigPaths().keySet()) {
            ConfigPath cp = crawlJob.getConfigPaths().get(key);
            Map<String,Object> configMap = new HashMap<String,Object>();
            configMap.put("key", key);
            configMap.put("name", cp.getName());
            configMap.put("path",cp.getFile());
            configMap.put("editable", EDIT_FILTER.accept(cp.getFile()));
            referencedPaths.add(configMap);
        }
        return referencedPaths;
    }
}
