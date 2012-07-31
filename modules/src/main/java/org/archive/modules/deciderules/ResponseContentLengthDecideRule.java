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

import static org.archive.modules.CoreAttributeConstants.A_FETCH_BEGAN_TIME;

import org.archive.modules.CrawlURI;

/**
 * Decide rule that will ACCEPT or REJECT a uri, depending on the
 * "decision" property, after it's fetched, if the content body is within a
 * specified size range, specified in bytes.
 * 
 * @contributor nlevitt
 */
public class ResponseContentLengthDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 1L;

    {
        setLowerBound(0l);
    }
    public long getLowerBound() {
        return (Long) kp.get("lowerBound");
    }
    /**
     * The rule will apply if the url has been fetched and content body length
     * is greater than or equal to this number of bytes. Default is 0, meaning
     * everything will match.
     */
    public void setLowerBound(long size) {
        kp.put("lowerBound", size);
    }

    {
        setUpperBound(Long.MAX_VALUE);
    }
    public long getUpperBound() {
        return (Long) kp.get("upperBound");
    }
    
    /**
     * The rule will apply if the url has been fetched and content body length
     * is less than or equal to this number of bytes. Default is
     * {@code Long.MAX_VALUE}, meaning everything will match.
     */
    public void setUpperBound(long bound) {
        kp.put("upperBound", bound);
    }

    @Override
    protected boolean evaluate(CrawlURI uri) {
        // only process if curi contains evidence of fetch attempt
        return uri.containsDataKey(A_FETCH_BEGAN_TIME)
                && uri.getRecorder() != null 
                && uri.getRecorder().getResponseContentLength() >= getLowerBound()
                && uri.getRecorder().getResponseContentLength() <= getUpperBound();
    }

}
