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

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.Pool;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.je.DatabaseEntry;

/**
 * Binding for use with BerkeleyDB-JE that uses Kryo serialization rather
 * than BDB's (custom version of) Java serialization.
 *
 * @author gojomo
 */
public class KryoBinding<K> implements EntryBinding<K> {
    private static final int POOL_SIZE = 8;

    protected final Class<K> baseClass;

    Pool<AutoKryo> kryoPool = new Pool<AutoKryo>(true, false, POOL_SIZE) {
        protected AutoKryo create () {
            AutoKryo kryo = new AutoKryo();
            kryo.autoregister(baseClass);
            kryo.setRegistrationRequired(false);
            kryo.setWarnUnregisteredClasses(true);
            return kryo;
        }
    };

    Pool<Output> outputPool = new Pool<Output>(true, false, POOL_SIZE) {
        protected Output create () {
            return new Output(16 * 1024, -1);
        }
    };

    public KryoBinding(Class<K> baseClass) {
        this.baseClass = baseClass;
    }

    /**
     * Copies superclass simply to allow different source for FastOoutputStream.
     *
     * @see com.sleepycat.bind.serial.SerialBinding#entryToObject
     */
    public void objectToEntry(K object, DatabaseEntry entry) {
        AutoKryo kryo = kryoPool.obtain();
        try {
            Output output = outputPool.obtain();
            try {
                kryo.writeObject(output, object);
                entry.setData(output.toBytes());
            } finally {
                outputPool.free(output);
            }
        } finally {
            kryoPool.free(kryo);
        }
    }

    @Override
    public K entryToObject(DatabaseEntry entry) {
        AutoKryo kryo = kryoPool.obtain();
        try {
            return kryo.readObject(new Input(entry.getData()), baseClass);
        } finally {
            kryoPool.free(kryo);
        }
    }
}
