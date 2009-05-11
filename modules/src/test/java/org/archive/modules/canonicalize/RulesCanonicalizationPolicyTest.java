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
import org.archive.util.TmpDirTestCase;

/**
 * Test canonicalization
 * 
 * @contributor stack
 */
public class RulesCanonicalizationPolicyTest extends TmpDirTestCase {

    private RulesCanonicalizationPolicy policy;
    
    protected void setUp() throws Exception {
        super.setUp();
        policy =  new RulesCanonicalizationPolicy();
//        this.rules = new ArrayList<CanonicalizationRule>();
//        this.rules.add(new LowercaseRule());
//        this.rules.add(new StripUserinfoRule());
//        this.rules.add(new StripWWWRule());
//        this.rules.add(new StripSessionIDs());
//        this.rules.add(new FixupQueryString());
    }
    
    public void testCanonicalize() throws URIException {
        final String scheme = "http://";
        final String nonQueryStr = "archive.org/index.html";
        final String result = scheme + nonQueryStr;
        assertTrue("Mangled original", result.equals(
                policy.canonicalize(result)));
        String tmp = scheme + "www." + nonQueryStr;
        assertTrue("Mangled www", result.equals(
                policy.canonicalize(tmp)));
        tmp = scheme + "www." + nonQueryStr +
            "?jsessionid=01234567890123456789012345678901";
        assertTrue("Mangled sessionid", result.equals(
                policy.canonicalize(tmp)));
        tmp = scheme + "www." + nonQueryStr +
            "?jsessionid=01234567890123456789012345678901";
        assertTrue("Mangled sessionid", result.equals(
                policy.canonicalize(tmp)));
    }
}
