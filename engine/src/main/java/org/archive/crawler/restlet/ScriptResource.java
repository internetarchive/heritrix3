/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.crawler.restlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.util.TextUtils;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.restlet.resource.WriterRepresentation;

/**
 * Restlet Resource which runs an arbitrary script, which is supplied
 * with variables pointing to the job and appContext, from which all
 * other live crawl objects are reachable. 
 * 
 * Any JSR-223 script engine that's properly discoverable on the 
 * classpath will be available from a drop-down selector. 
 * 
 * @contributor gojomo
 */
public class ScriptResource extends JobRelatedResource {
    static ScriptEngineManager MANAGER = new ScriptEngineManager();
    // oddly, ordering is different each call to getEngineFactories, so cache
    static LinkedList<ScriptEngineFactory> FACTORIES = new LinkedList<ScriptEngineFactory>();
    static {
        FACTORIES.addAll(MANAGER.getEngineFactories());
    }
    String script = "";
    Exception ex = null;
    int linesExecuted = 0; 
    String rawOutput = ""; 
    String htmlOutput = "";
    String chosenEngine = FACTORIES.isEmpty() ? "" : FACTORIES.getFirst().getNames().get(0);
    
    public ScriptResource(Context ctx, Request req, Response res) throws ResourceException {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
    }
    
    @Override
    public void acceptRepresentation(Representation entity) throws ResourceException {
        Form form = getRequest().getEntityAsForm();
        chosenEngine = form.getFirstValue("engine");
        script = form.getFirstValue("script");
        if(StringUtils.isBlank(script)) {
            script="";
        }

        ScriptEngine eng = MANAGER.getEngineByName(chosenEngine);
        
        StringWriter rawString = new StringWriter(); 
        PrintWriter rawOut = new PrintWriter(rawString);
        eng.put("rawOut", rawOut);
        StringWriter htmlString = new StringWriter(); 
        PrintWriter htmlOut = new PrintWriter(htmlString);
        eng.put("htmlOut", htmlOut);
        eng.put("job", cj);
        eng.put("appCtx", cj.getJobContext());
        eng.put("scriptResource", this);
        try {
            eng.eval(script);
            linesExecuted = script.split("\r?\n").length;
        } catch (ScriptException e) {
            ex = e;
        } catch (RuntimeException e) {
            ex = e;
        } finally {
            rawOut.flush();
            rawOutput = rawString.toString();
            htmlOut.flush();
            htmlOutput = htmlString.toString();

            eng.put("rawOut", null);
            eng.put("htmlOut", null);
            eng.put("job", null);
            eng.put("appCtx", null);
            eng.put("scriptResource", null);
        }
        //TODO: log script, results somewhere; job log INFO? 
        
        getResponse().setEntity(represent());
    }
    
    public Representation represent(Variant variant) throws ResourceException {
        Representation representation;
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            representation = new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer,"script", makePresentableMap());
                }
            };
        } else {
            representation = new WriterRepresentation(MediaType.TEXT_HTML) {
                public void write(Writer writer) throws IOException {
                    ScriptResource.this.writeHtml(writer);
                }
            };
        }
        // TODO: remove if not necessary in future?
        representation.setCharacterSet(CharacterSet.UTF_8);
        return representation;
    }

    protected List<String> getAvailableActions() {
        List<String> actions = new LinkedList<String>();
        actions.add("rescan");
        actions.add("add");
        actions.add("create");
        return actions;
    }

    protected Collection<Map<String,String>> getAvailableScriptEngines() {
        List<Map<String,String>> engines = new LinkedList<Map<String,String>>();
        for (ScriptEngineFactory f: FACTORIES) {
            Map<String,String> engine = new LinkedHashMap<String, String>();
            engine.put("engine", f.getNames().get(0));
            engine.put("language", f.getLanguageName());
            engines.add(engine);
        }
        return engines;
    }

    protected Collection<Map<String,String>> getAvailableGlobalVariables() {
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
        
        return vars;
    }

    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected LinkedHashMap<String,Object> makePresentableMap() {
        LinkedHashMap<String,Object> info = new LinkedHashMap<String,Object>();

        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        Reference baseRefRef = new Reference(baseRef);

        info.put("crawlJobShortName", cj.getShortName());
        info.put("crawlJobUrl", new Reference(baseRefRef, "..").getTargetRef());
        info.put("availableScriptEngines", getAvailableScriptEngines());
        info.put("availableGlobalVariables", getAvailableGlobalVariables());

        if(linesExecuted>0) {
            info.put("linesExecuted", linesExecuted);
        }
        if(ex!=null) {
            info.put("exception", ex);
        }
        if(StringUtils.isNotBlank(rawOutput)) {
            info.put("rawOutput", rawOutput);
        }
        if(StringUtils.isNotBlank(htmlOutput)) {
            info.put("htmlOutput", htmlOutput);
        }

        return info;
    }

    protected void writeHtml(Writer writer) {
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<head><title>Script in "+cj.getShortName()+"</title></head>");
        pw.println("<h1>Execute script for job <i><a href='/engine/job/"
                    +TextUtils.urlEscape(cj.getShortName())
                    +"'>"+cj.getShortName()+"</a></i></h1>");

        // output of previous script, if any
        if(linesExecuted>0) {
            pw.println("<span class='success'>"+linesExecuted+" lines executed</span>");
        }
        if(ex!=null) {
            pw.println("<pre style='color:red; height:150px; overflow:auto'>");
            ex.printStackTrace(pw);
            pw.println("</pre>");
        }
        if(StringUtils.isNotBlank(htmlOutput)) {
            pw.println("<fieldset><legend>htmlOut</legend>");
            pw.println(htmlOutput);
            pw.println("</fieldset>");
        }
        
        if(StringUtils.isNotBlank(rawOutput)) {
            pw.println("<fieldset><legend>rawOut</legend><pre>");
            pw.println(StringEscapeUtils.escapeHtml(rawOutput));
            pw.println("</pre></fieldset>");
        }

        pw.println("<form method='POST'>");
        pw.println("<input type='submit' value='execute'></input>");
        pw.println("<select name='engine'>");;
        for(ScriptEngineFactory f : FACTORIES) {
            String opt = f.getNames().get(0);
            pw.println("<option "
                    +(opt.equals(chosenEngine)?" selected='selected' ":"")
                    +"value='"+opt+"'>"+f.getLanguageName()+"</option>");
        }
        pw.println("</select>");
        pw.println("<textarea rows='20' style='width:100%' name=\'script\'>"+script+"</textarea>");
        pw.println("<input type='submit' value='execute'></input>");
        pw.println("</form>");
        pw.println(
                "The script will be executed in an engine preloaded " +
                "with (global) variables: <ul>\n" +
                "<li>rawOut: a PrintWriter for arbitrary text output to this page</li>\n" +
                "<li>htmlOut: a PrintWriter for HTML output to this page</li>\n" +
                "<li>job: the current CrawlJob instance</li>\n" +
                "<li>appCtx: current job ApplicationContext, if any</li>\n" +
                "<li>scriptResource: the ScriptResource implementing this " +
                "page, which offers utility methods</li>\n" +
                "</ul>");

        pw.flush();
    }
}
