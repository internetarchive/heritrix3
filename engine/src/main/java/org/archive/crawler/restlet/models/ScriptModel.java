package org.archive.crawler.restlet.models;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

@SuppressWarnings("serial")
public class ScriptModel  extends LinkedHashMap<String, Object> {

    public ScriptModel(String crawlJobShortName, String crawlJobUrl, Collection<Map<String,String>> scriptEngines, int linesExecuted, Exception exception, String rawOutput, String htmlOutput){
        super();
        this.put("crawlJobUrl",crawlJobUrl);
        this.put("crawlJobShortName", crawlJobShortName);
        this.put("availableScriptEngines", scriptEngines);
        
        if (linesExecuted > 0) {
            this.put("linesExecuted", linesExecuted);
        }
        if (exception != null) {
            this.put("exception", exception);
        }
        if (StringUtils.isNotBlank(rawOutput)) {
            this.put("rawOutput", rawOutput);
        }
        if (StringUtils.isNotBlank(htmlOutput)) {
            this.put("htmlOutput", htmlOutput);
        }
        
        List<Map<String,String>> vars = new LinkedList<Map<String,String>>();
        Map<String,String> var;
        
        var = new LinkedHashMap<String,String>();
        var.put("variable", "rawOut");
        var.put("description", "a PrintWriter for arbitrary text output to this page");
        vars.add(var);
        
        var = new LinkedHashMap<String,String>();
        var.put("variable", "htmlOut");
        var.put("description", "a PrintWriter for HTML output to this page");
        vars.add(var);
        
        var = new LinkedHashMap<String,String>();
        var.put("variable", "job");
        var.put("description", "the current CrawlJob instance");
        vars.add(var);
        
        var = new LinkedHashMap<String,String>();
        var.put("variable", "appCtx");
        var.put("description", "current job ApplicationContext, if any");
        vars.add(var);
        
        var = new LinkedHashMap<String,String>();
        var.put("variable", "scriptResource");
        var.put("description", "the ScriptResource implementing this page, which offers utility methods");
        vars.add(var);
        this.put("availableGlobalVariables", vars);
    }
}
