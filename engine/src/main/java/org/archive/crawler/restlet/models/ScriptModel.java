package org.archive.crawler.restlet.models;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.archive.crawler.restlet.ScriptingConsole;

@XmlRootElement(name="script")
@XmlType(propOrder={
        "crawlJobUrl", "crawlJobShortName", "availableScriptEngines", 
        "script", "linesExecuted", "exception", "rawOutput", "htmlOutput",
        "availableGlobalVariables"
})
public class ScriptModel {
    private String crawlJobUrl;
    private Collection<Map<String, String>> availableScriptEngines;
    private ScriptingConsole scriptingConsole;

	public ScriptModel(ScriptingConsole cc,
	        String crawlJobUrl,
			Collection<Map<String, String>> scriptEngines) {
	    scriptingConsole = cc;
	    this.crawlJobUrl = crawlJobUrl;
	    this.availableScriptEngines = scriptEngines;
    }
	
    public boolean isFailure() {
        return scriptingConsole.getException() != null;
    }
    public String getStackTrace() {
        Throwable exception = scriptingConsole.getException();
        if (exception == null) return "";
        StringWriter s = new StringWriter();
        exception.printStackTrace(new PrintWriter(s));
        return s.toString();
    }
    public Throwable getException() {
        return scriptingConsole.getException();
    }
    public int getLinesExecuted() {
        return scriptingConsole.getLinesExecuted();
    }
    public String getRawOutput() {
        return scriptingConsole.getRawOutput();
    }
    public String getHtmlOutput() {
        return scriptingConsole.getHtmlOutput();
    }
    public String getScript() {
        return scriptingConsole.getScript();
    }
    
    public String getCrawlJobShortName() {
        return scriptingConsole.getCrawlJob().getShortName();
    }
    public Collection<Map<String, String>> getAvailableScriptEngines() {
        return availableScriptEngines;
    }
    public List<Map<String, String>> getAvailableGlobalVariables() {
        return scriptingConsole.getAvailableGlobalVariables();
    }
    public String getCrawlJobUrl() {
        return crawlJobUrl;
    }
}
