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
package org.archive.modules.net;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;

/**
 * RobotsPolicy represents the strategy used by the crawler 
 * for determining how robots.txt files will be honored. 
 *
 * @contributor gojomo
 */
abstract public class RobotsPolicy {
    public static Map<String, RobotsPolicy> STANDARD_POLICIES = new HashMap<String,RobotsPolicy>();
    static {
        STANDARD_POLICIES.put("obey", ObeyRobotsPolicy.INSTANCE);
        // the obey policy has also historically been called 'classic'
        STANDARD_POLICIES.put("classic", ObeyRobotsPolicy.INSTANCE);
        STANDARD_POLICIES.put("ignore", IgnoreRobotsPolicy.INSTANCE); 
    }

    public abstract boolean allows(String userAgent, CrawlURI curi, Robotstxt robotstxt);
    
    public abstract boolean obeyMetaRobotsNofollow();
    
    public String getPathQuery(CrawlURI curi) {
        try {
            return curi.getUURI().getPathQuery();
        } catch (URIException e) {
            // unlikely
            return "";
        }
    }
}
