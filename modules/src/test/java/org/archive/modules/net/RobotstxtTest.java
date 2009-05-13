/* RobotstxtTest
 *
 * $Id$
 *
 * Created Sep 1, 2005
 *
 * Copyright (C) 2005 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
