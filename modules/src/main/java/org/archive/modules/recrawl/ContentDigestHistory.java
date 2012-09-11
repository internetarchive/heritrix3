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
import org.archive.modules.CrawlURI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.sleepycat.bind.EntityBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/** Needs to be a toplevel bean for Lifecyle? */
public class ContentDigestHistory implements Lifecycle {

    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }
    
    protected String historyDbName = "contentDigestHistory";
    public String getHistoryDbName() {
        return this.historyDbName;
    }
    public void setHistoryDbName(String name) {
        this.historyDbName = name; 
    }

    protected StoredSortedMap<String,Map<String,Object>> store;
    protected Database historyDb;

    protected String persistKeyFor(CrawlURI curi) {
        return curi.getContentDigestSchemeString();
    }
    
    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void start() {
        if (isRunning()) {
            return;
        }
        StoredSortedMap<String, Map<String, Object>> historyMap;
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            historyDb = bdb.openDatabase(getHistoryDbName(), historyDbConfig(), true);
            historyMap = new StoredSortedMap<String, Map<String,Object>>(
                        historyDb,
                        new StringBinding(),
                        (EntityBinding<Map<String, Object>>) new SerialBinding<Map>(classCatalog, Map.class),
                        true);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        store = historyMap;
    }

    @Override
    public boolean isRunning() {
        return historyDb != null; 
    }

    @Override
    public void stop() {
        if (!isRunning()) {
            return;
        }
        // leave other cleanup to BdbModule
        historyDb = null;
    }
    
    protected transient BdbModule.BdbConfig historyDbConfig;
    protected BdbModule.BdbConfig historyDbConfig() {
        if (historyDbConfig == null) {
            historyDbConfig = new BdbModule.BdbConfig();
            historyDbConfig.setTransactional(false);
            historyDbConfig.setAllowCreate(true);
            historyDbConfig.setDeferredWrite(true);
        }
        
        return historyDbConfig;
    }

    public void load(CrawlURI curi) {
        String pkey = persistKeyFor(curi);
        Map<String, Object> prior = store.get(pkey);
        if (prior != null) {
            // merge in keys
            prior.keySet().removeAll(curi.getData().keySet());
            curi.getData().putAll(prior);
        }
    }
    
    public void store(CrawlURI curi) {
        store.put(persistKeyFor(curi), curi.getPersistentDataMap());
    }
}