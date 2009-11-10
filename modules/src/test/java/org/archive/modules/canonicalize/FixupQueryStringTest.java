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
import org.archive.state.ModuleTestBase;

/**
 * Test we strip trailing question mark.
 * @author stack
 * @version $Date$, $Revision$
 */
public class FixupQueryStringTest extends ModuleTestBase {

    public void testCanonicalize() throws URIException {
        final String url = "http://WWW.aRchive.Org/index.html";
        assertTrue("Mangled " + url,
            url.equals((new FixupQueryString()).
                canonicalize(url)));
        assertTrue("Failed to strip '?' " + url,
            url.equals((new FixupQueryString()).
                canonicalize(url + "?")));
        assertTrue("Failed to strip '?&' " + url,
            url.equals((new FixupQueryString()).
                canonicalize(url + "?&")));
        assertTrue("Failed to strip extraneous '&' " + url,
            (url + "?x=y").equals((new FixupQueryString()).
                canonicalize(url + "?&x=y")));
        String tmp = url + "?x=y";
        assertTrue("Mangled x=y " + tmp,
            tmp.equals((new FixupQueryString()).
                canonicalize(tmp)));
        String tmp2 = tmp + "&";
        String fixed = new FixupQueryString().
            canonicalize(tmp2);
        assertTrue("Mangled " + tmp2, tmp.equals(fixed));
    }
}
