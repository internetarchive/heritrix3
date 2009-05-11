/* MimetypeUtilsTest
 * 
 * $Id$
 * 
 * Created on Sep 22, 2004
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

import junit.framework.TestCase;

/**
 * @author stack
 * @version $Date$, $Revision$
 */
public class MimetypeUtilsTest extends TestCase {

	public void testStraightTruncate() {
        assertTrue("Straight broken",
            MimetypeUtils.truncate("text/html").equals("text/html"));
	}
    
    public void testWhitespaceTruncate() {
        assertTrue("Null broken",
            MimetypeUtils.truncate(null).equals("no-type"));
        assertTrue("Empty broken",
                MimetypeUtils.truncate("").equals("no-type"));
        assertTrue("Tab broken",
                MimetypeUtils.truncate("    ").equals("no-type"));
        assertTrue("Multispace broken",
                MimetypeUtils.truncate("    ").equals("no-type"));
        assertTrue("NL broken",
                MimetypeUtils.truncate("\n").equals("no-type"));
    }
    
    public void testCommaTruncate() {
        assertTrue("Comma broken",
            MimetypeUtils.truncate("text/html,text/html").equals("text/html"));
        assertTrue("Comma space broken",
            MimetypeUtils.truncate("text/html, text/html").
                equals("text/html"));
        assertTrue("Charset broken",
            MimetypeUtils.truncate("text/html;charset=iso9958-1").
                equals("text/html"));
        assertTrue("Charset space broken",
            MimetypeUtils.truncate("text/html; charset=iso9958-1").
                equals("text/html"));
        assertTrue("dbl text/html space charset broken", MimetypeUtils.
            truncate("text/html, text/html; charset=iso9958-1").
                equals("text/html"));
    }
}
