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
package org.archive.crawler.frontier;

import java.io.IOException;

import com.sleepycat.bind.serial.ClassCatalog;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.SerialOutput;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.util.FastOutputStream;
import com.sleepycat.util.RuntimeExceptionWrapper;

/**
 * A SerialBinding that recycles a single FastOutputStream per
 * thread, avoiding reallocation of the internal buffer for 
 * either repeated serializations or because of mid-serialization
 * expansions. (Cached stream's buffer will quickly grow to a size 
 * sufficient for all serialized instances.)
 *
 * @author gojomo
 */
public class RecyclingSerialBinding<K> extends SerialBinding<K> {
    /**
     * Thread-local cache of reusable FastOutputStream
     */
    ThreadLocal<FastOutputStream> fastOutputStreamHolder
     = new ThreadLocal<FastOutputStream>();
    
    private ClassCatalog classCatalog;
    private Class<K> baseClass;

    /**
     * Constructor. Save parameters locally, as superclass 
     * fields are private. 
     * 
     * @param classCatalog is the catalog to hold shared class information
     *
     * @param baseClass is the base class for serialized objects stored using
     * this binding
     */
    @SuppressWarnings("unchecked")
    public RecyclingSerialBinding(ClassCatalog classCatalog, Class baseClass) {
        super(classCatalog, baseClass);
        this.classCatalog = classCatalog;
        this.baseClass = baseClass;
    }

    /**
     * Copies superclass simply to allow different source for FastOoutputStream.
     * 
     * @see com.sleepycat.bind.serial.SerialBinding#entryToObject
     */
    public void objectToEntry(Object object, DatabaseEntry entry) {

        if (baseClass != null && !baseClass.isInstance(object)) {
            throw new IllegalArgumentException(
                        "Data object class (" + object.getClass() +
                        ") not an instance of binding's base class (" +
                        baseClass + ')');
        }
        FastOutputStream fo = getFastOutputStream();
        try {
            SerialOutput jos = new SerialOutput(fo, classCatalog);
            jos.writeObject(object);
        } catch (IOException e) {
            throw new RuntimeExceptionWrapper(e);
        }

        byte[] hdr = SerialOutput.getStreamHeader();
        entry.setData(fo.getBufferBytes(), hdr.length,
                     fo.getBufferLength() - hdr.length);
    }

    /**
     * Get the cached (and likely pre-grown to efficient size) FastOutputStream,
     * creating it if necessary. 
     * 
     * @return FastOutputStream
     */
    private FastOutputStream getFastOutputStream() {
        FastOutputStream fo = (FastOutputStream) fastOutputStreamHolder.get();
        if (fo == null) {
            fo = new FastOutputStream();
            fastOutputStreamHolder.set(fo);
        }
        fo.reset();
        return fo;
    }
}
