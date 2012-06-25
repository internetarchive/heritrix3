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
package org.archive.crawler.frontier.precedence;

import static org.archive.modules.CoreAttributeConstants.A_PRECALC_PRECEDENCE;

import java.util.Map;

import org.archive.bdb.BdbModule;
import org.archive.modules.CrawlURI;
import org.archive.modules.recrawl.PersistProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;

/**
 * UriPrecedencePolicy which assigns URIs a precedence from a value that 
 * was preloaded for them into the uri-history database. 
 * 
 * NOTE: Because this is a Lifecycle bean requiring start and stop, it
 * should not be instantiated as an anonymous inner bean. Rather, it 
 * should be a top-level named bean, then either autowired or placed-by-
 * reference into the frontier.
 */
public class PreloadedUriPrecedencePolicy extends BaseUriPrecedencePolicy 
implements Lifecycle {
    private static final long serialVersionUID = -1474685153995064123L;
    
    /** Backup URI precedence assignment policy to use. */
    {
        setDefaultUriPrecedencePolicy(new BaseUriPrecedencePolicy());
    }
    public UriPrecedencePolicy getDefaultUriPrecedencePolicy() {
        return (UriPrecedencePolicy) kp.get("defaultUriPrecedencePolicy");
    }
    public void setDefaultUriPrecedencePolicy(UriPrecedencePolicy policy) {
        kp.put("defaultUriPrecedencePolicy",policy);
    }

    // TODO: refactor to better share code with PersistOnlineProcessor
    protected BdbModule bdb;
    @Autowired
    public void setBdbModule(BdbModule bdb) {
        this.bdb = bdb;
    }

    protected StoredSortedMap<String, ?> store;
    protected Database historyDb;
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void start() {
        if(isRunning()) {
            return;
        }
        store = null;
        String dbName = PersistProcessor.URI_HISTORY_DBNAME;
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            BdbModule.BdbConfig dbConfig = PersistProcessor.HISTORY_DB_CONFIG;

            historyDb = bdb.openDatabase(dbName, dbConfig, true);
            SerialBinding sb = new SerialBinding(classCatalog, Map.class);
            StoredSortedMap historyMap = new StoredSortedMap(historyDb, new StringBinding(), sb, true);
            store = historyMap;
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean isRunning() {
        return historyDb != null; 
    }
    
    public void stop() {
        if(!isRunning()) {
            return;
        }
        
        // BdbModule will handle closing of DB
        // XXX happens at finish; move to teardown?
        historyDb = null;         
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.precedence.BaseUriPrecedencePolicy#uriScheduled(org.archive.crawler.datamodel.CrawlURI)
     */
    @Override
    public void uriScheduled(CrawlURI curi) {
        int precedence = calculatePrecedence(curi);
        if(precedence==0) {
            // fall back to configured default policy
            getDefaultUriPrecedencePolicy().uriScheduled(curi);
            return;
        }
        curi.setPrecedence(precedence);
        
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.precedence.BaseUriPrecedencePolicy#calculatePrecedence(org.archive.crawler.datamodel.CrawlURI)
     */
    @Override
    protected int calculatePrecedence(CrawlURI curi) {
        mergePrior(curi);
        Integer preloadPrecedence = (Integer) curi.getData().get(A_PRECALC_PRECEDENCE);
        if(preloadPrecedence==null) {
            return 0;
        }
        return super.calculatePrecedence(curi) + preloadPrecedence;
    }
    
    /**
     * Merge any data from the Map stored in the URI-history store into the 
     * current instance. 
     * 
     * TODO: ensure compatibility with use of PersistLoadProcessor; suppress
     * double-loading
     * @param curi CrawlURI to receive prior state data
     */
    protected void mergePrior(CrawlURI curi) {
        String key = PersistProcessor.persistKeyFor(curi);
        @SuppressWarnings({ "rawtypes", "unchecked" })
        Map<String,Map> prior = (Map<String, Map>) store.get(key);
        if(prior!=null) {
            // merge in keys
            curi.getData().putAll(prior); 
        }
    }
}
