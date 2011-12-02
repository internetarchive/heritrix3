package org.archive.util;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.springframework.context.ApplicationContext;

public class ScriptUtils {
    private static Map<String, Object> savedState= new HashMap<String, Object>();

    public static ScriptEngineManager MANAGER = new ScriptEngineManager();
    
    /**
     * Standardize the environment scripts can expect to execute in.
     * @param engine javascript/groovy/beanshell/...
     * @param script the code
     * @param appCtx where to start traversing beans
     * @param txtOut text output
     * @param htmlOut if the script would like to format its output as html
     * @param bindings nonstandard bindings
     * @throws ScriptException
     */
    public static void eval(ScriptEngine eng
            , String script
            , ApplicationContext appCtx
            , PrintWriter txtOut
            , PrintWriter htmlOut
            , Bindings bindings) throws ScriptException {
        
        eng.put("rawOut", txtOut);
        eng.put("htmlOut", htmlOut);
        eng.put("appCtx", appCtx);
        eng.put("engine", eng);
        eng.put("savedState", savedState);
        
        try {
            eng.eval(script);
        } finally {
            txtOut.flush();
            htmlOut.flush();

            eng.put("rawOut",  null);
            eng.put("htmlOut", null);
            eng.put("appCtx",  null);
            eng.put("engine",  null);
        }

    }
    
    /**
     * uses MANAGER.getEngineByName(String)
     */
    public static void eval(String engine
            , String script
            , ApplicationContext appCtx
            , PrintWriter txtOut
            , PrintWriter htmlOut
            , Bindings bindings) throws ScriptException {
        ScriptEngine eng = MANAGER.getEngineByName(engine);
        eval(eng, script, appCtx, txtOut, htmlOut, bindings);
    }
}
