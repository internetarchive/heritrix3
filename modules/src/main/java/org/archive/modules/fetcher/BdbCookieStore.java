package org.archive.modules.fetcher;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.http.cookie.Cookie;
import org.archive.bdb.BdbModule;
import org.archive.checkpointing.Checkpoint;
import org.springframework.beans.factory.annotation.Autowired;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.collections.StoredSortedKeySet;
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
    private transient StoredSortedKeySet<Cookie> cookies;

    public void prepare() {
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
            dbConfig.setTransactional(false);
            dbConfig.setAllowCreate(true);
            cookieDb = bdb.openDatabase(COOKIEDB_NAME, dbConfig,
                    isCheckpointRecovery);
            cookies = new StoredSortedKeySet<Cookie>(cookieDb,
                    new SerialBinding<Cookie>(classCatalog, Cookie.class), true);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
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
            // first remove any old cookie that is equivalent
            cookies.remove(cookie);
            if (!cookie.isExpired(new Date())) {
                cookies.add(cookie);
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
    public synchronized List<Cookie> getCookies() {
        if (cookies != null) {
            //create defensive copy so it won't be concurrently modified
            return new ArrayList<Cookie>(cookies);
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
        for (Iterator<Cookie> it = cookies.iterator(); it.hasNext();) {
            if (it.next().isExpired(date)) {
                it.remove();
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
        loadCookies(reader, cookies);
    }

    @Override
    protected void saveCookies(String absolutePath) {
        saveCookies(absolutePath, cookies);
    }
}
