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

package org.archive.modules.recrawl;

import java.util.Map;

import org.archive.bdb.BdbModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * Common superclass for persisting Processors which directly store/load
 * to persistence (as opposed to logging for batch load later). 
 * @author gojomo
 */
public abstract class PersistOnlineProcessor extends PersistProcessor 
implements Lifecycle {
    
    @SuppressWarnings("unused")
    private static final long serialVersionUID = -666479480942267268L;
    
    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }
    
    protected String historyDbName = "uri_history";
    public String getHistoryDbName() {
        return this.historyDbName;
    }
    public void setHistoryDbName(String name) {
        this.historyDbName = name; 
    }

    @SuppressWarnings("unchecked")
    protected StoredSortedMap<String,Map> store;
    protected Database historyDb;

    public PersistOnlineProcessor() {
    }

    @SuppressWarnings("unchecked")
    public void start() {
        // TODO: share single store instance between Load and Store processors
        // (shared context? EnhancedEnvironment?)

        if (isRunning()) {
            return;
        }
        StoredSortedMap<String,Map> historyMap;
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            BdbModule.BdbConfig dbConfig = HISTORY_DB_CONFIG;

            historyDb = bdb.openDatabase(getHistoryDbName(), dbConfig, true);
            historyMap = 
                new StoredSortedMap<String,Map>(
                        historyDb,
                        new StringBinding(), 
                        new SerialBinding<Map>(classCatalog,Map.class), 
                        true);
        } catch (DatabaseException e) {
        	throw new RuntimeException(e);
        }
        store = historyMap;
    }
    
    public boolean isRunning() {
        return historyDb != null; 
    }

    public void stop() {
        if (!isRunning()) {
            return;
        }
        // leave other cleanup to BdbModule
        historyDb = null;
    }

}