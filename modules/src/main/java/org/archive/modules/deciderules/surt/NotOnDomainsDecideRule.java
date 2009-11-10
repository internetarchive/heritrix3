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
 * Rule applies configured decision to any URIs that are
 * *not* in one of the domains in the configured set of
 * domains, filled from the seed set. 
 *
 * @author gojomo
 */
public class NotOnDomainsDecideRule extends OnDomainsDecideRule {

    private static final long serialVersionUID = -1634035244888724934L;
    
    //private static final Logger logger =
    //    Logger.getLogger(NotOnDomainsDecideRule.class.getName());
    /**
     * Usual constructor. 
     * @param name
     */
    public NotOnDomainsDecideRule() {
    }

    /**
     * Evaluate whether given object's URI is NOT in the set of
     * domains -- simply reverse superclass's determination
     * 
     * @param object to evaluate
     * @return true if URI is not in domain set
     */
    protected boolean evaluate(CrawlURI object) {
        boolean superDecision = super.evaluate(object);
        return !superDecision;
    }
}
