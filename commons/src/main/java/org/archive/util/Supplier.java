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

/**
 * Class for optionally providing one instance of the parameterized
 * type. The one instance may be provided at construction, or by 
 * overriding get() in subclasses, may be created on demand. 
 * 
 * @param <V>
 */
public class Supplier<V> {
    protected V instance;
    
    public Supplier() {
        super();
    }
    
    public Supplier(V instance) {
        super();
        this.instance = instance;
    }

    public V get() {
        return instance;
    }
}