package org.archive.crawler.restlet.models;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.util.FileUtils;

@SuppressWarnings("serial")
public class EngineModel extends HashMap<String, Object> {
    
    public EngineModel(Engine engine, String urlBaseRef){
        super();
        this.put("heritrixVersion", engine.getHeritrixVersion());
        this.put("heapReport", engine.heapReport());
        this.put("jobsDir", FileUtils.tryToCanonicalize(engine.getJobsDir()).getAbsolutePath());
        this.put("jobsDirUrl", urlBaseRef + "jobsdir/");

        List<Map<String,Object>> jobList = new ArrayList<Map<String,Object>>();
        this.put("jobs", jobList);
        
        //Generate list of jobs
        ArrayList<Map.Entry<String,CrawlJob>> jobConfigurations = new ArrayList<Map.Entry<String,CrawlJob>>(engine.getJobConfigs().entrySet());
        Collections.sort(jobConfigurations, new Comparator<Map.Entry<String, CrawlJob>>() {
            public int compare(Map.Entry<String, CrawlJob> cj1, Map.Entry<String, CrawlJob> cj2) {
                return (cj2.getValue()).compareTo(cj1.getValue());
            }
        });
        for(Map.Entry<String,CrawlJob> jobConfig : jobConfigurations) {
            CrawlJobModel crawlJobModel = new CrawlJobModel(jobConfig.getValue(), urlBaseRef);
            crawlJobModel.put("key", jobConfig.getKey());
            jobList.add(crawlJobModel);
        }
        
        List<String> actions = new LinkedList<String>();
        actions.add("rescan");
        actions.add("add");
        actions.add("create");
        this.put("availableActions", actions);
    }
}
