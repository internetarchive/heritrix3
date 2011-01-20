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

import org.archive.bdb.AutoKryo;

/**
 * Wrapper allowing other objects to be held in an ObjectIdentityCache. 
 * 
 * @contributor gojomo
 * @param <K>
 */
public class IdentityCacheableWrapper<K> implements IdentityCacheable {
    private static final long serialVersionUID = 1L;
    
    K wrapped; 
    
    public IdentityCacheableWrapper(String key, K wrapped) {
        super();
        this.wrapped = wrapped;
        this.key = key;
    }

    public K get() {
        return wrapped; 
    }
    
    //
    // IdentityCacheable support
    //
    transient private ObjectIdentityCache<?> cache;
    String key;
    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void makeDirty() {
        cache.dirtyKey(getKey());
    }

    @Override
    public void setIdentityCache(ObjectIdentityCache<?> cache) {
        this.cache = cache; 
    } 
    
    //
    // AutoKryo suppport
    //
    public static void autoregisterTo(AutoKryo kryo) {
        kryo.register(IdentityCacheableWrapper.class);
        kryo.setRegistrationOptional(true); 
    }
}
