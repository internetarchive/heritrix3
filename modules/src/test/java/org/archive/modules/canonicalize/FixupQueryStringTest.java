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
 * Test we strip trailing question mark.
 * @author stack
 * @version $Date$, $Revision$
 */
public class FixupQueryStringTest extends ModuleTestBase {
    @Test
    public void testCanonicalize() throws URIException {
        final String url = "http://WWW.aRchive.Org/index.html";
        assertEquals(url, new FixupQueryString().canonicalize(url),
                "Mangled " + url);
        assertEquals(url, new FixupQueryString().canonicalize(url + "?"),
                "Failed to strip '?' " + url);
        assertEquals(url, new FixupQueryString().canonicalize(url + "?&"),
                "Failed to strip '?&' " + url);
        assertEquals(url + "?x=y", new FixupQueryString().canonicalize(url + "?&x=y"),
                "Failed to strip extraneous '&' " + url);
        String tmp = url + "?x=y";
        assertEquals(tmp, new FixupQueryString().canonicalize(tmp),
                "Mangled x=y " + tmp);
        String tmp2 = tmp + "&";
        String fixed = new FixupQueryString().canonicalize(tmp2);
        assertEquals(tmp, fixed, "Mangled " + tmp2);
    }
}
