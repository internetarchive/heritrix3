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
package org.archive.modules.deciderules.surt;

import org.archive.util.SurtPrefixSet;

/**
 * Rule applies configured decision to any URIs that
 * are on one of the hosts in the configured set of
 * hosts, filled from the seed set. 
 *
 * @author gojomo
 */
public class OnHostsDecideRule extends SurtPrefixedDecideRule {

    private static final long serialVersionUID = 3L;

    //private static final Logger logger =
    //    Logger.getLogger(OnHostsDecideRule.class.getName());
    /**
     * Usual constructor. 
     * @param name
     */
    public OnHostsDecideRule() {
        super();
    }

    /**
     * Patch the SURT prefix set so that it only includes host-enforcing prefixes
     * 
     * @see org.archive.modules.deciderules.surt.SurtPrefixedDecideRule#readPrefixes()
     */
    protected void readPrefixes(/*StateProvider context*/) {
        buildSurtPrefixSet();
        surtPrefixes.convertAllPrefixesToHosts();
        dumpSurtPrefixSet();
    }

	protected String prefixFrom(String uri) {
		return SurtPrefixSet.convertPrefixToHost(super.prefixFrom(uri));
	}
}
