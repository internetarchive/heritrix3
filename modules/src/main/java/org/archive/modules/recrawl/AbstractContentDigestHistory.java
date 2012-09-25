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

import org.archive.modules.CrawlURI;

/**
 * Represents a store of information, presumably persistent, keyed by content
 * digest.
 * 
 * @contributor nlevitt
 */
public abstract class AbstractContentDigestHistory {
    /**
     * Looks up the history by key {@code persistKeyFor(curi)} and loads it into
     * {@code curi.getContentDigestHistory()}.
     * 
     * @param curi
     */
    public abstract void load(CrawlURI curi);
    
    /**
     * Stores {@code curi.getContentDigestHistory()} for the key
     * {@code persistKeyFor(curi)}.
     * 
     * @param curi
     */
    public abstract void store(CrawlURI curi);

    /**
     * 
     * @param curi
     * @return {@code curi.getContentDigestSchemeString()}
     * @throws IllegalStateException if {@code curi.getContentDigestSchemeString()} is null
     */
    protected String persistKeyFor(CrawlURI curi) {
        String key = curi.getContentDigestSchemeString();
        if (key == null) {
            throw new IllegalStateException("cannot load content digest history, CrawlURI does not have content digest value for " + curi);
        }
        return key;
    }
}
