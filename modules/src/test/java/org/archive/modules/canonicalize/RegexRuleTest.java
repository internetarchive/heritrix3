/* RegexRuleTest
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

import java.util.regex.Pattern;

import javax.management.InvalidAttributeValueException;

import org.apache.commons.httpclient.URIException;
import org.archive.state.ModuleTestBase;


/**
 * Test the regex rule.
 * @author stack
 * @version $Date$, $Revision$
 */
public class RegexRuleTest extends ModuleTestBase {

    public void testCanonicalize()
    throws URIException, InvalidAttributeValueException {
        final String url = "http://www.aRchive.Org/index.html";
        RegexRule rr = new RegexRule();
        rr.canonicalize(url);
        String product = rr.canonicalize(url);
        assertTrue("Default doesn't work.",  url.equals(product));
    }

    public void testSessionid()
    throws InvalidAttributeValueException {
        final String urlBase = "http://joann.com/catalog.jhtml";
        final String urlMinusSessionid = urlBase + "?CATID=96029";
        final String url = urlBase +
		    ";$sessionid$JKOFFNYAAKUTIP4SY5NBHOR50LD3OEPO?CATID=96029";
        RegexRule rr = new RegexRule();
        rr.setRegex(Pattern.compile("^(.+)(?:;\\$sessionid\\$[A-Z0-9]{32})(\\?.*)+$"));
        rr.setFormat("$1$2");
        String product = rr.canonicalize(url);
        assertTrue("Failed " + url, urlMinusSessionid.equals(product));
    }
    
// This should fail -- no backrefs to nonexistent match; very easy to 
// add match of empty-string if that's acceptable in replace.
//    public void testNullFormat()
//    throws InvalidAttributeValueException {
//        final String urlBase = "http://joann.com/catalog.jhtml";
//        final String url = urlBase +
//            ";$sessionid$JKOFFNYAAKUTIP4SY5NBHOR50LD3OEPO";
//        RegexRule rr = new RegexRule();
//        rr.setRegex(Pattern.compile("^(.+)(?:;\\$sessionid\\$[A-Z0-9]{32})$"));
//        rr.setFormat("$1$2");
//        String product = rr.canonicalize(url);
//        assertTrue("Failed " + url, urlBase.equals(product));
//    }
}
