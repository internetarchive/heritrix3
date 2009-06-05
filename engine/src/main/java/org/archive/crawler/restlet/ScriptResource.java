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
import java.util.LinkedList;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
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
    String rawOutput = ""; 
    String htmlOutput = "";
    String chosenEngine = FACTORIES.isEmpty() ? "" : FACTORIES.getFirst().getNames().get(0);
    
    public ScriptResource(Context ctx, Request req, Response res) throws ResourceException {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
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
        } catch (ScriptException e) {
            ex = e;
        } catch (RuntimeException e) {
            ex = e;
        } finally {
            rawOut.flush();
            rawOutput = rawString.toString();
            htmlOut.flush();
            htmlOutput = htmlString.toString();
        }
        
        getResponse().setEntity(represent());
    }
    
    public Representation represent(Variant variant) throws ResourceException {
        Representation representation = new WriterRepresentation(
                MediaType.TEXT_HTML) {
            public void write(Writer writer) throws IOException {
                ScriptResource.this.writeHtml(writer);
            }
        };
        // TODO: remove if not necessary in future?
        representation.setCharacterSet(CharacterSet.UTF_8);
        return representation;
    }

    protected void writeHtml(Writer writer) {
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<h1>Execute script for job <i>"+cj.getShortName()+"</i></h1>");
        // output of previous script, if any

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

        pw.close();
    }
}
