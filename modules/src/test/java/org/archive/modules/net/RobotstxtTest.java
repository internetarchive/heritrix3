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
import java.io.Reader;
import java.io.StringReader;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import org.archive.bdb.AutoKryo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class RobotstxtTest {
    @Test
    public void testParseRobots() throws IOException {
        Reader reader = new StringReader("BLAH");
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
        Reader reader = new StringReader(
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
            );
        return new Robotstxt(reader); 
    }
    
    Robotstxt whitespaceFlawedRobots() throws IOException {
        Reader reader = new StringReader(
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
            );
        return new Robotstxt(reader); 
    }

    @Test
    public void testValidRobots() throws IOException {
        Robotstxt r = sampleRobots1();
        evalRobots(r); 
    }

    @Test
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
        assertFalse(r.getDirectivesFor("Mozilla allowbot2 99.9").allows("/ok?butno=something"), "ignoring query-string");
        
        // bot denied with blanket deny
        assertFalse(r.getDirectivesFor("Mozilla denybot 99.9").allows("/path"));
        assertFalse(r.getDirectivesFor("Mozilla denybot 99.9").allows("/"));
        
        // unnamed bot with mixed catchall allow/deny
        assertTrue(r.getDirectivesFor("Mozilla anonbot 99.9").allows("/path"));
        assertFalse(r.getDirectivesFor("Mozilla anonbot 99.9").allows("/cgi-bin/foo.pl"));
        
        // no crawl-delay
        assertEquals(-1f, r.getDirectivesFor("Mozilla denybot 99.9").getCrawlDelay());
        
        // with crawl-delay 
        assertEquals(20f, r.getDirectivesFor("Mozilla delaybot 99.9").getCrawlDelay());
    }

    Robotstxt htmlMarkupRobots() throws IOException {
        Reader reader = new StringReader(
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
            );
        return new Robotstxt(reader); 
    }
    
    /**
     * Test handling of a robots.txt with extraneous HTML markup
     */
    @Test
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
     */
    @Test
    public void testCompactSerialization() throws IOException {
        AutoKryo kryo = new AutoKryo();
        kryo.autoregister(Robotstxt.class);
        
        final String TEST_ROBOTS_TXT = "User-Agent:a\n" +
        "User-Agent:b\n" +
        "User-Agent:c\n" +
        "User-Agent:d\n" +
        "Disallow:/service\n";

        StringReader sr = new StringReader(TEST_ROBOTS_TXT);
        Robotstxt rt = new Robotstxt(sr);
        {
            RobotsDirectives da = rt.getDirectivesFor("a", false);
            RobotsDirectives db = rt.getDirectivesFor("b", false);
            assertSame(da, db, "user-agent a and b shares the same RobotsDirectives before serialization");
        }
        Output buffer = new Output(1024, -1);
        kryo.writeObject(buffer, rt);
        Robotstxt rt2 = kryo.readObject(new Input(buffer.toBytes()), Robotstxt.class);
        assertNotNull(rt2);
        {
            RobotsDirectives da = rt2.getDirectivesFor("a", false);
            RobotsDirectives db = rt2.getDirectivesFor("b", false);
            assertSame(da, db, "user-agent a and b shares the same RobotsDirectives after deserialization");
        }
    }

    @Test
    public void testSeparatedSections() throws IOException {
        final String TEST_ROBOTS_TXT = "User-agent: *\n"
                + "Crawl-delay: 5\n"
                + "User-agent: a\n"
                + "Disallow: /\n"
                + "User-agent: *\n"
                + "Disallow: /disallowed\n"
                + "User-agent: a\n"
                + "Crawl-delay: 99\n";
        StringReader sr = new StringReader(TEST_ROBOTS_TXT);
        Robotstxt rt = new Robotstxt(sr);

        assertFalse(rt.getDirectivesFor("a").allows("/foo"));

        assertTrue(rt.getDirectivesFor("c").allows("/foo"));
        assertFalse(rt.getDirectivesFor("c").allows("/disallowed"));

        assertEquals(5f, rt.getDirectivesFor("c").getCrawlDelay());

        assertEquals(99f, rt.getDirectivesFor("a").getCrawlDelay());
    }

    @Test
    public void testSizeLimit() throws IOException {
        StringBuilder builder = new StringBuilder(
                "User-agent: a\n" +
                        "  Disallow: /\n" +
                        "User-Agent: b\nDisallow: /");
        for (int i = 0; i < Robotstxt.MAX_SIZE; i++) {
            builder.append(' ');
        }
        builder.append("\nUser-Agent: c\nDisallow: /\n");
        Robotstxt rt = new Robotstxt(new StringReader(builder.toString()));
        assertFalse(rt.getDirectivesFor("a").allows("/foo"),
                "we should parse the first few lines");
        assertTrue(rt.getDirectivesFor("b").allows("/foo"),
                "ignore the line that breaks the size limit");
        assertTrue(rt.getDirectivesFor("c").allows("/foo"),
                "and also ignore any lines after the size limit");
    }

    @Test
    public void testAllBlankLines() throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < Robotstxt.MAX_SIZE; i++) {
            builder.append('\n');
        }
        new Robotstxt(new StringReader(builder.toString()));
    }

    @Test
    public void testWildcards() throws IOException {
        Robotstxt rt = new Robotstxt(new StringReader("""
                User-Agent: *
                Disallow: *.gif$
                Disallow: /example/
                Allow: /publications/
                """));
        assertTrue(rt.getDirectivesFor("x").allows("/"));
        assertFalse(rt.getDirectivesFor("x").allows("/example/blocked"));
        assertFalse(rt.getDirectivesFor("x").allows("/image.gif"));
        assertTrue(rt.getDirectivesFor("x").allows("/image.gif?size=large"));
        assertTrue(rt.getDirectivesFor("x").allows("/publications/image.gif"));

        rt = new Robotstxt(new StringReader("""
                User-Agent: *
                allow: /a/*/c
                disallow: /a/b/c
                disallow: /a/bb/c
                """));
        assertTrue(rt.getDirectivesFor("x").allows("/a/b/c"));
        assertFalse(rt.getDirectivesFor("x").allows("/a/bb/c"));

        rt = new Robotstxt(new StringReader("""
                User-Agent: *
                allow: /a
                disallow: /a
                """));
        assertTrue(rt.getDirectivesFor("x").allows("/a/b"));

        rt = new Robotstxt(new StringReader("""
                User-Agent: *
                allow: /$
                Disallow: /
                """));
        assertTrue(rt.getDirectivesFor("x").allows("/"));
        assertFalse(rt.getDirectivesFor("x").allows("/a"));
        assertFalse(rt.getDirectivesFor("x").allows("//"));

        rt = new Robotstxt(new StringReader("""
                User-Agent: *
                Disallow: /foo*/bar
                """));
        assertFalse(rt.getDirectivesFor("x").allows("/foo/bar"));
        assertFalse(rt.getDirectivesFor("x").allows("/fooooo/bar"));
        assertTrue(rt.getDirectivesFor("x").allows("/fox/bar"));

        rt = new Robotstxt(new StringReader("""
                User-Agent: *
                Disallow: /foo$/
                Allow: /foo$
                """));
        assertFalse(rt.getDirectivesFor("x").allows("/foo$/"));
        assertFalse(rt.getDirectivesFor("x").allows("/foo$/bar"));
        assertTrue(rt.getDirectivesFor("x").allows("/foo$"));
        assertTrue(rt.getDirectivesFor("x").allows("/foo"));

        rt = new Robotstxt(new StringReader("""
                User-Agent: *
                Disallow: /*$
                Allow: /*
                """));
        assertFalse(rt.getDirectivesFor("x").allows("/"));
        assertFalse(rt.getDirectivesFor("x").allows("/a"));
        assertFalse(rt.getDirectivesFor("x").allows("//"));
    }
}
