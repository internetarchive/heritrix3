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
