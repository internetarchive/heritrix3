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

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * Binding for use with BerkeleyDB-JE that uses Kryo serialization rather
 * than BDB's (custom version of) Java serialization.
 * 
 * @author gojomo
 */
public class KryoBinding<K> implements EntryBinding<K> {

    protected Class<K> baseClass;

    // Setup ThreadLocal of Kryo instances
    public static final ThreadLocal<AutoKryo> kryos = new ThreadLocal<AutoKryo>() {
        @Override
        protected AutoKryo initialValue() {
            return new AutoKryo();
        };
    };

    // This caches a ThreadLocal output buffer for re-use.
    private ThreadLocal<WeakReference<ByteArrayOutputStream>> threadBuffer = new ThreadLocal<WeakReference<ByteArrayOutputStream>>() {
        @Override
        protected WeakReference<ByteArrayOutputStream> initialValue() {
            return new WeakReference<ByteArrayOutputStream>(
                    new ByteArrayOutputStream());
        }
    };
    
    /**
     * Constructor. Save parameters locally, as superclass 
     * fields are private. 
     * 
     * @param baseClass is the base class for serialized objects stored using
     * this binding
     */
    public KryoBinding(Class<K> baseClass) {
        this.baseClass = baseClass;
        // kryos.get().autoregister(baseClass);
    }

    private ByteArrayOutputStream getBuffer() {
        WeakReference<ByteArrayOutputStream> ref = threadBuffer.get();
        ByteArrayOutputStream ob = ref.get();
        if (ob == null) {
            ob = new ByteArrayOutputStream();
            threadBuffer
                    .set(new WeakReference<ByteArrayOutputStream>(ob));
        }
        return ob;
    }

    /**
     * Copies superclass simply to allow different source for FastOoutputStream.
     * 
     * @see com.sleepycat.bind.serial.SerialBinding#entryToObject
     */
    public void objectToEntry(K object, DatabaseEntry entry) {
        Output output = new Output(getBuffer());
        kryos.get().writeObject(output, object);
        entry.setData(output.getBuffer());
    }

    @Override
    public K entryToObject(DatabaseEntry entry) {
        Input bb = new Input(entry.getData());
        return kryos.get().readObjectOrNull(bb, baseClass);
    }
}
