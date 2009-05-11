/* BeanShellProcessor
 *
 * Created on Aug 4, 2006
 *
 * Copyright (C) 2006 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307  USA
 */
package org.archive.modules;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.spring.ConfigPath;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.context.ApplicationContext;

import bsh.EvalError;
import bsh.Interpreter;

/**
 * A processor which runs a BeanShell script on the CrawlURI.
 *
 * Script source may be provided via a file
 * local to the crawler. 
 * Script source should define
 * a method with one argument, 'run(curi)'. Each processed CrawlURI is
 * passed to this script method. 
 * 
 * Other variables available to the script include 'self' (this 
 * BeanShellProcessor instance) and 'controller' (the crawl's 
 * CrawlController instance). 
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class BeanShellProcessor extends Processor {

    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(BeanShellProcessor.class.getName());


    /**
     *  BeanShell script file.
     */
    ConfigPath scriptFile = null;
    public ConfigPath getScriptFile() {
        return this.scriptFile;
    }
    @Required
    public void setScriptFile(ConfigPath file) {
        this.scriptFile = file; 
    }

    /**
     * Whether each ToeThread should get its own independent script context, or
     * they should share synchronized access to one context. Default is true,
     * meaning each threads gets its own isolated context.
     * 
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
    
    protected ThreadLocal<Interpreter> threadInterpreter;
    protected Interpreter sharedInterpreter;
    public Map<Object,Object> sharedMap = Collections.synchronizedMap(
            new HashMap<Object,Object>());

    /**
     * Constructor.
     */
    public BeanShellProcessor() {
        super();
    }

    protected boolean shouldProcess(ProcessorURI curi) {
        return true;
    }
    
    @Override
    protected synchronized void innerProcess(ProcessorURI curi) {
        // depending on previous configuration, interpreter may 
        // be local to this thread or shared
        Interpreter interpreter = getInterpreter(); 
        synchronized(interpreter) {
            // synchronization is harmless for local thread interpreter,
            // necessary for shared interpreter
            try {
                interpreter.set("curi",curi);
                interpreter.eval("process(curi)");
            } catch (EvalError e) {
                logger.log(Level.WARNING,"BeanShell error", e);
            } 
        }
    }

    /**
     * Get the proper Interpreter instance -- either shared or local 
     * to this thread. 
     * @return Interpreter to use
     */
    protected Interpreter getInterpreter() {
        if(sharedInterpreter==null 
           && getIsolateThreads()) {
            // initialize
            sharedInterpreter = newInterpreter();
        }
        if(sharedInterpreter!=null) {
            return sharedInterpreter;
        }
        Interpreter interpreter = threadInterpreter.get(); 
        if(interpreter==null) {
            interpreter = newInterpreter(); 
            threadInterpreter.set(interpreter);
        }
        return interpreter; 
    }

    /**
     * Create a new Interpreter instance, preloaded with any supplied
     * source code or source file and the variables 'self' (this 
     * BeanShellProcessor) and 'controller' (the CrawlController). 
     * 
     * @return  the new Interpreter instance
     */
    protected Interpreter newInterpreter() {
        Interpreter interpreter = new Interpreter(); 
        try {
            interpreter.set("self", this);
            interpreter.set("context", appCtx);

            File file = getScriptFile().getFile();
            try {
                interpreter.source(file.getPath());
            } catch (IOException e) {
                logger.log(Level.SEVERE,"unable to read script file",e);
            }
        } catch (EvalError e) {
            logger.log(Level.SEVERE,"error in source file",e);
        }
        
        return interpreter; 
    }
}
