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
import org.archive.modules.canonicalize.StripUserinfoRule;
import org.archive.state.ModuleTestBase;


/**
 * Test stripping of userinfo from an url.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripUserinfoRuleTest extends ModuleTestBase {

    public void testCanonicalize() throws URIException {
        String url = "http://WWW.aRchive.Org/index.html";
        final String expectedResult = url;
        String result = (new StripUserinfoRule()).
            canonicalize(url);
        assertTrue("Mangled no userinfo " + result,
            url.equals(result));
        url = "http://stack:password@WWW.aRchive.Org/index.html";
        result = (new StripUserinfoRule()).
            canonicalize(url);
        assertTrue("Didn't strip userinfo " + result,
            expectedResult.equals(result));
        url = "http://stack:pass@@@@@@word@WWW.aRchive.Org/index.html";
        result = (new StripUserinfoRule()).
            canonicalize(url);
        assertTrue("Didn't get to last @ " + result,
            expectedResult.equals(result));
        url = "ftp://stack:pass@@@@@@word@archive.org/index.html";
        result = (new StripUserinfoRule()).
            canonicalize(url);
        assertTrue("Didn't get to last @ " + result,
            "ftp://archive.org/index.html".equals(result));
    }
}
