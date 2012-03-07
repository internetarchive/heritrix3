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
        if (getOnlyStoreIfWriteTagPresent()) {
            @SuppressWarnings("unchecked")
            Map<String,Object>[] history = (Map<String,Object>[])curi.getData().get(A_FETCH_HISTORY);
            return history != null && history[0] != null && history[0].containsKey(A_WRITE_TAG);
        } else {
            return curi.isSuccess(); 
        }
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
