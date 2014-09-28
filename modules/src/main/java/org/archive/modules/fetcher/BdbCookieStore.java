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

import it.unimi.dsi.mg4j.util.MutableString;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.archive.bdb.BdbModule;
import org.archive.checkpointing.Checkpoint;
import org.springframework.beans.factory.annotation.Autowired;

import com.sleepycat.bind.ByteArrayBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

public class BdbCookieStore extends AbstractCookieStore implements CookieStore {

    /**
     * Needed because httpclient requires List<Cookie> even though it only uses
     * methods available on Collection. (+1 for python, -1 for java on this one)
     */
    public static class CollectionListFacade<T> implements List<T> {
        private Collection<T> wrapped;
        public CollectionListFacade(Collection<T> wrapped) { this.wrapped = wrapped; }
        @Override public int size() { return wrapped.size(); }
        @Override public boolean isEmpty() { return wrapped.isEmpty(); }
        @Override public boolean contains(Object o) { return wrapped.contains(o); }
        @Override public Iterator<T> iterator() { return wrapped.iterator(); }
        @Override public Object[] toArray() { return wrapped.toArray(); }
        @SuppressWarnings("hiding") @Override public <T> T[] toArray(T[] a) { return wrapped.toArray(a); }
        @Override public boolean add(T e) { throw new RuntimeException("immutable list"); }
        @Override public boolean remove(Object o) { throw new RuntimeException("immutable list"); }
        @Override public boolean containsAll(Collection<?> c) { return wrapped.containsAll(c); }
        @Override public boolean addAll(Collection<? extends T> c) { throw new RuntimeException("immutable list"); }
        @Override public boolean addAll(int index, Collection<? extends T> c) { throw new RuntimeException("immutable list"); }
        @Override public boolean removeAll(Collection<?> c) { throw new RuntimeException("immutable list"); }
        @Override public boolean retainAll(Collection<?> c) { throw new RuntimeException("immutable list"); }
        @Override public void clear() { throw new RuntimeException("immutable list"); }
        @Override public T get(int index) { throw new RuntimeException("not implemented"); }
        @Override public T set(int index, T element) { throw new RuntimeException("immutable list"); }
        @Override public void add(int index, T element) { throw new RuntimeException("immutable list"); }
        @Override public T remove(int index) { throw new RuntimeException("immutable list"); }
        @Override public int indexOf(Object o) { throw new RuntimeException("not implemented"); }
        @Override public int lastIndexOf(Object o) { throw new RuntimeException("not implemented"); }
        @Override public ListIterator<T> listIterator() { throw new RuntimeException("not implemented"); }
        @Override public ListIterator<T> listIterator(int index) { throw new RuntimeException("not implemented"); }
        @Override public List<T> subList(int fromIndex, int toIndex) { throw new RuntimeException("not implemented"); }
    }

    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }

    public static String COOKIEDB_NAME = "hc_httpclient_cookies";

    private transient Database cookieDb;
    private transient StoredSortedMap<byte[],Cookie> cookies;

    public void prepare() {
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
            dbConfig.setTransactional(false);
            dbConfig.setAllowCreate(true);
            dbConfig.setSortedDuplicates(false);
            cookieDb = bdb.openDatabase(COOKIEDB_NAME, dbConfig,
                    isCheckpointRecovery);
            cookies = new StoredSortedMap<byte[],Cookie>(cookieDb,
                    new ByteArrayBinding(), 
                    new SerialBinding<Cookie>(classCatalog, Cookie.class), 
                    true);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void addCookie(Cookie cookie) {
        byte[] key;
        try {
            key = makeSortKey(cookie).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // impossible
        }

        if (!cookie.isExpired(new Date())) {
            cookies.put(key, cookie);
        } else {
            cookies.remove(key);
        }
    }
    
    public CookieStore cookieStoreFor(String topPrivateDomain) {
        // XXX "the natural ordering of a stored collection is data byte order" -- explain more about subMap 
        SortedMap<byte[], Cookie> domainCookiesSubMap;
        try {
            byte[] startKey = topPrivateDomain.getBytes("UTF-8");
            byte[] endKey = (topPrivateDomain + ".").getBytes("UTF-8");
            domainCookiesSubMap = cookies.subMap(startKey, endKey);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // impossible
        }
        
        Collection<Cookie> domainCookiesCollection = domainCookiesSubMap.values();
        List<Cookie> domainCookiesList = new CollectionListFacade<Cookie>(domainCookiesCollection);
        
        return new DomainCookieStore(domainCookiesList);
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
    
    /** are we a checkpoint recovery? (in which case, reuse stored cookie data?) */
    protected boolean isCheckpointRecovery = false; 
    @Override
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        // just remember that we are doing checkpoint-recovery;
        // actual state recovery happens via BdbModule
        isCheckpointRecovery = true;
    }
    
    public void saveCookies(String saveCookiesFile) {
        // Do nothing if cookiesFile is not specified.
        if (saveCookiesFile == null || saveCookiesFile.length() <= 0) {
            return;
        }

        FileOutputStream out = null;
        try {
            out = new FileOutputStream(new File(saveCookiesFile));
            String tab ="\t";
            out.write("# Heritrix Cookie File\n".getBytes());
            out.write("# This file is the Netscape cookies.txt format\n\n".getBytes());
            for (Cookie cookie: cookies.values()) {
                // Guess an initial size
                MutableString line = new MutableString(1024 * 2);
                line.append(cookie.getDomain());
                line.append(tab);
                // XXX line.append(cookie.isDomainAttributeSpecified() ? "TRUE" : "FALSE");
                line.append("TRUE");
                line.append(tab);
                line.append(cookie.getPath() != null ? cookie.getPath() : "/");
                line.append(tab);
                line.append(cookie.isSecure() ? "TRUE" : "FALSE");
                line.append(tab);
                line.append(cookie.getExpiryDate() != null ? cookie.getExpiryDate().getTime() / 1000 : -1);
                line.append(tab);
                line.append(cookie.getName());
                line.append(tab);
                line.append(cookie.getValue() != null ? cookie.getValue() : "");
                line.append("\n");
                out.write(line.toString().getBytes());
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Unable to write " + saveCookiesFile, e);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    @Override
    public void clear() {
        cookies.clear();
    }

    /**
     * @return an immutable list view of the cookies
     */
    @Override
    public List<Cookie> getCookies() {
        if (cookies != null) {
            return new CollectionListFacade<Cookie>(cookies.values());
        } else {
            return null;
        }
    }

    @Override
    public boolean clearExpired(Date date) {
        throw new RuntimeException("note implemented");
    }
    
    @Override
    public String toString() {

        Iterator<Entry<byte[], Cookie>> i = cookies.entrySet().iterator();
        if (! i.hasNext())
            return "{}";

        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (;;) {
            Entry<byte[], Cookie> e = i.next();
            String key;
            try {
                key = new String(e.getKey(), "UTF-8");
            } catch (UnsupportedEncodingException e1) {
                throw new RuntimeException();
            }
            Cookie value = e.getValue();
            sb.append(key);
            sb.append('=');
            sb.append(value == this ? "(this Map)" : value);
            if (! i.hasNext())
                return sb.append('}').toString();
            sb.append(',').append(' ');
        }
    }
}
