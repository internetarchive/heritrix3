/* StripSessionIDsTest
 * 
 * Created on Oct 6, 2004
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
package org.archive.modules.canonicalize;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.canonicalize.StripSessionIDs;
import org.archive.state.ModuleTestBase;


/**
 * Test stripping of session ids.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripSessionIDsTest extends ModuleTestBase {
    private static final String  BASE = "http://www.archive.org/index.html";

    public void testCanonicalize() throws URIException {
        String str32id = "0123456789abcdefghijklemopqrstuv";
        String url = BASE + "?jsessionid=" + str32id;
        String expectedResult = BASE + "?";
        String result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // Test that we don't strip if not 32 chars only.
        url = BASE + "?jsessionid=" + str32id + '0';
        expectedResult = url;
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // Test what happens when followed by another key/value pair.
        url = BASE + "?jsessionid=" + str32id + "&x=y";
        expectedResult = BASE + "?x=y";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // Test what happens when followed by another key/value pair and
        // prefixed by a key/value pair.
        url = BASE + "?one=two&jsessionid=" + str32id + "&x=y";
        expectedResult = BASE + "?one=two&x=y";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // Test what happens when prefixed by a key/value pair.
        url = BASE + "?one=two&jsessionid=" + str32id;
        expectedResult = BASE + "?one=two&";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // Test aspsession.
        url = BASE + "?aspsessionidABCDEFGH=" + "ABCDEFGHIJKLMNOPQRSTUVWX"
            + "&x=y";
        expectedResult = BASE + "?x=y";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // Test archive phpsession.
        url = BASE + "?phpsessid=" + str32id + "&x=y";
        expectedResult = BASE + "?x=y";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // With prefix too.
        url = BASE + "?one=two&phpsessid=" + str32id + "&x=y";
        expectedResult = BASE + "?one=two&x=y";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // With only prefix
        url = BASE + "?one=two&phpsessid=" + str32id;
        expectedResult = BASE + "?one=two&";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));
        
        // Test sid.
        url = BASE + "?" + "sid=9682993c8daa2c5497996114facdc805" + "&x=y";
        expectedResult = BASE + "?x=y";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));	
        
        // Igor test.
        url = BASE + "?" + "sid=9682993c8daa2c5497996114facdc805" + "&" +
            "jsessionid=" + str32id;
        expectedResult = BASE + "?";
        result = new StripSessionIDs().canonicalize(url);
        assertTrue("Failed " + result, expectedResult.equals(result));  
    }
}
