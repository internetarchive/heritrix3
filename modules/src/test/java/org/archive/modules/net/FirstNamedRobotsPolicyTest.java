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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;

import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;

import junit.framework.TestCase;

/**
 * Test for FirstNamedRobotsPolicy
 * 
 * @contributor gojomo
 */
public class FirstNamedRobotsPolicyTest extends TestCase {
    
    Robotstxt sampleRobots1() throws IOException {
        BufferedReader reader = new BufferedReader(
            new StringReader(
                "User-agent: *\n" +
                "Disallow: /cgi-bin/\n" +
                "Disallow: /details/software\n" +
                "\n"+
                "User-agent: denybot\n" +
                "Disallow: /\n" +
                "\n"+
                "User-agent: allowbot1\n" +
                "Disallow: \n" +
                "\n"+
                "User-agent: allowbot2\n" +
                "Disallow: /foo\n" +
                // slightly different from version in RobotstxtTest
                "\n"+
                "User-agent: delaybot\n" +
                "Disallow: /\n" +
                "Crawl-Delay: 20\n"+
                "Allow: /images/\n"
            ));
        return new Robotstxt(reader); 
    }
    
    public void testDecisionsByListedCandidates() throws IOException {
        Robotstxt robots = sampleRobots1();
        FirstNamedRobotsPolicy policy = new FirstNamedRobotsPolicy();
        policy.setCandidateUserAgents(Arrays.asList("unnamedBot","allowbot2"));
        CrawlURI curi = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com/cgi-bin/whatever"));
        // should be disallowed by immediate match on primary user-agent disallow-all
        assertFalse(policy.allows("denybot",curi,robots));
        // should be allowed by immediate match on primary user-agent allow-all
        assertTrue(policy.allows("allowbot1",curi,robots));
        // but no custom user-agent should be assigned
        assertNull(curi.getUserAgent());
        // should be allowed by specific allowbot2 rules tried 3rd
        assertTrue(policy.allows("goodbot",curi,robots));
        // and, curi should have updated user-agent
        assertEquals("allowbot2",curi.getUserAgent());
        CrawlURI curi2 = new CrawlURI(UURIFactory
                .getInstance("http://www.example.com/foo"));
       // should be disallowed by specific allowbot2 directive tried 3rd
        assertFalse(policy.allows("goodbot",curi2,robots));
    }
    
}
