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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.crawler.framework.BeanLookupBindings;
import org.archive.crawler.restlet.models.CrawlJobModel;
import org.archive.crawler.restlet.models.EngineModel;
import org.archive.crawler.restlet.models.ScriptModel;
import org.archive.crawler.restlet.models.ViewModel;
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
import org.springframework.context.ApplicationContext;

import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Restlet Resource which runs an arbitrary script, which is supplied
 * with variables pointing to the job and appContext, from which all
 * other live crawl objects are reachable. 
 * 
 * Any JSR-223 script engine that's properly discoverable on the 
 * classpath will be available from a drop-down selector. 
 * 
 * @contributor gojomo
 * @contributor adam-miller
 */
public class ScriptResource extends JobRelatedResource {
    protected static ScriptEngineManager MANAGER = new ScriptEngineManager();
    // oddly, ordering is different each call to getEngineFactories, so cache
    protected static LinkedList<ScriptEngineFactory> FACTORIES = new LinkedList<ScriptEngineFactory>();
    static {
        FACTORIES.addAll(MANAGER.getEngineFactories());
        // Sort factories alphabetically so that they appear in the UI consistently
        Collections.sort(FACTORIES, new Comparator<ScriptEngineFactory>() {
            @Override
            public int compare(ScriptEngineFactory sef1, ScriptEngineFactory sef2) {
                return sef1.getEngineName().compareTo(sef2.getEngineName());
            }
        });
    }
    protected String script = "";
    protected Exception ex = null;
    protected int linesExecuted = 0; 
    protected String rawOutput = ""; 
    protected String htmlOutput = "";
    protected String chosenEngine = FACTORIES.isEmpty() ? "" : FACTORIES.getFirst().getNames().get(0);
    private Configuration _templateConfiguration;
    
    public ScriptResource(Context ctx, Request req, Response res) throws ResourceException {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
        
        Configuration tmpltCfg = new Configuration();
        tmpltCfg.setClassForTemplateLoading(this.getClass(),"");
        tmpltCfg.setObjectWrapper(ObjectWrapper.BEANS_WRAPPER);
        setTemplateConfiguration(tmpltCfg);
    }
    public void setTemplateConfiguration(Configuration tmpltCfg) {
        _templateConfiguration=tmpltCfg;
    }
    public Configuration getTemplateConfiguration(){
        return _templateConfiguration;
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
        ApplicationContext appCtx = cj.getJobContext();
        Bindings bindings = new BeanLookupBindings(appCtx);
        bindings.put("rawOut", rawOut);
        StringWriter htmlString = new StringWriter(); 
        PrintWriter htmlOut = new PrintWriter(htmlString);
        bindings.put("htmlOut", htmlOut);
        bindings.put("job", cj);
        bindings.put("appCtx", appCtx);
        bindings.put("scriptResource", this);
        try {
            eng.eval(script, bindings);
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

            // the script could create an object that persists and retains a reference to the Bindings
            bindings.put("rawOut", null);
            bindings.put("htmlOut", null);
            bindings.put("job", null);
            bindings.put("appCtx", null);
            bindings.put("scriptResource", null);
        }
        //TODO: log script, results somewhere; job log INFO? 
        
        getResponse().setEntity(represent());
    }
    
    public Representation represent(Variant variant) throws ResourceException {
        Representation representation;
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            representation = new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer,"script", makeDataModel());
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

    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected ScriptModel makeDataModel() {
        
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        Reference baseRefRef = new Reference(baseRef);
        
        ScriptModel model = new ScriptModel(cj.getShortName(),
                new Reference(baseRefRef, "..").getTargetRef().toString(),
                getAvailableScriptEngines(),
                linesExecuted,
                ex,
                rawOutput,
                htmlOutput);

        return model;
    }

    protected void writeHtml(Writer writer) {
        
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        Configuration tmpltCfg = getTemplateConfiguration();

        ViewModel viewModel = new ViewModel();
        viewModel.setFlashes(Flash.getFlashes(getRequest()));
        viewModel.put("baseRef",baseRef);
        viewModel.put("cssRef", getStylesheetRef());
        viewModel.put("staticRef", getStaticRef(""));
        viewModel.put("baseResourceRef",getRequest().getRootRef().toString()+"/engine/static/");
        viewModel.put("model",makeDataModel());
        viewModel.put("selectedEngine", chosenEngine);
        viewModel.put("script", script);

        try {
            Template template = tmpltCfg.getTemplate("Script.ftl");
            template.process(viewModel, writer);
            writer.flush();
        } catch (IOException e) { 
            throw new RuntimeException(e); 
        } catch (TemplateException e) { 
            throw new RuntimeException(e); 
        }

    }
}
