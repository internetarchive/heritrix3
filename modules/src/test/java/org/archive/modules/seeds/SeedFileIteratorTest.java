/* SeedFileIteratorTest
 *
 * $Id$
 *
 * Created on May 31, 2005
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
package org.archive.modules.seeds;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedList;

import junit.framework.TestCase;

import org.archive.modules.seeds.SeedFileIterator;
import org.archive.net.UURI;

/**
 * Test {@link SeedFileIterator}.
 * @author gojomo
 * @version $Revision$, $Date$
 */
public class SeedFileIteratorTest extends TestCase {
    public void testHyphenInHost() {
        final String seedFileContent = "http://www.examp-le.com/";
        StringWriter sw = new StringWriter();
        StringReader sr = new StringReader(seedFileContent);
        UURI seed = 
            (UURI)(new SeedFileIterator(new BufferedReader(sr), sw)).next();
        assertEquals("Hyphen is problem", seed.toString(),
            seedFileContent);
    }

    public void testGeneral() throws IOException {
        String seedFile = "# comment\n" + // comment
                "\n" + // blank line
                "www.example.com\n" + // naked host, implied scheme
                "www.example.org/foo\n" + // naked host+path, implied scheme
                "http://www.example.net\n" + // full HTTP URL
                "+http://www.example.us"; // 'directive' (should be ignored)
        StringWriter ignored = new StringWriter();
        SeedFileIterator iter = new SeedFileIterator(new BufferedReader(
                new StringReader(seedFile)), new BufferedWriter(ignored));
        LinkedList<String> seeds = new LinkedList<String>();
        while (iter.hasNext()) {
            UURI n = iter.next();
            if (n instanceof UURI) {
                seeds.add(n.getURI());
            }
        }
        assertTrue("didn't get naked host", seeds
                .contains("http://www.example.com/"));
        assertTrue("didn't get naked host+path", seeds
                .contains("http://www.example.org/foo"));
        assertTrue("didn't get full http URL", seeds
                .contains("http://www.example.net/"));
        assertTrue("got wrong number of URLs", seeds.size() == 3);
        assertTrue("ignored entry not reported", ignored.toString().indexOf(
                "+http://www.example.us") >= 0);
    }
}

