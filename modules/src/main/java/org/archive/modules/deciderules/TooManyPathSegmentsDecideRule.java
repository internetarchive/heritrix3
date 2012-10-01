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

import java.util.logging.Logger;

import org.archive.modules.CrawlURI;

/**
 * Rule REJECTs any CrawlURIs whose total number of path-segments (as
 * indicated by the count of '/' characters not including the first '//')
 * is over a given threshold.
 *
 * @author gojomo
 */
public class TooManyPathSegmentsDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 3L;
    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(TooManyPathSegmentsDecideRule.class.getName()); 

    /** default for this class is to REJECT */
    {
        setDecision(DecideResult.REJECT);
    }
    
    /**
     * Number of path segments beyond which this rule will reject URIs.
     */
    {
        setMaxPathDepth(20);
    }
    public int getMaxPathDepth() {
        return (Integer) kp.get("maxPathDepth");
    }
    public void setMaxPathDepth(int maxPathDepth) {
        kp.put("maxPathDepth", maxPathDepth);
    }
    /**
     * Usual constructor. 
     */
    public TooManyPathSegmentsDecideRule() {
    }

    /**
     * Evaluate whether given object is over the threshold number of
     * path-segments.
     * 
     * @param object
     * @return true if the path-segments is exceeded
     */
    @Override
    protected boolean evaluate(CrawlURI curi) {
        String uriPath = curi.getUURI().getEscapedPath();
        if (uriPath == null) {
            // no path means no segments
            return false;
        }
        int count = 0;
        int threshold = getMaxPathDepth();
        for (int i = 0; i < uriPath.length(); i++) {
            if (uriPath.charAt(i) == '/') {
                count++;
            }
            if (count > threshold) {
                return true;
            }
        }
        return false;
    }

}
