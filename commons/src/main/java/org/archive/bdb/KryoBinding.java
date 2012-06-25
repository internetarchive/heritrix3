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
package org.archive.bdb;

import java.lang.ref.WeakReference;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.ObjectBuffer;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * Binding for use with BerkeleyDB-JE that uses Kryo serialization rather
 * than BDB's (custom version of) Java serialization.
 * 
 * @contributor gojomo
 */
public class KryoBinding<K> implements EntryBinding<K> {

    protected Class<K> baseClass;
    protected AutoKryo kryo = new AutoKryo(); 
    protected ThreadLocal<WeakReference<ObjectBuffer>> threadBuffer = new ThreadLocal<WeakReference<ObjectBuffer>>() {
        @Override
        protected WeakReference<ObjectBuffer> initialValue() {
            return new WeakReference<ObjectBuffer>(new ObjectBuffer(kryo,16*1024,Integer.MAX_VALUE));
        }
    };
    
    /**
     * Constructor. Save parameters locally, as superclass 
     * fields are private. 
     * 
     * @param classCatalog is the catalog to hold shared class information
     *
     * @param baseClass is the base class for serialized objects stored using
     * this binding
     */
    public KryoBinding(Class<K> baseClass) {
        this.baseClass = baseClass;
        kryo.autoregister(baseClass);
        // TODO: reevaluate if explicit registration should be required
        kryo.setRegistrationOptional(true);
    }

    public Kryo getKryo() {
        return kryo;
    }
    
    private ObjectBuffer getBuffer() {
        WeakReference<ObjectBuffer> ref = threadBuffer.get();
        ObjectBuffer ob = ref.get();
        if (ob == null) {
            ob = new ObjectBuffer(kryo,16*1024,Integer.MAX_VALUE);
            threadBuffer.set(new WeakReference<ObjectBuffer>(ob));
        }
        return ob;        
    }
    
    /**
     * Copies superclass simply to allow different source for FastOoutputStream.
     * 
     * @see com.sleepycat.bind.serial.SerialBinding#entryToObject
     */
    public void objectToEntry(K object, DatabaseEntry entry) {
        entry.setData(getBuffer().writeObjectData(object));
    }

    @Override
    public K entryToObject(DatabaseEntry entry) {
        return getBuffer().readObjectData(entry.getData(), baseClass);
    }
}
