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
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieIdentityComparator;
import org.archive.bdb.BdbModule;
import org.archive.checkpointing.Checkpoint;
import org.springframework.beans.factory.annotation.Autowired;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

public class BdbCookieStore extends AbstractCookieStore {

    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }

    /** are we a checkpoint recovery? (in which case, reuse stored cookie data?) */
    protected boolean isCheckpointRecovery = false; 
    
    public static String COOKIEDB_NAME = "hc_httpclient_cookies";
 
    private transient Database cookieDb;
    private transient StoredSortedMap<String,Cookie> cookies;
    private transient List<Cookie> cachedCookieList;

    public void prepare() {
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
            dbConfig.setTransactional(false);
            dbConfig.setAllowCreate(true);
            cookieDb = bdb.openDatabase(COOKIEDB_NAME, dbConfig,
                    isCheckpointRecovery);
            cookies = new StoredSortedMap<String, Cookie>(cookieDb,
                    new StringBinding(), new SerialBinding<Cookie>(
                            classCatalog, Cookie.class), true);
            cachedCookieList = new ArrayList<Cookie>(cookies.values());
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
    }
    
    private transient Comparator<Cookie> cookieIdentityComparator = new CookieIdentityComparator();
    protected void removeFromCachedList(Cookie c) {
        for (int i = 0; i < cachedCookieList.size(); i++) {
            if (cookieIdentityComparator.compare(cachedCookieList.get(i), c) == 0) {
                cachedCookieList.remove(i);
                break;
            }
        }
    }
    
    /**
     * Adds an {@link Cookie HTTP cookie}, replacing any existing equivalent cookies.
     * If the given cookie has already expired it will not be added, but existing
     * values will still be removed.
     *
     * @param cookie the {@link Cookie cookie} to be added
     */
    @Override
    public synchronized void addCookie(Cookie cookie) {
        if (cookie != null) {
            String key = makeKey(cookie);
            
            // first remove any old cookie that is equivalent
            Cookie removed = cookies.remove(key);
            if (removed != null) {
                removeFromCachedList(removed);
            }
            
            if (!cookie.isExpired(new Date())) {
                cookies.put(key, cookie);
                cachedCookieList.add(cookie);
            }
        }
    }

    /**
     * Returns an immutable array of {@link Cookie cookies} that this HTTP
     * state currently contains.
     *
     * @return an array of {@link Cookie cookies}.
     */
    @Override
    public List<Cookie> getCookies() {
        return cachedCookieList;
    }
    
    protected List<Cookie> getCookiesBypassCache() {
        if (cookies != null) {
            return new ArrayList<Cookie>(cookies.values());
        } else {
            return null;
        }
    }

    /**
     * Removes all of {@link Cookie cookies} in this HTTP state
     * that have expired by the specified {@link java.util.Date date}.
     *
     * @return true if any cookies were purged.
     *
     * @see Cookie#isExpired(Date)
     */
    @Override
    public synchronized boolean clearExpired(final Date date) {
        if (date == null) {
            return false;
        }
        boolean removed = false;
        for (String key: cookies.keySet()) {
            if (cookies.get(key).isExpired(date)) {
                Cookie c = cookies.remove(key);
                cachedCookieList.remove(c);
                removed = true;
            }
        }
        return removed;
    }

    /**
     * Clears all cookies.
     */
    @Override
    public synchronized void clear() {
        cookies.clear();
        cachedCookieList.clear();
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

    
    @Override
    protected void loadCookies(Reader reader) {
        Collection<Cookie> loadedCookies = readCookies(reader);
        for (Cookie cookie: loadedCookies) {
            addCookie(cookie);
        }
    }

    @Override
    protected void saveCookies(String absolutePath) {
        saveCookies(absolutePath, cookies.values());
    }
}
