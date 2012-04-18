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
import java.nio.ByteBuffer;

import junit.framework.TestCase;

import org.archive.bdb.AutoKryo;

public class RobotstxtTest extends TestCase {
    public void testParseRobots() throws IOException {
        BufferedReader reader = new BufferedReader(new StringReader("BLAH"));
        Robotstxt r = new Robotstxt(reader);
        assertFalse(r.hasErrors);
        assertEquals(0,r.getNamedUserAgents().size());
        // Parse archive robots.txt with heritrix agent.
        String agent = "archive.org_bot";
        reader = new BufferedReader(
            new StringReader("User-agent: " + agent + "\n" +
            "Disallow: /cgi-bin/\n" +
            "Disallow: /details/software\n"));
        r = new Robotstxt(reader);
        assertFalse(r.hasErrors);
        assertEquals(1,r.getNamedUserAgents().size());
        assertEquals(1,r.agentsToDirectives.size());
        assertEquals(agent, r.getNamedUserAgents().get(0));
        // Parse archive robots.txt with star agent.
        agent = "*";
        reader = new BufferedReader(
            new StringReader("User-agent: " + agent + "\n" +
            "Disallow: /cgi-bin/\n" +
            "Disallow: /details/software\n"));
        r = new Robotstxt(reader);
        assertFalse(r.hasErrors);
        assertEquals(0, r.getNamedUserAgents().size());
        assertEquals(0, r.agentsToDirectives.size());
    }
    
    static Robotstxt sampleRobots1() throws IOException {
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
                "Disallow: /ok?butno\n" +
                "Allow: /\n"+
                "\n"+
                "User-agent: delaybot\n" +
                "Disallow: /\n" +
                "Crawl-Delay: 20\n"+
                "Allow: /images/\n"
            ));
        return new Robotstxt(reader); 
    }
    
    Robotstxt whitespaceFlawedRobots() throws IOException {
        BufferedReader reader = new BufferedReader(
            new StringReader(
                "  User-agent: *\n" +
                " Disallow: /cgi-bin/\n" +
                "  Disallow: /details/software\n" +
                " User-agent: denybot\n" +
                " Disallow: /\n" +
                "  User-agent: allowbot1\n" +
                "  Disallow: \n" +
                " User-agent: allowbot2\n" +
                " Disallow: /foo\n" +
                " Disallow: /ok?butno\n" +
                " Allow: /\n"+
                " User-agent: delaybot\n" +
                "  Disallow: /\n" +
                " Crawl-Delay: 20\n"+
                " Allow: /images/\n"
            ));
        return new Robotstxt(reader); 
    }
    
    public void testValidRobots() throws IOException {
        Robotstxt r = sampleRobots1();
        evalRobots(r); 
    }
    
    public void testWhitespaceFlawedRobots() throws IOException {
        Robotstxt r = whitespaceFlawedRobots();
        evalRobots(r); 
    }
    
    public void evalRobots(Robotstxt r) throws IOException {
        // bot allowed with empty disallows
        assertTrue(r.getDirectivesFor("Mozilla allowbot1 99.9").allows("/path"));
        assertTrue(r.getDirectivesFor("Mozilla allowbot1 99.9").allows("/"));
        
        // bot allowed with explicit allow
        assertTrue(r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/path"));
        assertTrue(r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/"));
        
        // bot denied with specific disallow overriding general allow
        assertFalse(r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/foo"));
        // HER-1976: query-string disallow
        assertFalse("ignoring query-string", r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/ok?butno=something"));
        
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
    /**
     * Test serialization/deserialization of Robotstxt object.
     * Improper behavior, such as failure to restore shared RobotsDirectives objects,
     * can lead to excess memory usage and CPU cycles. In one case, 450KB robots.txt
     * exploded into 450MB. See [HER-1912].
     * @throws IOException
     */
    public void testCompactSerialization() throws IOException {
        AutoKryo kryo = new AutoKryo();
        kryo.autoregister(Robotstxt.class);
        
        final String TEST_ROBOTS_TXT = "User-Agent:a\n" +
        "User-Agent:b\n" +
        "User-Agent:c\n" +
        "User-Agent:d\n" +
        "Disallow:/service\n";

        StringReader sr = new StringReader(TEST_ROBOTS_TXT);
        Robotstxt rt = new Robotstxt(new BufferedReader(sr));
        {
            RobotsDirectives da = rt.getDirectivesFor("a", false);
            RobotsDirectives db = rt.getDirectivesFor("b", false);
            assertTrue("user-agent a and b shares the same RobotsDirectives before serialization", da == db);
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        kryo.writeObject(buffer, rt);
        buffer.flip();
        Robotstxt rt2 = kryo.readObject(buffer, Robotstxt.class);
        assertNotNull(rt2);
        {
            RobotsDirectives da = rt2.getDirectivesFor("a", false);
            RobotsDirectives db = rt2.getDirectivesFor("b", false);
            assertTrue("user-agent a and b shares the same RobotsDirectives after deserialization", da == db);
        }
    }
}
