/**
 * 
 */
package org.archive.crawler.restlet;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.archive.crawler.framework.BeanLookupBindings;
import org.archive.crawler.framework.CrawlJob;

/**
 * ScriptingConsole implements view-independent logic of scripting console.
 * 
 * Currently it is short-lived; it is created by ScriptResource for each request and
 * destroyed after rendering the view.
 * 
 * @contributor kenji
 *
 */
public class ScriptingConsole {
    private final CrawlJob cj;

    private ScriptEngine eng;
    private String script;
    private Bindings bindings;
    
    private StringWriter rawString;
    private StringWriter htmlString;
    private Throwable exception;
    private int linesExecuted;

    private List<Map<String, String>> availableGlobalVariables;
    
    public ScriptingConsole(CrawlJob job) {
        this.cj = job;
        this.bindings = new BeanLookupBindings(this.cj.getJobContext());
        this.script = "";
        
        setupAvailableGlobalVariables();
    }
    protected void addGlobalVariable(String name, String desc) {
        Map<String, String> var = new LinkedHashMap<String, String>();
        var.put("variable", name);
        var.put("description", desc);
        availableGlobalVariables.add(var);
    }
    private void setupAvailableGlobalVariables() {     
        availableGlobalVariables = new LinkedList<Map<String,String>>();
        addGlobalVariable("rawOut", "a PrintWriter for arbitrary text output to this page");
        addGlobalVariable("htmlOut", "a PrintWriter for HTML output to this page");
        addGlobalVariable("job", "the current CrawlJob instance");
        addGlobalVariable("appCtx", "current job ApplicationContext, if any");
        // TODO: a bit awkward to have this here, because ScriptingConsole has no ref to
        // ScriptResource. better to have ScriptResource call #addGlobalVariable(String, String)?
        addGlobalVariable("scriptResource",
                "the ScriptResource implementing this page, which offers utility methods");
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
        exception = null;
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
    public CrawlJob getCrawlJob( ) {
        return cj;
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
    
    public List<Map<String, String>> getAvailableGlobalVariables() {
        return availableGlobalVariables;
    }
}
