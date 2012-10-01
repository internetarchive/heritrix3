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
package org.archive.modules.deciderules;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpMethod;
import org.archive.modules.CrawlURI;

/**
 * Applies configured decision for URIs with content length less than or equal
 * to a given threshold length value. Examines either HTTP header Content-Length
 * or actual downloaded content length (based on the useHeaderLength property), 
 * and has no effect on resources longer than the given threshold value.
 * 
 * Note that because neither the Content-Length header nor the actual size are
 * available at URI-scoping time, this rule is unusable in crawl scopes. 
 * Instead, the earliest it can be used is as a mid-fetch rule (in FetchHTTP), 
 * when the headers are available but not yet the body. It can also be used 
 * to affect processing after the URI is fully fetched. 
 */
public class ResourceNoLongerThanDecideRule extends PredicatedDecideRule {
    private static final long serialVersionUID = -8774160016195991876L;

    private static final Logger logger = Logger.
    	getLogger(ResourceNoLongerThanDecideRule.class.getName());
    
    /**
     * Shall this rule be used as a midfetch rule? If true, this rule will
     * determine content length based on HTTP header information, otherwise
     * the size of the already downloaded content will be used.
     */
    {
        setUseHeaderLength(true);
    }
    public boolean getUseHeaderLength() {
        return (Boolean) kp.get("useHeaderLength");
    }
    public void setUseHeaderLength(boolean useHeaderLength) {
        kp.put("useHeaderLength",useHeaderLength);
    }
    
    /**
     * Max content-length this filter will allow to pass through. If -1, 
     * then no limit.
     */
    {
        setContentLengthThreshold(-1L);
    }
    public long getContentLengthThreshold() {
        return (Long) kp.get("contentLengthThreshold");
    }
    public void setContentLengthThreshold(long threshold) {
        kp.put("contentLengthThreshold",threshold);
    }

    // Header predictor state constants
    public static final int HEADER_PREDICTS_MISSING = -1;
	
    public ResourceNoLongerThanDecideRule() {
    }
    
    protected boolean evaluate(CrawlURI curi) {
        int contentlength = HEADER_PREDICTS_MISSING;

        // filter used as midfetch filter
        if (getUseHeaderLength()) {

            if (curi.getHttpMethod() == null) {
                // Missing header info, let pass
                if (logger.isLoggable(Level.INFO)) {
                    logger.info("Error: Missing HttpMethod object in "
                            + "CrawlURI. " + curi.toString());
                }
                return false;
            }

            // Initially assume header info is missing
            HttpMethod method = curi.getHttpMethod();

            // get content-length
            String newContentlength = null;
            if (method.getResponseHeader("content-length") != null) {
                newContentlength = method.getResponseHeader("content-length")
                        .getValue();
            }

            if (newContentlength != null && newContentlength.length() > 0) {
                try {
                    contentlength = Integer.parseInt(newContentlength);
                } catch (NumberFormatException nfe) {
                    // Ignore.
                }
            }

            // If no document length was reported or format was wrong,
            // let pass
            if (contentlength == HEADER_PREDICTS_MISSING) {
                return false;
            }
        } else {
            contentlength = (int) curi.getContentSize();
        }
        
        return test(contentlength);
    }
    
    
    protected boolean test(int contentlength) {
        return contentlength < getContentLengthThreshold();        
    }
}