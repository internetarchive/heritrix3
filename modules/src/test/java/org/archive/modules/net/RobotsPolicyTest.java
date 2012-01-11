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

import java.io.IOException;

import junit.framework.TestCase;

import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;

public class RobotsPolicyTest extends TestCase {

    public void testClassicRobots() throws IOException {
        Robotstxt rtxt = RobotstxtTest.sampleRobots1();
        RobotsPolicy policy = RobotsPolicy.STANDARD_POLICIES.get("classic");
        evalQueryString(policy, rtxt); 
    }
    
    
    /**
     * HER-1976: query-string disallow
     * 
     * @param policy
     * @param r
     * @throws IOException
     */
    public void evalQueryString(RobotsPolicy policy, Robotstxt rtxt) throws IOException {
        CrawlURI qs = new CrawlURI(UURIFactory.getInstance("http://example.com/ok?butno=something"));

        assertFalse("ignoring query-string", policy.allows("Mozilla allowbot2 99.9", qs, rtxt));
        
    }
}