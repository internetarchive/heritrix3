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

package org.archive.modules.fetcher;

import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;

import org.apache.commons.httpclient.Cookie;
import org.archive.bdb.BdbModule;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.springframework.beans.factory.annotation.Autowired;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * CookieStorage using BDB, so that cookies accumulated in large crawls
 * do not outgrow RAM. 
 * 
 * @author pjack
 */
public class BdbCookieStorage extends AbstractCookieStorage implements Checkpointable {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;
    
    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }
    
    /** are we a checkpoint recovery? (in which case, reuse stored cookie data?) */
    protected boolean isCheckpointRecovery = false; 
    
    public static String COOKIEDB_NAME = "http_cookies";
 
    private transient Database cookieDb;
    private transient StoredSortedMap<String,Cookie> cookies;

    public BdbCookieStorage() {
    }

    protected SortedMap<String,Cookie> prepareMap() {
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
            dbConfig.setTransactional(false);
            dbConfig.setAllowCreate(true);
            cookieDb = bdb.openDatabase(COOKIEDB_NAME, dbConfig, isCheckpointRecovery);
            cookies = 
                new StoredSortedMap<String,Cookie>(
                    cookieDb,
                    new StringBinding(), 
                    new SerialBinding<Cookie>(classCatalog,Cookie.class), 
                    true);
            return cookies;
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
    }

    public SortedMap<String, Cookie> getCookiesMap() {
//        assert cookies != null : "cookie map not set up";
        return cookies;
    }

    protected void innerSaveCookiesMap(Map<String, Cookie> map) {
    }

    

    @Override
    public void startCheckpoint(Checkpoint checkpointInProgress) {
        // do nothing; handled by map checkpoint via BdbModule
    }
    @Override
    public void doCheckpoint(Checkpoint checkpointInProgress)
            throws IOException {
        // do nothing; handled by map checkpoint via BdbModule
    }
    @Override
    public void finishCheckpoint(Checkpoint checkpointInProgress) {
        // do nothing; handled by map checkpoint via BdbModule
    }

    @Override
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        // just remember that we are doing checkpoint-recovery;
        // actual state recovery happens via BdbModule
        isCheckpointRecovery = true; 
    }

}
