package org.archive.crawler.restlet.models;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.archive.crawler.framework.BeanLookupBindings;
import org.archive.crawler.framework.CrawlJob;

@XmlRootElement(name="script")
@XmlType(propOrder={
        "crawlJobUrl", "crawlJobShortName", "availableScriptEngines", 
        "script", "linesExecuted", "exception", "rawOutput", "htmlOutput",
        "availableGlobalVariables"
})
public class ScriptModel {
    private CrawlJob cj;
    private String crawlJobUrl;
    private Collection<Map<String, String>> availableScriptEngines;
    
    //private ApplicationContext appCtx;
    private ScriptEngine eng;
    private String script;
    private Bindings bindings;
    
    private StringWriter rawString;
    private StringWriter htmlString;
    private Throwable exception;
    private int linesExecuted;
    
    private List<Map<String, String>> availableGlobalVariables;

	public ScriptModel(CrawlJob cj,
	        String crawlJobUrl,
			Collection<Map<String, String>> scriptEngines) {
	    this.crawlJobUrl = crawlJobUrl;
	    this.cj = cj;
	    this.availableScriptEngines = scriptEngines;
        
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
        
        this.availableGlobalVariables = vars;
        
        this.bindings = new BeanLookupBindings(this.cj.getJobContext());
        this.script = "";
    }
	
    public void bind(String name, Object obj) {
        bindings.put(name, obj);
    }
    public Object unbind(String name) {
        return bindings.remove(name);
    }
    public void execute(ScriptEngine eng, String script) {
        // TODO: update through setter rather than passing as method arguments?
        this.eng = eng;
        this.script = script;
        
        bind("job", cj);
        rawString = new StringWriter();
        htmlString = new StringWriter();
        PrintWriter rawOut = new PrintWriter(rawString);
        PrintWriter htmlOut = new PrintWriter(htmlString);
        bind("rawOut", rawOut);
        bind("htmlOut", htmlOut);
        bind("appCtx", cj.getJobContext());
        try {
            this.eng.eval(this.script, bindings);
            // TODO: should count with RE rather than creating String[]?
            linesExecuted = script.split("\r?\n").length;
        } catch (ScriptException ex) {
            Throwable cause = ex.getCause();
            exception = cause != null ? cause : ex;
        } catch (RuntimeException ex) {
            exception = ex;
        } finally {
            rawOut.flush();
            htmlOut.flush();
            // TODO: are these really necessary? 
            unbind("rawOut");
            unbind("htmlOut");
            unbind("appCtx");
            unbind("job");
        }
    }
    public boolean isFailure() {
        return exception != null;
    }
    public String getStackTrace() {
        if (exception == null) return "";
        StringWriter s = new StringWriter();
        exception.printStackTrace(new PrintWriter(s));
        return s.toString();
    }
    public Throwable getException() {
        return exception;
    }
    public int getLinesExecuted() {
        return linesExecuted;
    }
    public String getRawOutput() {
        return rawString != null ? rawString.toString() : "";
    }
    public String getHtmlOutput() {
        return htmlString != null ? htmlString.toString() : "";
    }
    public String getScript() {
        return script;
    }
    
    public String getCrawlJobShortName() {
        return cj.getShortName();
    }
    public Collection<Map<String, String>> getAvailableScriptEngines() {
        return availableScriptEngines;
    }
    public List<Map<String, String>> getAvailableGlobalVariables() {
        return availableGlobalVariables;
    }
    public String getCrawlJobUrl() {
        return crawlJobUrl;
    }
}
