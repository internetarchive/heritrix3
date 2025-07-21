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

import org.apache.commons.lang3.StringUtils;
import org.archive.crawler.restlet.models.ScriptModel;
import org.archive.crawler.restlet.models.ViewModel;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.representation.WriterRepresentation;
import org.restlet.resource.ResourceException;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Restlet Resource which runs an arbitrary script, which is supplied
 * with variables pointing to the job and appContext, from which all
 * other live crawl objects are reachable. 
 * 
 * Any JSR-223 script engine that's properly discoverable on the 
 * classpath will be available from a drop-down selector. 
 * 
 * @author gojomo
 * @author adam-miller
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
    
    protected String chosenEngine = FACTORIES.isEmpty() ? "" : FACTORIES.getFirst().getNames().get(0);

    @Override
    public void init(Context ctx, Request req, Response res) throws ResourceException {
        super.init(ctx, req, res);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));

        scriptingConsole = new ScriptingConsole(cj);
    }

    private ScriptingConsole scriptingConsole;
    
    @Override
    public Representation post(Representation entity, Variant variant) throws ResourceException {
        Form form = new Form(entity);
        chosenEngine = form.getFirstValue("engine");
        String script = form.getFirstValue("script");
        if(StringUtils.isBlank(script)) {
            script="";
        }

        ScriptEngine eng = MANAGER.getEngineByName(chosenEngine);
        
        scriptingConsole.bind("scriptResource",  this);
        scriptingConsole.execute(eng, script);
        scriptingConsole.unbind("scriptResource");
        
        //TODO: log script, results somewhere; job log INFO? 
        
        return get(variant);
    }

    @Override
    public Representation get(Variant variant) throws ResourceException {
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            return new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer,"script", makeDataModel());
                }
            };
        } else {
            ViewModel viewModel = new ViewModel();
            viewModel.put("baseResourceRef", getRequest().getRootRef().toString() + "/engine/static/");
            viewModel.put("model", makeDataModel());
            viewModel.put("selectedEngine", chosenEngine);
            viewModel.put("staticRef", getStaticRef(""));
            return render("Script.ftl", viewModel);
        }
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
        
        ScriptModel model = new ScriptModel(scriptingConsole,
                new Reference(baseRefRef, "..").getTargetRef().toString(),
                getAvailableScriptEngines());

        return model;
    }
}
