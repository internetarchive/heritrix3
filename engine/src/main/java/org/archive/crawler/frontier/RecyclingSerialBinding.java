/* RecyclingSerialBinding
*
* $Id$
*
* Created on May 25, 2005
*
* Copyright (C) 2005 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
