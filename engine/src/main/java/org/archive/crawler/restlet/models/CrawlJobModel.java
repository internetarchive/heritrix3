package org.archive.crawler.restlet.models;

import java.util.HashMap;

import org.archive.crawler.framework.CrawlJob;

@SuppressWarnings("serial")
public class CrawlJobModel extends HashMap<String, Object>{

    public CrawlJobModel(CrawlJob crawlJob){
        super();
        this.put("shortName",crawlJob.getShortName());
        this.put("status", crawlJob.getJobStatusDescription());
        this.put("launchCount", crawlJob.getLaunchCount());
        this.put("config", crawlJob.getPrimaryConfig());
        this.put("isProfile", crawlJob.isProfile());
        this.put("isLaunchInfoPartial", crawlJob.isLaunchInfoPartial());
        this.put("hasApplicationContext",crawlJob.hasApplicationContext());
        this.put("lastLaunch",crawlJob.getLastLaunch());
        this.put("key", "");
    }
}
