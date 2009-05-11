/* SURTTest
 *
 * $Id$
 *
 * Created Tue Jan 20 14:17:59 PST 2004
 *
 * Copyright (C) 2004 Internet Archive.
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

package org.archive.util;

import org.apache.commons.httpclient.URIException;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * JUnit test suite for SURT
 * 
 * @author gojomo
 * @version $ Id$
 */
public class SURTTest extends TestCase {
    /**
     * Create a new MemQueueTest object
     * 
     * @param testName
     *            the name of the test
     */
    public SURTTest(final String testName) {
        super(testName);
    }

    /**
     * run all the tests for MemQueueTest
     * 
     * @param argv
     *            the command line arguments
     */
    public static void main(String argv[]) {
        junit.textui.TestRunner.run(suite());
    }

    /**
     * return the suite of tests for MemQueueTest
     * 
     * @return the suite of test
     */
    public static Test suite() {
        return new TestSuite(SURTTest.class);
    }

    public void testMisc() throws URIException {
        assertEquals("", 
                "http://(org,archive,www,)",
                SURT.fromURI("http://www.archive.org"));

        assertEquals("", 
                "http://(org,archive,www,)/movies/movies.php",
                SURT.fromURI("http://www.archive.org/movies/movies.php"));

        assertEquals("", 
                "http://(org,archive,www,:8080)/movies/movies.php",
                SURT.fromURI("http://www.archive.org:8080/movies/movies.php"));

        assertEquals("", 
                "http://(org,archive,www,@user:pass)/movies/movies.php",
                SURT.fromURI("http://user:pass@www.archive.org/movies/movies.php"));

        assertEquals("", 
                "http://(org,archive,www,:8080@user:pass)/movies/movies.php",
                SURT.fromURI("http://user:pass@www.archive.org:8080/movies/movies.php"));
        
        assertEquals("", 
                "http://(org,archive,www,)/movies/movies.php#top",
                SURT.fromURI("http://www.archive.org/movies/movies.php#top"));
    }
    
    public void testAtSymbolInPath() throws URIException {
        assertEquals("@ in path",
                "http://(com,example,www,)/foo@bar",
                SURT.fromURI("http://www.example.com/foo@bar"));  
    }
    
    /**
     * Verify that dotted-quad numeric IP address is unreversed as per change
     * requested in: [ 1572391 ] SURTs for IP-address URIs unhelpful
     * 
     * @throws URIException
     */
    public void testDottedQuadAuthority() throws URIException {
        assertEquals("dotted-quad IP authority",
                "http://(127.2.34.5)/foo",
                SURT.fromURI("http://127.2.34.5/foo"));  
    }
}

