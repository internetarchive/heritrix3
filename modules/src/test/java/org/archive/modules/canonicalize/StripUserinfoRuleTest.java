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

import org.archive.url.URIException;
import org.archive.state.ModuleTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Test stripping of userinfo from an url.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripUserinfoRuleTest extends ModuleTestBase {

    @Test
    public void testCanonicalize() throws URIException {
        String url = "http://WWW.aRchive.Org/index.html";
        final String expectedResult = url;
        String result = (new StripUserinfoRule()).
            canonicalize(url);
        assertEquals(url, result, "Mangled no userinfo " + result);
        url = "http://stack:password@WWW.aRchive.Org/index.html";
        result = (new StripUserinfoRule()).
            canonicalize(url);
        assertEquals(expectedResult, result, "Didn't strip userinfo " + result);
        url = "http://stack:pass@@@@@@word@WWW.aRchive.Org/index.html";
        result = (new StripUserinfoRule()).
            canonicalize(url);
        assertEquals(expectedResult, result, "Didn't get to last @ " + result);
        url = "ftp://stack:pass@@@@@@word@archive.org/index.html";
        result = (new StripUserinfoRule()).
            canonicalize(url);
        assertEquals("ftp://archive.org/index.html", result, "Didn't get to last @ " + result);
    }
}
