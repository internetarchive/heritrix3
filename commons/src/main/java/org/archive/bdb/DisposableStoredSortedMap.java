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

import java.util.logging.Level;
import java.util.logging.Logger;

import com.sleepycat.bind.EntityBinding;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.collections.PrimaryKeyAssigner;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * TempStoredSortedMap remembers its backing Database, and offers
 * a dispose() method for closing/discarding the underlying Database. 
 * 
 * You *must* call dispose() when done with the map; otherwise temporary data
 * will remain in a temporary Database in the BDB environment indefinitely. 
 * 
 * @contributor gojomo
 * @param <K>
 * @param <V>
 */
public class DisposableStoredSortedMap<K,V> extends StoredSortedMap<K,V> {
    final private static Logger LOGGER = 
        Logger.getLogger(DisposableStoredSortedMap.class.getName()); 
    Database db; 
    
    public DisposableStoredSortedMap(Database db, EntryBinding<K> arg1, EntityBinding<V> arg2, boolean arg3) {
        super(db, arg1, arg2, arg3);
        this.db = db;
    }
    public DisposableStoredSortedMap(Database db, EntryBinding<K> arg1, EntityBinding<V> arg2, PrimaryKeyAssigner arg3) {
        super(db, arg1, arg2, arg3);
        this.db = db;
    }
    public DisposableStoredSortedMap(Database db, EntryBinding<K> arg1, EntryBinding<V> arg2, boolean arg3) {
        super(db, arg1, arg2, arg3);
        this.db = db;
    }
    public DisposableStoredSortedMap(Database db, EntryBinding<K> arg1, EntryBinding<V> arg2, PrimaryKeyAssigner arg3) {
        super(db, arg1, arg2, arg3);
        this.db = db;
    }

    public void dispose() {
        String name = null;
        try {
            if(this.db!=null) {
                name = this.db.getDatabaseName();
                this.db.close();
                this.db.getEnvironment().removeDatabase(null, name);
                this.db = null; 
            }
        } catch (DatabaseException e) {
            LOGGER.log(Level.WARNING, "Error closing db " + name, e);
        } 
    }
}
