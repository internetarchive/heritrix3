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
 
package org.archive.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trivial all-in-memory object cache, using a single internal
 * ConcurrentHashMap.
 * 
 * @contributor gojomo
 * @param <V>
 */
public class ObjectIdentityMemCache<V extends IdentityCacheable> 
implements ObjectIdentityCache<V> {
    protected ConcurrentHashMap<String, V> map; 
    
    public ObjectIdentityMemCache() {
        map = new ConcurrentHashMap<String, V>();
    }
    
    public ObjectIdentityMemCache(int cap, float load, int conc) {
        map = new ConcurrentHashMap<String, V>(cap, load, conc);
    }

    public void close() {
        // do nothing
    }

    
    @Override
    public V get(String key) {
        // all gets are mutatable
        return getOrUse(key,null);
    }

    public V getOrUse(String key, Supplier<V> supplierOrNull) {
        V val = map.get(key); 
        if (val==null && supplierOrNull!=null) {
            val = supplierOrNull.get(); 
            V prevVal = map.putIfAbsent(key, val);
            if(prevVal!=null) {
                val = prevVal; 
            }
        }
        if (val != null) {
            val.setIdentityCache(this);
        }
        return val; 
    }

    public int size() {
        return map.size();
    }

    public Set<String> keySet() {
        return map.keySet();
    }

    public void sync() {
        // do nothing
    }

    @Override
    public void dirtyKey(String key) {
        // do nothing: memory is whole cache        
    }

    /**
     * Offer raw map access for convenience of checkpoint/recovery.
     * @return Map<String, V>
     */
    public Map<String, V> getMap() {
        return map;
    }
}
