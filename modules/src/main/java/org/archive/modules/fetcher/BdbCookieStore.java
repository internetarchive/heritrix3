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
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.SortedMap;

import org.apache.commons.collections.collection.CompositeCollection;
import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.archive.bdb.BdbModule;
import org.archive.checkpointing.Checkpoint;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.net.InternetDomainName;
import com.sleepycat.bind.ByteArrayBinding;
import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.collections.StoredCollection;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * Cookie store using bdb for storage. Cookies are stored in a SortedMap keyed
 * by {@link #sortableKey(Cookie)}, so they are grouped together by domain.
 * {@link #cookieStoreFor(String)} returns a facade whose
 * {@link CookieStore#getCookies()} returns a list of cookies limited to
 * the supplied host and parent domains, if applicable.
 * 
 * @see https://webarchive.jira.com/browse/HER-2070
 * @see https://github.com/internetarchive/heritrix3/pull/96
 * @see https://groups.yahoo.com/neo/groups/archive-crawler/conversations/messages/8620
 * 
 * @contributor nlevitt
 */
public class BdbCookieStore extends AbstractCookieStore implements
        FetchHTTPCookieStore, CookieStore {

    /**
     * A {@link List} implementation that wraps a {@link Collection}. Needed
     * because httpclient requires {@code List<Cookie>}.
     * 
     * <p>
     * This class is "restricted" in the sense that it is immutable, and also
     * because some methods throw {@link RuntimeException} for other reasons.
     * For example, {@link #iterator()} is not implemented, because we use this
     * class to wrap a bdb {@link StoredCollection}, and iterators from that
     * class need to be explicitly closed. Since this class hides the fact that
     * a StoredCollection underlies it, we simply prevent {@link #iterator()}
     * from being used.
     */
    public static class RestrictedCollectionWrappedList<T> implements List<T> {
        private Collection<T> wrapped;
        public RestrictedCollectionWrappedList(Collection<T> wrapped) { this.wrapped = wrapped; }
        @Override public int size() { return wrapped.size(); }
        @Override public boolean isEmpty() { throw new RuntimeException("not implemented"); }
        @Override public boolean contains(Object o) { throw new RuntimeException("not implemented"); }
        @Override public Iterator<T> iterator() { throw new RuntimeException("not implemented"); }
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

    public void addCookieImpl(Cookie cookie) {
        byte[] key;
        try {
            key = sortableKey(cookie).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // impossible
        }

        if (!cookie.isExpired(new Date())) {
            cookies.put(key, cookie);
        } else {
            cookies.remove(key);
        }
    }

    protected Collection<Cookie> hostSubset(String host) { 
        try {
            byte[] startKey = (host + ";").getBytes("UTF-8");

            char chAfterDelim = (char)(((int)';')+1); 
            byte[] endKey = (host + chAfterDelim).getBytes("UTF-8");

            SortedMap<byte[], Cookie> submap = cookies.subMap(startKey, endKey);
            return submap.values();

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // impossible
        }
    }
    
    /**
     * Returns a {@link LimitedCookieStoreFacade} whose
     * {@link LimitedCookieStoreFacade#getCookies()} method returns only cookies
     * from {@code host} and its parent domains, if applicable.
     */
    public CookieStore cookieStoreFor(String host) {
        CompositeCollection cookieCollection = new CompositeCollection();

        if (InternetDomainName.isValid(host)) {
            InternetDomainName domain = InternetDomainName.from(host);

            while (domain != null) {
                Collection<Cookie> subset = hostSubset(domain.toString());
                cookieCollection.addComposited(subset);

                if (domain.hasParent()) {
                    domain = domain.parent();
                } else {
                    domain = null;
                }
            }
        } else {
            Collection<Cookie> subset = hostSubset(host.toString());
            cookieCollection.addComposited(subset);
        }

        @SuppressWarnings("unchecked")
        List<Cookie> cookieList = new RestrictedCollectionWrappedList<Cookie>(cookieCollection);
        LimitedCookieStoreFacade store = new LimitedCookieStoreFacade(cookieList);
        return store;
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
            return new RestrictedCollectionWrappedList<Cookie>(cookies.values());
        } else {
            return null;
        }
    }

    @Override
    public boolean clearExpired(Date date) {
        throw new RuntimeException("not implemented");
    }
}
