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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.SortedMap;

import org.apache.commons.httpclient.Cookie;
import org.archive.bdb.BdbModule;
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
public class BdbCookieStorage extends AbstractCookieStorage {
    private static final long serialVersionUID = 1L;
    
    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }
    
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
            cookieDb = bdb.openDatabase(COOKIEDB_NAME, dbConfig, true);
            cookies = 
                new StoredSortedMap<String,Cookie>(
                    cookieDb,
                    new StringBinding(), 
                    new SerialBinding<Cookie>(classCatalog,Cookie.class), 
                    true);
            @SuppressWarnings("unchecked")
            SortedMap<String,Cookie> result = cookies;
            return result;
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
    }


    @SuppressWarnings("unchecked")
    public SortedMap<String, Cookie> getCookiesMap() {
//        assert cookies != null : "cookie map not set up";
        return cookies;
    }


    protected void innerSaveCookiesMap(Map<String, Cookie> map) {
    }


    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        try {
            out.writeUTF(cookieDb.getDatabaseName());
        } catch (DatabaseException e) {
            IOException io = new IOException();
            io.initCause(e);
            throw io;
        }
    }
    
    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream in)
    throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        cookieDb = bdb.getDatabase(in.readUTF());
        cookies = new StoredSortedMap(cookieDb,
                new StringBinding(), new SerialBinding(bdb.getClassCatalog(),
                        Cookie.class), true);        
    }
    
}
