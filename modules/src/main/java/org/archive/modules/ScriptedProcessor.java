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

package org.archive.modules;

import java.io.Reader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.io.IOUtils;
import org.archive.io.ReadSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A processor which runs a JSR-223 script on the CrawlURI. 
 *
 * Script source may be provided via a file local to the crawler or
 * an inline configuration string. 
 * 
 * The source must include a function "run()" taking one argument. 
 * Each processed CrawlURI is passed to this script function. 
 * 
 * Other variables available to the script include 'self' (this 
 * ScriptedProcessor instance) and 'context' (the crawl's 
 * ApplicationContext instance, from which all named beans are
 * reachable). 
 * 
 * TODO: provide way to trigger reload of script mid-crawl; perhaps
 * by watching for a certain applicationEvent? 
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class ScriptedProcessor extends Processor 
implements ApplicationContextAware, InitializingBean {

    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(ScriptedProcessor.class.getName());

    /** engine name; default "beanshell" */
    protected String engineName = "beanshell";
    public String getEngineName() {
        return this.engineName;
    }
    public void setEngineName(String name) {
        this.engineName = name;
    }
    
    ReadSource scriptSource = null;
    public ReadSource getScriptSource() {
        return this.scriptSource;
    }
    @Required
    public void setScriptSource(ReadSource source) {
        this.scriptSource = source; 
    }

    /**
     * Whether each ToeThread should get its own independent script 
     * engine, or they should share synchronized access to one 
     * engine. Default is true, meaning each thread gets its own 
     * isolated engine.
     */
    protected boolean isolateThreads = true; 
    public boolean getIsolateThreads() {
        return isolateThreads;
    }
    public void setIsolateThreads(boolean isolateThreads) {
        this.isolateThreads = isolateThreads;
    }

    ApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.appCtx = applicationContext;
    }
    
    transient protected ThreadLocal<ScriptEngine> threadEngine = 
        new ThreadLocal<ScriptEngine>();
    protected ScriptEngine sharedEngine;

    /**
     * Constructor.
     */
    public ScriptedProcessor() {
        super();
    }

    public void afterPropertiesSet() throws Exception {
        // fail at build-time if script engine not available
        if(null == new ScriptEngineManager().getEngineByName(engineName)) {
            throw new BeanInitializationException("named ScriptEngine not available");
        }
    }
    protected boolean shouldProcess(CrawlURI curi) {
        return true;
    }
    
    @Override
    protected void innerProcess(CrawlURI curi) {
        // depending on previous configuration, engine may 
        // be local to this thread or shared
        ScriptEngine engine = getEngine(); 
        synchronized(engine) {
            // synchronization is harmless for local thread engine,
            // necessary for shared engine
            engine.put("curi",curi);
            engine.put("appCtx", appCtx);
            try {
                engine.eval("process(curi)");
            } catch (ScriptException e) {
                logger.log(Level.WARNING,e.getMessage(),e);
            } finally { 
                engine.put("curi", null);
                engine.put("appCtx", null);
            }
        }
    }

    /**
     * Get the proper ScriptEngine instance -- either shared or local 
     * to this thread. 
     * @return ScriptEngine to use
     */
    protected synchronized ScriptEngine getEngine() {
        if(sharedEngine==null 
           && getIsolateThreads()) {
            // initialize
            sharedEngine = newEngine();
        }
        if(sharedEngine!=null) {
            return sharedEngine;
        }
        ScriptEngine engine = threadEngine.get(); 
        if(engine==null) {
            engine = newEngine(); 
            threadEngine.set(engine);
        }
        return engine; 
    }

    /**
     * Create a new ScriptEngine instance, preloaded with any supplied
     * source file and the variables 'self' (this ScriptedDecideRule) 
     * and 'context' (the ApplicationContext). 
     * 
     * @return  the new Interpreter instance
     */
    protected ScriptEngine newEngine() {
        ScriptEngine interpreter = new ScriptEngineManager().getEngineByName(engineName);

        interpreter.put("self", this);
        interpreter.put("context", appCtx);
        
        Reader reader = null;
        try {
            reader = getScriptSource().obtainReader();
            interpreter.eval(reader);
        } catch (ScriptException e) {
            logger.log(Level.SEVERE,"script problem",e);
        } finally {
            IOUtils.closeQuietly(reader);
        }

        return interpreter; 
    }
}
