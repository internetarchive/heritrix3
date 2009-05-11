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
 * CrawlHostTest.java
 *
 * Created on Jan 31, 2007
 *
 * $Id:$
 */

package org.archive.modules.net;

import org.archive.modules.net.CrawlHost;
import org.archive.util.TestUtils;

import junit.framework.TestCase;

/**
 * @author pjack
 *
 */
public class CrawlHostTest extends TestCase {

    
    public void testSerialization() throws Exception {
        TestUtils.testSerialization(new CrawlHost("hi"));
    }
    
    
}
