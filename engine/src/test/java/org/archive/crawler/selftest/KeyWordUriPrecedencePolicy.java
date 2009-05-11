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

package org.archive.crawler.selftest;

import org.archive.crawler.datamodel.CrawlURI;
import org.archive.crawler.frontier.precedence.BaseUriPrecedencePolicy;

/**
 * Testing policy which uses a precedence inside the CrawlURI (presumably
 * put there earlier by KeyWordProcessor).
 * 
 * @author pjack
 */
public class KeyWordUriPrecedencePolicy extends BaseUriPrecedencePolicy {
    private static final long serialVersionUID = 1L;

    @Override
    protected int calculatePrecedence(CrawlURI curi) {
        if (curi.getPrecedence() > 0) {
            return curi.getPrecedence();
        }
        return super.calculatePrecedence(curi);
    }
}
