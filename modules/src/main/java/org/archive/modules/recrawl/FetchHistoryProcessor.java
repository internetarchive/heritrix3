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

import java.util.HashMap;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.deciderules.recrawl.IdenticalDigestDecideRule;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.*;
import static org.archive.modules.CoreAttributeConstants.A_FETCH_BEGAN_TIME;

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
        HashMap<String, Object> latestFetch = new HashMap<String,Object>();

        // save status
        latestFetch.put(A_STATUS,curi.getFetchStatus());
        // save fetch start time
        latestFetch.put(A_FETCH_BEGAN_TIME,curi.getData().get(A_FETCH_BEGAN_TIME));
        // save digest
        String digest = curi.getContentDigestSchemeString();
        if(digest!=null) {
            latestFetch.put(A_CONTENT_DIGEST,digest);
        }
        // save relevant HTTP headers, if available
        if(curi.isHttpTransaction()) {
            HttpMethod method = curi.getHttpMethod();
            saveHeader(A_ETAG_HEADER,method,latestFetch);
            saveHeader(A_LAST_MODIFIED_HEADER,method,latestFetch);
            // save reference length (real or virtual)
            long referenceLength; 
            if(curi.containsDataKey(A_REFERENCE_LENGTH) ) {
                // reuse previous length if available (see FetchHTTP#setSizes). 
                referenceLength = (Long) curi.getData().get(A_REFERENCE_LENGTH);
            } else {
                // normally, use content-length
                referenceLength = curi.getContentLength();
            }
            latestFetch.put(A_REFERENCE_LENGTH,referenceLength);
        }
        
        // get or create proper-sized history array
        int targetHistoryLength = getHistoryLength();
        @SuppressWarnings("unchecked")
        HashMap<String, ?>[] history = 
            (HashMap<String, ?>[]) (curi.containsDataKey(A_FETCH_HISTORY) 
		    ? curi.getData().get(A_FETCH_HISTORY) 
		    : new HashMap[targetHistoryLength]);
        if(history.length != targetHistoryLength) {
            @SuppressWarnings("unchecked")
            HashMap<String, ?>[] newHistory = new HashMap[targetHistoryLength];
            System.arraycopy(
                    history,0,
                    newHistory,0,
                    Math.min(history.length,newHistory.length));
            history = newHistory; 
        }

        // rotate all history entries up one slot, insert new at [0]
        for(int i = history.length-1; i >0; i--) {
            history[i] = history[i-1];
        }
        history[0]=latestFetch;

        curi.getData().put(A_FETCH_HISTORY,history);

        if (IdenticalDigestDecideRule.hasIdenticalDigest(curi)) {
            curi.getAnnotations().add("duplicate:digest");
        }
    }

    /**
     * Save a header from the given HTTP operation into the AList.
     * 
     * @param name header name to save into history AList
     * @param method http operation containing headers
     * @param latestFetch AList to get header
     */
    protected void saveHeader(String name, HttpMethod method, 
    		HashMap<String, Object> latestFetch) {
        Header header = method.getResponseHeader(name);
        if(header!=null) {
            latestFetch.put(name, header.getValue());
        }
    }


    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        // only process if curi contains evidence of fetch attempt
        return curi.containsDataKey(A_FETCH_BEGAN_TIME);
    }
}