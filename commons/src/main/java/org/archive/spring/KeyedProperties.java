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
 
 package org.archive.spring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map for storing overridable properties. 
 * 
 * An object wanting to allow its properties to be overridden 
 * contextually will store those properties in this map. Its 
 * accessors (like getProp() and setProp()) will only pass-through
 * to the 'prop' entry in this map.)
 * 
 */
public class KeyedProperties extends ConcurrentHashMap<String,Object> {
    private static final long serialVersionUID = 3403222335436162778L;
    /** the alternate global property-paths leading to this map 
     * TODO: consider if deterministic ordered list is important */
    HashSet<String> externalPaths = new HashSet<String>(); 
    
    /**
     * Add a path by which the outside world can reach this map
     * @param path String path
     */
    public void addExternalPath(String path) {
        externalPaths.add(path);
    }

    /**
     * Get the given value, checking override maps if appropriate.
     * 
     * @param key
     * @return discovered override, or local value
     */
    public Object get(String key) {
        ArrayList<OverlayContext> overlays = threadOverrides.get();
        for(int i = overlays.size()-1; i>=0; i--) {
            OverlayContext ocontext = overlays.get(i); 
            for(int j = ocontext.getOverlayNames().size()-1; j>=0; j--) {
                String name = ocontext.getOverlayNames().get(j);
                Map<String,Object> m = ocontext.getOverlayMap(name);
                for(String ok : getOverrideKeys(key)) {
                    Object val = m.get(ok);
                    if(val!=null) {
                        return val;
                    }
                }
            }
        }

        return super.get(key);
    }

    /**
     * Compose the complete keys (externalPath + local key name) to use
     * for checking for contextual overrides. 
     * 
     * @param key local key to compose
     * @return List of full keys to check
     */
    protected List<String> getOverrideKeys(String key) {
        ArrayList<String> keys = new ArrayList<String>(externalPaths.size());
        for(String path : externalPaths) {
            keys.add(path+"."+key);
        }
        return keys;
    }

    
    //
    // CLASS SERVICES
    //
    
    /**
     * ThreadLocal (contextual) collection of pushed override maps
     */
    static ThreadLocal<ArrayList<OverlayContext>> threadOverrides = 
        new ThreadLocal<ArrayList<OverlayContext>>() {
        protected ArrayList<OverlayContext> initialValue() {
            return new ArrayList<OverlayContext>();
        }
    };
    /**
     * Add an override map to the stack 
     * @param m Map to add
     */
    static public void pushOverrideContext(OverlayContext ocontext) {
        threadOverrides.get().add(ocontext);
    }
    
    /**
     * Remove last-added override map from the stack
     * @return Map removed
     */
    static public OverlayContext popOverridesContext() {
        // TODO maybe check that pop is as expected
        return threadOverrides.get().remove(threadOverrides.get().size()-1);
    }
    
    static public void clearAllOverrideContexts() {
        threadOverrides.get().clear(); 
    }
    
    static public void loadOverridesFrom(OverlayContext ocontext) {
        assert ocontext.haveOverlayNamesBeenSet();
        pushOverrideContext(ocontext);
    }
    
    static public boolean clearOverridesFrom(OverlayContext ocontext) {
        return threadOverrides.get().remove(ocontext);
    }
    
    static public void withOverridesDo(OverlayContext ocontext, Runnable todo) {
        try {
            loadOverridesFrom(ocontext);
            todo.run();
        } finally {
            clearOverridesFrom(ocontext); 
        }
    }

    public static boolean overridesActiveFrom(OverlayContext ocontext) {
        return threadOverrides.get().contains(ocontext);
    }
}
