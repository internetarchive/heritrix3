/* FixupQueryStrTest
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
