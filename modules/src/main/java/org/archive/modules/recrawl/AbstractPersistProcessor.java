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

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_FETCH_HISTORY;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WRITE_TAG;

import java.util.Map;

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;

public abstract class AbstractPersistProcessor extends Processor {

    /** @see RecrawlAttributeConstants#A_WRITE_TAG */
    protected boolean onlyStoreIfWriteTagPresent = true;
    public boolean getOnlyStoreIfWriteTagPresent() {
        return onlyStoreIfWriteTagPresent;
    }
    public void setOnlyStoreIfWriteTagPresent(boolean onlyStoreIfWriteTagPresent) {
        this.onlyStoreIfWriteTagPresent = onlyStoreIfWriteTagPresent;
    }

    /**
     * Whether the current CrawlURI's state should be persisted (to log or
     * direct to database)
     * 
     * @param curi
     *            CrawlURI
     * @return true if state should be stored; false to skip persistence
     */
    protected boolean shouldStore(CrawlURI curi) {
        // do this first for quick decision on CURLs postponed by prerequisite
        if (!curi.isSuccess()) {
            return false;
        }
        
        // DNS query need not be persisted
        String scheme = curi.getUURI().getScheme();
        if (!(scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp"))) {
            return false;
        }
        
        if (getOnlyStoreIfWriteTagPresent() && !hasWriteTag(curi)) { 
            return false;
        }
        
        return true;
    }

    /**
     * @param curi
     * @return true if {@code curi} has WRITE_TAG in the latest fetch history (i.e. this crawl).
     */
    @SuppressWarnings("unchecked")
    protected boolean hasWriteTag(CrawlURI uri) {
        Map<String,Object>[] history = (Map<String,Object>[])uri.getData().get(A_FETCH_HISTORY);
        return history != null && history[0] != null && history[0].containsKey(A_WRITE_TAG);
    }
    
    /**
     * Whether the current CrawlURI's state should be loaded
     * 
     * @param curi CrawlURI
     * @return true if state should be loaded; false to skip loading
     */
    protected boolean shouldLoad(CrawlURI curi) {
        // TODO: don't load some (prereqs?)
        return true;
    }

}
