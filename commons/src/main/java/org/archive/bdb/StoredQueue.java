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

import java.io.Serializable;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;


import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * Queue backed by a JE Collections StoredSortedMap. 
 * 
 * @author gojomo
 *
 * @param <E>
 */
public class StoredQueue<E extends Serializable> extends AbstractQueue<E>  
implements Serializable {
    private static final long serialVersionUID = 3L;
    private static final Logger logger =
        Logger.getLogger(StoredQueue.class.getName());

    transient StoredSortedMap<Long,E> queueMap; // Long -> E
    transient Database queueDb; // Database
    AtomicLong tailIndex; // next spot for insert
    AtomicLong headIndex; // next spot for read
    transient E peekItem = null;
    
    /**
     * Create a StoredQueue backed by the given Database. 
     * 
     * The Class of values to be queued may be provided; there is only a 
     * benefit when a primitive type is specified. A StoredClassCatalog
     * must be provided if a primitive type is not supplied. 
     * 
     * @param db
     * @param clsOrNull 
     * @param classCatalog
     */
    public StoredQueue(Database db, Class<E> clsOrNull, StoredClassCatalog classCatalog) {
        tailIndex = new AtomicLong(0);
        headIndex = new AtomicLong(0);
        hookupDatabase(db, clsOrNull, classCatalog);
    }

    /**
     * @param db
     * @param clsOrNull
     * @param classCatalog
     */
    public void hookupDatabase(Database db, Class<E> clsOrNull, StoredClassCatalog classCatalog) {
        EntryBinding<E> valueBinding = TupleBinding.getPrimitiveBinding(clsOrNull);
        if(valueBinding == null) {
            valueBinding = new SerialBinding<E>(classCatalog, clsOrNull);
        }
        queueDb = db;
        queueMap = new StoredSortedMap<Long,E>(
                db,
                TupleBinding.getPrimitiveBinding(Long.class),
                valueBinding,
                true);
    }

    @Override
    public Iterator<E> iterator() {
        return queueMap.values().iterator();
    }

    @Override
    public int size() {
        synchronized(tailIndex) {
            synchronized(headIndex) {
                return (int)(tailIndex.get()-headIndex.get());
            }
        }
        
    }

    public boolean offer(E o) {
        synchronized (tailIndex) {
            queueMap.put(tailIndex.getAndIncrement(), o);
        }
        return true;
    }

    public E peek() {
        synchronized (headIndex) {
            if(peekItem != null) {
                return peekItem;
            }
            E head = null;
            while(head == null && headIndex.get() < tailIndex.get()) {
                head = (E) queueMap.get(headIndex.get());
                if(head != null) {
                    peekItem = head;
                    return head;
                }
                // ERROR; should never be null with headIndex < tailIndex
                logger.log(Level.SEVERE,
                        "unexpected empty index of StoredQueue("
                        + queueDb.getDatabaseName()+"): "
                        + headIndex.get() + " (tailIndex: " 
                        + tailIndex.get(),new Exception());
                headIndex.incrementAndGet();
            }
            return head;
        }
    }

    public E poll() {
        synchronized (headIndex) {
            E head = peek();
            if(head!=null) {
                queueMap.remove(headIndex.getAndIncrement());
                peekItem = null;
                return head;
            } else {
                return null;
            }
        }
    }

    /**
     * A suitable DatabaseConfig for the Database backing a StoredQueue. 
     * (However, it is not necessary to use these config options.)
     * 
     * @return DatabaseConfig suitable for queue
     */
    public static BdbModule.BdbConfig databaseConfig() {
        BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(true);
        return dbConfig;
    }
    
    /**
     * Save the state to a stream (that is, serialize it).
     *
     * @serialData The capacity is emitted (int), followed by all of
     * its elements (each an <tt>Object</tt>) in the proper order,
     * followed by a null
     * @param s the stream
     */
    private void writeObject(java.io.ObjectOutputStream s)
        throws java.io.IOException {
        try {
            queueDb.sync();
        } catch (DatabaseException e) {
            throw new RuntimeException(e); 
        } 
        s.defaultWriteObject();
    }

    public void close() {
        try {
            queueDb.sync();
            queueDb.close();
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
    }
}