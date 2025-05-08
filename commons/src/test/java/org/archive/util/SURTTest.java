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
package org.archive.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit test suite for SURT
 * 
 * @author gojomo
 * @version $ Id$
 */
public class SURTTest {

    @Test
    public void testMisc() {
        assertEquals("http://(org,archive,www,)", SURT.fromURI("http://www.archive.org"));

        assertEquals("http://(org,archive,www,)/movies/movies.php",
                SURT.fromURI("http://www.archive.org/movies/movies.php"));

        assertEquals("http://(org,archive,www,:8080)/movies/movies.php",
                SURT.fromURI("http://www.archive.org:8080/movies/movies.php"));

        assertEquals("http://(org,archive,www,@user:pass)/movies/movies.php",
                SURT.fromURI("http://user:pass@www.archive.org/movies/movies.php"));

        assertEquals("http://(org,archive,www,:8080@user:pass)/movies/movies.php",
                SURT.fromURI("http://user:pass@www.archive.org:8080/movies/movies.php"));

        assertEquals("http://(org,archive,www,)/movies/movies.php#top",
                SURT.fromURI("http://www.archive.org/movies/movies.php#top"));
    }

    @Test
    public void testAtSymbolInPath() {
        assertEquals("http://(com,example,www,)/foo@bar",
                SURT.fromURI("http://www.example.com/foo@bar"), "@ in path");
    }
    
    /**
     * Verify that dotted-quad numeric IP address is unreversed as per change
     * requested in: [ 1572391 ] SURTs for IP-address URIs unhelpful
     */
    @Test
    public void testDottedQuadAuthority() {
        assertEquals("http://(127.2.34.5)/foo", SURT.fromURI("http://127.2.34.5/foo"),
                "dotted-quad IP authority");
    }
}

