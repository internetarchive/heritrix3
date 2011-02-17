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
package org.archive.modules.deciderules;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.archive.modules.CrawlURI;

/**
 * Rule applies the configured decision (default REJECT) for any URI which 
 * has a URI-scheme NOT contained in the configured Set. 
 *
 * @contributor gojomo
 */
public class SchemeNotInSetDecideRule extends PredicatedDecideRule {
    private static final long serialVersionUID = 3L;

    {
        setDecision(DecideResult.REJECT);
    }
    
    /**
     * Usual constructor. 
     * @param name Name of this DecideRule.
     */
    public SchemeNotInSetDecideRule() {
    }
    
    /** set of schemes to test URI scheme */ 
    protected Set<String> schemes = new HashSet<String>(); 
    {
        // default set are those schemes Heritrix supports in usual configuration
        schemes.addAll(Arrays.asList(new String[] {"http","https","ftp","dns","whois"}));
    }
    public Set<String> getSchemes() {
        return schemes;
    }
    public void setSchemes(Set<String> schemes) {
        this.schemes = schemes;
    }

    /**
     * Evaluate whether given object is over the threshold number of
     * hops.
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        return !schemes.contains(uri.getUURI().getScheme());
    }
}
