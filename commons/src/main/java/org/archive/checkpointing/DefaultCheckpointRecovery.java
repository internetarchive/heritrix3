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

package org.archive.checkpointing;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation.
 * 
 * @author pjack
 *
 */
public class DefaultCheckpointRecovery implements CheckpointRecovery {

    
//    final private Map<Object,Map<Key,Object>> newSettings =
//        new IdentityHashMap<Object,Map<Key,Object>>();

    final private Map<URI,URI> uriTranslations = new HashMap<URI,URI>();

    final private Map<String,String> fileTranslations = 
        new HashMap<String,String>();

    final private String name;
    
    public DefaultCheckpointRecovery(String name) {
        this.name = name;
    }

    public String getRecoveredJobName() {
        return name;
    }

    public Map<String,String> getFileTranslations() {
        return fileTranslations;
    }

    public Map<URI,URI> getURITranslations() {
        return uriTranslations;
    }


//    public <T> void setState(Object module, Key<T> key, T value) {
//        Map<Key,Object> map = newSettings.get(module);
//        if (map == null) {
//            map = new HashMap<Key,Object>();
//            newSettings.put(module, map);
//        }
//        
//        map.put(key, value);
//    }


    public String translatePath(String path) {
        Map.Entry<String,String> match = null;
        for (Map.Entry<String,String> me: fileTranslations.entrySet()) {
            if (path.startsWith(me.getKey())) {
                if ((match == null) 
                || (match.getKey().length() < me.getKey().length())) {
                    match = me;
                }
            }
        }
        
        if (match == null) {
            return path;
        }
        
        int size = match.getKey().length();
        return match.getValue() + path.substring(size);
    }


    public URI translateURI(URI uri) {
        URI r = uriTranslations.get(uri);
        return r == null ? uri : r;
    }
    
    
//    public void apply(SingleSheet global) {
//        for (Map.Entry<Object,Map<Key,Object>> mod: newSettings.entrySet()) {
//            Object module = mod.getKey();
//            for (Map.Entry<Key,Object> me: mod.getValue().entrySet()) {
//                @SuppressWarnings("unchecked")
//                Key<Object> k = me.getKey();
//                global.set(module, k, me.getValue());
//            }
//        }
//    }
}
