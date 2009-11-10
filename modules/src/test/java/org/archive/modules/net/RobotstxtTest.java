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

import junit.framework.TestCase;

public class RobotstxtTest extends TestCase {
    public void testParseRobots() throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader("BLAH"));
        Robotstxt r = new Robotstxt(reader);
        assertFalse(r.hasErrors);
        assertTrue(r.getUserAgents().size() == 0);
        // Parse archive robots.txt with heritrix agent.
        String agent = "archive.org_bot";
        reader = new BufferedReader(
            new StringReader("User-agent: " + agent + "\n" +
            "Disallow: /cgi-bin/\n" +
            "Disallow: /details/software\n"));
        r = new Robotstxt(reader);
        assertFalse(r.hasErrors);
        assertTrue(r.getUserAgents().size() == 1);
        assertTrue(r.agentsToDirectives.size() == 1);
        assertEquals(r.getUserAgents().get(0), agent);
        // Parse archive robots.txt with star agent.
        agent = "*";
        reader = new BufferedReader(
            new StringReader("User-agent: " + agent + "\n" +
            "Disallow: /cgi-bin/\n" +
            "Disallow: /details/software\n"));
        r = new Robotstxt(reader);
        assertFalse(r.hasErrors);
        assertTrue(r.getUserAgents().size() == 1);
        assertTrue(r.agentsToDirectives.size() == 1);
        assertEquals(r.getUserAgents().get(0), "");
    }
    
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
                "Allow: /\n"+
                "\n"+
                "User-agent: delaybot\n" +
                "Disallow: /\n" +
                "Crawl-Delay: 20\n"+
                "Allow: /images/\n"
            ));
        return new Robotstxt(reader); 
    }
    
    public void testDirectives() throws IOException {
        Robotstxt r = sampleRobots1();
        // bot allowed with empty disallows
        assertTrue(r.getDirectivesFor("Mozilla allowbot1 99.9").allows("/path"));
        assertTrue(r.getDirectivesFor("Mozilla allowbot1 99.9").allows("/"));
        // bot allowed with explicit allow
        assertTrue(r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/path"));
        assertTrue(r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/"));
        assertTrue(r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/foo"));
        // bot denied with blanket deny
        assertFalse(r.getDirectivesFor("Mozilla denybot 99.9").allows("/path"));
        assertFalse(r.getDirectivesFor("Mozilla denybot 99.9").allows("/"));
        // unnamed bot with mixed catchall allow/deny
        assertTrue(r.getDirectivesFor("Mozilla anonbot 99.9").allows("/path"));
        assertFalse(r.getDirectivesFor("Mozilla anonbot 99.9").allows("/cgi-bin/foo.pl"));
        // no crawl-delay
        assertEquals(r.getDirectivesFor("Mozilla denybot 99.9").getCrawlDelay(),-1f);
        // with crawl-delay 
        assertEquals(r.getDirectivesFor("Mozilla delaybot 99.9").getCrawlDelay(),20f);
    }

    Robotstxt htmlMarkupRobots() throws IOException {
        BufferedReader reader = new BufferedReader(
            new StringReader(
                "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\"><HTML>\n"
                +"<HEAD>\n"
                +"<TITLE>/robots.txt</TITLE>\n"
                +"<HEAD>\n"
                +"<BODY>\n"
                +"User-agent: *<BR>\n"
                +"Disallow: /<BR>\n"
                +"Crawl-Delay: 30<BR>\n"
                +"\n"
                +"</BODY>\n"
                +"</HTML>\n"
            ));
        return new Robotstxt(reader); 
    }
    
    /**
     * Test handling of a robots.txt with extraneous HTML markup
     * @throws IOException
     */
    public void testHtmlMarkupRobots() throws IOException {
        Robotstxt r = htmlMarkupRobots();
        assertFalse(r.getDirectivesFor("anybot").allows("/index.html"));
        assertEquals(30f,r.getDirectivesFor("anybot").getCrawlDelay());
    }
}
