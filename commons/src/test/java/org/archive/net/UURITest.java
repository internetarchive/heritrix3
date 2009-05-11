/* UURITest.java
 *
 * $Id$
 *
 * Created Jul 18, 2005
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
package org.archive.net;

import java.net.URISyntaxException;

import junit.framework.TestCase;

public class UURITest extends TestCase {
    public void testHasScheme() {
        assertTrue(UURI.hasScheme("http://www.archive.org"));
        assertTrue(UURI.hasScheme("http:"));
        assertFalse(UURI.hasScheme("ht/tp://www.archive.org"));
        assertFalse(UURI.hasScheme("/tmp"));
    }
    
    public void testGetFileName() throws URISyntaxException {
        final String filename = "x.arc.gz";
        assertEquals(filename,
            UURI.parseFilename("/tmp/one.two/" + filename));
        assertEquals(filename,
            UURI.parseFilename("http://archive.org/tmp/one.two/" +
                    filename));
        assertEquals(filename,
            UURI.parseFilename("rsync://archive.org/tmp/one.two/" +
                    filename)); 
    }
}
