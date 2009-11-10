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
