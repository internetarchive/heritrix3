package org.archive.util;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.springframework.context.ApplicationContext;

public class ScriptUtils {
    private static Map<String, Object> scriptStaticState = new ConcurrentHashMap<String, Object>();

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
    public static void eval(ScriptEngine eng, String script,
            ApplicationContext appCtx, PrintWriter txtOut, PrintWriter htmlOut,
            Map<String, Object> bindings) throws ScriptException {
        // the local staticState provides isolation (I from ACID)
        ChangeMarkingHashMap<String,Object> staticState = new ChangeMarkingHashMap<String, Object>(scriptStaticState);
        eng.put("rawOut", txtOut);
        eng.put("htmlOut", htmlOut);
        eng.put("appCtx", appCtx);
        eng.put("staticState", staticState);
        for (Map.Entry<String, Object> i : bindings.entrySet())
            eng.put(i.getKey(), i.getValue());
        
        try {
            eng.eval(script);
        } finally {
            txtOut.flush();
            htmlOut.flush();

            eng.put("rawOut",  null);
            eng.put("htmlOut", null);
            eng.put("appCtx",  null);
            eng.put("staticState", null);
            for (Map.Entry<String, Object> i : bindings.entrySet())
                eng.put(i.getKey(), null);
        }
        for (String i : staticState.alteredKeys)
            scriptStaticState.put(i, staticState.get(i));
    }
    
    /*
     * Without marking changes, all keys would have to be overwritten. 
     * With marking changes, concurrent scripts which do not otherwise step on each other's toes
     * will not overwrite one another's modified values with unmodified values.
     * See the use of alteredKeys in eval. 
     */
    @SuppressWarnings("serial")
    private static class ChangeMarkingHashMap<A, B> extends HashMap<A, B> {
        public Set<A> alteredKeys = new HashSet<A>();
        public ChangeMarkingHashMap(Map<A, B> savedState) {
            super(savedState);
        }
        @Override
        public B put(A key, B newValue) {
            B originalValue = super.put(key, newValue);
            if (originalValue != newValue)
                alteredKeys.add(key);
            return originalValue;
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
