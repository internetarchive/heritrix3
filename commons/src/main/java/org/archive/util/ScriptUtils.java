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
    private static Map<String, Bindings> savedBindings = new HashMap<>();
    
    private ScriptEngine eng;
    public ScriptUtils(ScriptEngine eng) {
        this.eng = eng;
    }
    public void saveBindings(String bindingsName) {
        // appropriate values are nulled at the end of the script,
        // so keep the reference to the same managed Bindings
        savedBindings.put(bindingsName
                , eng.getBindings(ScriptContext.ENGINE_SCOPE));
    }
    public void loadBindings(String bindingsName) {
        // don't overwrite bindings that weren't explicitly set in
        // the bindings being retrieved
        for (Entry<String, Object> inABind : savedBindings.get(bindingsName).entrySet())
            eng.put(inABind.getKey(), inABind.getValue());
    }

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
        eng.put("scriptUtils", new ScriptUtils(eng));
        
        try {
            eng.eval(script);
        } finally {
            txtOut.flush();
            htmlOut.flush();

            eng.put("rawOut",  null);
            eng.put("htmlOut", null);
            eng.put("appCtx",  null);
            eng.put("engine",  null);
            eng.put("scriptUtils",  null);
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
