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
 * Test stripping 'www' if present.
 * @author stack
 * @version $Date$, $Revision$
 */
public class StripWWWRuleTest extends ModuleTestBase {

    @Test
    public void testCanonicalize() throws URIException {
        String url = "http://WWW.aRchive.Org/index.html";
        String expectedResult = "http://aRchive.Org/index.html";
        String result = (new StripWWWRule()).
            canonicalize(url);
        assertEquals(expectedResult, result, "Failed " + result);
        url = "http://wWWW.aRchive.Org/index.html";
        expectedResult = "http://wWWW.aRchive.Org/index.html";
        result = (new StripWWWRule()).
            canonicalize(url);
        assertEquals(expectedResult, result, "Failed " + result);
        url = "http://ww.aRchive.Org/index.html";
        expectedResult = "http://ww.aRchive.Org/index.html";
        result = (new StripWWWRule()).
            canonicalize(url);
        assertEquals(expectedResult, result, "Failed " + result);
        url = "http://www001.aRchive.Org/index.html";
        expectedResult = "http://www001.aRchive.Org/index.html";
        result = (new StripWWWRule()).
            canonicalize(url);
        assertEquals(expectedResult, result, "Failed " + result);
    }
}
