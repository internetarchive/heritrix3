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

import static org.archive.modules.CoreAttributeConstants.A_FETCH_BEGAN_TIME;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_CONTENT_DIGEST;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ETAG_HEADER;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_FETCH_HISTORY;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_REFERENCE_LENGTH;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_STATUS;

import java.util.HashMap;
import java.util.Map;

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.revisit.IdenticalPayloadDigestRevisit;
import org.archive.modules.revisit.ServerNotModifiedRevisit;

/**
 * Maintain a history of fetch information inside the CrawlURI's attributes. 
 * 
 * @author gojomo
 * @version $Date: 2006-09-25 20:19:54 +0000 (Mon, 25 Sep 2006) $, $Revision: 4654 $
 */
public class FetchHistoryProcessor extends Processor {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;
    
    /** Desired history array length. */
    protected int historyLength = 2;
    public int getHistoryLength() {
        return this.historyLength;
    }
    public void setHistoryLength(int length) {
        this.historyLength = length;
    }
//    key description: "Number of previous fetch entries to retain in the URI " +
//    "history. The current fetch becomes a history entry at " +
//    "this Processor step, so the smallest useful value is " +
//    "'2' (including the current fetch). Default is '2'."
    
    // class description: "FetchHistoryProcessor. Maintain a history of fetch " +
    // "information inside the CrawlURI's attributes.."
    
    public FetchHistoryProcessor() {
    }

    @Override
    protected void innerProcess(CrawlURI puri) throws InterruptedException {
        CrawlURI curi = (CrawlURI) puri;
        curi.addPersistentDataMapKey(A_FETCH_HISTORY);
        HashMap<String, Object> latestFetch = new HashMap<String, Object>();

        // save status
        latestFetch.put(A_STATUS, curi.getFetchStatus());
        // save fetch start time
        latestFetch.put(A_FETCH_BEGAN_TIME, curi.getFetchBeginTime());
        // save digest
        String digest = curi.getContentDigestSchemeString();
        if (digest != null) {
            latestFetch.put(A_CONTENT_DIGEST, digest);
        }
        // save relevant HTTP headers, if available
        if (curi.isHttpTransaction()) {
            saveHeader(curi, latestFetch, A_ETAG_HEADER);
            saveHeader(curi, latestFetch, A_LAST_MODIFIED_HEADER);

            // save reference length (real or virtual)
            long referenceLength;
            if (curi.containsDataKey(A_REFERENCE_LENGTH)) {
                // reuse previous length if available (see FetchHTTP#setSizes).
                referenceLength = (Long) curi.getData().get(A_REFERENCE_LENGTH);
            } else {
                // normally, use content-length
                referenceLength = curi.getContentLength();
            }
            latestFetch.put(A_REFERENCE_LENGTH, referenceLength);
        }

        HashMap<String, Object>[] history = historyRealloc(curi);

        // rotate all history entries up one slot, insert new at [0]
        for (int i = history.length - 1; i > 0; i--) {
            history[i] = history[i - 1];
        }
        history[0] = latestFetch;

        curi.getData().put(A_FETCH_HISTORY, history);

        if (curi.getFetchStatus() == 304) {
            // Copy forward the content digest as the current digest is simply of an empty response
            latestFetch.put(A_CONTENT_DIGEST, history[1].get(A_CONTENT_DIGEST));
            // Create revisit profile
            curi.getAnnotations().add("duplicate:server-not-modified");
            ServerNotModifiedRevisit revisit = new ServerNotModifiedRevisit();
            revisit.setETag((String) latestFetch.get(A_ETAG_HEADER));
            revisit.setLastModified((String) latestFetch.get(A_LAST_MODIFIED_HEADER));
            revisit.setPayloadDigest((String)latestFetch.get(A_CONTENT_DIGEST));
            curi.setRevisitProfile(revisit);
        } else if (hasIdenticalDigest(curi)) {
            curi.getAnnotations().add("duplicate:digest");
            IdenticalPayloadDigestRevisit revisit = 
            		new IdenticalPayloadDigestRevisit((String)history[1].get(A_CONTENT_DIGEST));
            revisit.setRefersToTargetURI(curi.getURI()); // Matches are always on the same URI
            revisit.setRefersToDate((Long)history[1].get(A_FETCH_BEGAN_TIME));
            curi.setRevisitProfile(revisit);
        }
    }

    /**
     * Utility method for testing if a CrawlURI's last two history 
     * entries (one being the most recent fetch) have identical 
     * content-digest information. 
     * 
     * @param curi CrawlURI to test
     * @return true if last two history entries have identical digests, 
     * otherwise false
     */
    public static boolean hasIdenticalDigest(CrawlURI curi) {
        Map<String,Object>[] history = curi.getFetchHistory();

        return history != null
                && history[0] != null 
                && history[0].containsKey(A_CONTENT_DIGEST)
                && history[1] != null
                && history[1].containsKey(A_CONTENT_DIGEST)
                && history[0].get(A_CONTENT_DIGEST).equals(history[1].get(A_CONTENT_DIGEST));
    }
    
    /** Get or create proper-sized history array */
    @SuppressWarnings("unchecked")
    protected HashMap<String, Object>[] historyRealloc(CrawlURI curi) {
        int targetHistoryLength = getHistoryLength();
        HashMap<String, Object>[] history = curi.getFetchHistory();
        if (history == null) {
            history = new HashMap[targetHistoryLength];
        }
        if (history.length != targetHistoryLength) {
            HashMap<String, Object>[] newHistory = new HashMap[targetHistoryLength];
            System.arraycopy(history, 0, newHistory, 0,
                    Math.min(history.length, newHistory.length));
            history = newHistory;
        }
        return history;
    }

    /** Save a header from the given HTTP operation into the Map. */
    protected void saveHeader(CrawlURI curi, Map<String,Object> map,
            String key) {
        String value = curi.getHttpResponseHeader(key);
        if (value != null) {
            map.put(key, value);
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        // only process if curi contains evidence of fetch attempt
        return curi.containsDataKey(A_FETCH_BEGAN_TIME);
    }
}