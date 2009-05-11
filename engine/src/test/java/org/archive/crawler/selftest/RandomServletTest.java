/* 
 * Copyright (C) 2007 Internet Archive.
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
 *
 * RandomServletTest.java
 *
 * Created on Feb 28, 2007
 *
 * $Id:$
 */

package org.archive.crawler.selftest;

import junit.framework.TestCase;

/**
 * @author pjack
 *
 */
public class RandomServletTest extends TestCase {

    
    
    public void testPathParse() {
        for (int i = 0; i < 1000; i++) {
            String s = RandomServletLinkWriter.toPath(i);
            // System.out.println(i +" -> " + s);
            int v = RandomServletLinkWriter.fromPath(s);
            int v2 = RandomServletLinkWriter.fromPath("/" + s);
            assertEquals(i, v);
            assertEquals(i, v2);
        }
    }

}
