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

import org.archive.modules.CrawlURI;


/**
 * Rule applies configured decision to any URIs that, when 
 * expressed in SURT form, do *not* begin with one of the prefixes
 * in the configured set. 
 * 
 * The set can be filled with SURT prefixes implied or
 * listed in the seeds file, or another external file. 
 *
 * @author gojomo
 */
public class NotSurtPrefixedDecideRule extends SurtPrefixedDecideRule {

    private static final long serialVersionUID = -7491388438128566377L;

    //private static final Logger logger =
    //    Logger.getLogger(NotSurtPrefixedDecideRule.class.getName());
    /**
     * Usual constructor. 
     * @param name
     */
    public NotSurtPrefixedDecideRule() {
    }

    /**
     * Evaluate whether given object's URI is NOT in the SURT
     * prefix set -- simply reverse superclass's determination
     * 
     * @param object
     * @return true if regex is matched
     */
    protected boolean evaluate(CrawlURI object) {
        return !super.evaluate(object);
    }
}
