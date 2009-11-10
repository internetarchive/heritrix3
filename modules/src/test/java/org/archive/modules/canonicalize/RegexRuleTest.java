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
