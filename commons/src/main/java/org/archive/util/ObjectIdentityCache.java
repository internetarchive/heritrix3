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

import java.io.Closeable;
import java.util.Set;

/**
 * An object cache for create-once-by-name-and-then-reuse objects. 
 * 
 * Objects are added, but never removed. Subsequent get()s using the 
 * same key will return the exact same object, UNLESS all such objects
 * have been forgotten, in which case a new object MAY be returned. 
 * 
 * This allows implementors (such as ObjectIdentityBdbCache or 
 * CachedBdbMap) to page out (aka 'expunge') instances to
 * persistent storage while they're not being used. However, as long as
 * they are used (referenced), all requests for the same-named object
 * will share a reference to the same object, and the object may be
 * mutated in place without concern for explicitly persisting its
 * state to disk.  
 * 
 * @param <V>
 */
public interface ObjectIdentityCache<V extends IdentityCacheable> extends Closeable {
    /** get the object under the given key/name -- but should not mutate 
     * object state*/
    public abstract V get(final String key);
    
    /** get the object under the given key/name, using (and remembering)
     * the object supplied by the supplier if no prior mapping exists 
     * -- but should not mutate object state */
    public abstract V getOrUse(final String key, Supplier<V> supplierOrNull);

    /** force the persistent backend, if any, to be updated with all 
     * live object state */ 
    public abstract void sync();
    
    /** force the persistent backend, if any, to eventually be updated with 
     * live object state for the given key */ 
    public abstract void dirtyKey(final String key);

    /** close/release any associated resources */ 
    public abstract void close();
    
    /** count of name-to-object contained */ 
    public abstract int size();

    /** set of all keys */ 
    public abstract Set<String> keySet();
}