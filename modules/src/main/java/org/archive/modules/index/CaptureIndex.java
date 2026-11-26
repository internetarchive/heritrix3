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

package org.archive.modules.index;

import java.util.List;

import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;

/**
 * Index of the response and revisit records written to WARC files, so that captures can be found
 * again during the crawl. Captures are keyed by the CrawlURI's canonical string, so two URLs are
 * the same resource here exactly when the frontier considers them the same.
 */
public interface CaptureIndex {
    /**
     * Indexes the records just written for a CrawlURI. Only GET requests with a canonical string
     * are indexed.
     *
     * @param records the records just written for it
     */
    void add(CrawlURI curi, Iterable<WARCRecordInfo> records);

    /**
     * Returns the most recent capture of a URL, or null if there is none.
     *
     * @param canonicalUri the URL as canonicalized by the frontier
     */
    Capture latest(String canonicalUri);

    /**
     * Returns captures in index order: by URL key, then newest first. Call again with the last
     * capture returned as {@code after} to get the next batch.
     *
     * @param urlKey the URL key to match
     * @param exact  whether the URL key must match exactly rather than as a prefix
     * @param after  the capture to start after, or null to start at the beginning
     * @param limit  the maximum number of captures to return
     */
    List<Capture> query(String urlKey, boolean exact, Capture after, int limit);
}
