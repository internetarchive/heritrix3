package org.archive.crawler.restlet.models;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.archive.crawler.framework.CrawlController.State;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.util.FileUtils;

@SuppressWarnings("serial")
public class EngineModel extends LinkedHashMap<String, Object> {
    
    public EngineModel(Engine engine, String urlBaseRef){
        super();
        this.put("heritrixVersion", engine.getHeritrixVersion());
        this.put("heapReport", engine.heapReportData());
        this.put("jobsDir", FileUtils.tryToCanonicalize(engine.getJobsDir()).getAbsolutePath());
        this.put("jobsDirUrl", urlBaseRef + "jobsdir/");
        
        List<String> actions = new LinkedList<String>();
        actions.add("rescan");
        actions.add("add");
        actions.add("create");
        this.put("availableActions", actions);
        
        this.put("jobs", makeJobList(engine, urlBaseRef));
    }

	private List<Map<String, Object>> makeJobList(Engine engine,
			String urlBaseRef) {
		List<Map<String, Object>> jobList;
		jobList = new ArrayList<Map<String,Object>>();
        // Generate list of jobs
        ArrayList<Map.Entry<String,CrawlJob>> jobConfigurations = new ArrayList<Map.Entry<String,CrawlJob>>(engine.getJobConfigs().entrySet());
        Collections.sort(jobConfigurations, new Comparator<Map.Entry<String, CrawlJob>>() {
            public int compare(Map.Entry<String, CrawlJob> cj1, Map.Entry<String, CrawlJob> cj2) {
                return cj1.getValue().compareTo(cj2.getValue());
            }
        });
        
        for(Map.Entry<String,CrawlJob> jobConfig : jobConfigurations) {
            CrawlJob job = jobConfig.getValue();
            Map<String, Object> crawlJobModel = new LinkedHashMap<String, Object>();
            crawlJobModel.put("shortName",job.getShortName());
            crawlJobModel.put("url",urlBaseRef+"job/"+job.getShortName());
            crawlJobModel.put("isProfile",job.isProfile());
            crawlJobModel.put("launchCount",job.getLaunchCount());
            crawlJobModel.put("lastLaunch",job.getLastLaunch());
            crawlJobModel.put("hasApplicationContext",job.hasApplicationContext());
            crawlJobModel.put("statusDescription", job.getJobStatusDescription());
            crawlJobModel.put("isLaunchInfoPartial", job.isLaunchInfoPartial());
            File primaryConfig = FileUtils.tryToCanonicalize(job.getPrimaryConfig());
            crawlJobModel.put("primaryConfig", primaryConfig.getAbsolutePath());
            crawlJobModel.put("primaryConfigUrl", urlBaseRef + "jobdir/" + primaryConfig.getName());
            if (job.getCrawlController() != null) {
                crawlJobModel.put("crawlControllerState", job.getCrawlController().getState());
                if (job.getCrawlController().getState() == State.FINISHED) {
                    crawlJobModel.put("crawlExitStatus", job.getCrawlController().getCrawlExitStatus());
                }
            }
            
            crawlJobModel.put("key", jobConfig.getKey());
            jobList.add(crawlJobModel);
        }
		return jobList;
	}
}
