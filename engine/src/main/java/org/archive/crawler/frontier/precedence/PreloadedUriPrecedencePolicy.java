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

    @SuppressWarnings("unchecked")
    protected StoredSortedMap store;
    protected Database historyDb;
    
    @SuppressWarnings("unchecked")
    public void start() {
        if(isRunning()) {
            return;
        }
        String dbName = PersistProcessor.URI_HISTORY_DBNAME;
        StoredSortedMap historyMap;
        try {
            StoredClassCatalog classCatalog = bdb.getClassCatalog();
            BdbModule.BdbConfig dbConfig = PersistProcessor.HISTORY_DB_CONFIG;

            historyDb = bdb.openDatabase(dbName, dbConfig, true);
            historyMap = new StoredSortedMap(historyDb,
                    new StringBinding(), new SerialBinding(classCatalog,
                            Map.class), true);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
        store = historyMap;
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
    @SuppressWarnings("unchecked")
    protected void mergePrior(CrawlURI curi) {
        Map<String, Map> prior = null;
        String key = PersistProcessor.persistKeyFor(curi);
        prior = (Map<String,Map>) store.get(key);
        if(prior!=null) {
            // merge in keys
            curi.getData().putAll(prior); 
        }
    }
}
