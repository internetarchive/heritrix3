package org.archive.crawler.restlet.models;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.archive.crawler.framework.Engine;
import org.archive.util.FileUtils;

@SuppressWarnings("serial")
public class EngineModel extends HashMap<String, Object> {
    
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
    }
}
