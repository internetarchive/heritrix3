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

import java.util.regex.Pattern;

import org.archive.modules.CrawlURI;

/**
 * Rule applies configured decision to any CrawlURIs whose String URI
 * matches the supplied regex.
 *
 * @author gojomo
 */
public class MatchesRegexDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 2L;
    
    {
        setRegex(Pattern.compile("."));
    }
    public Pattern getRegex() {
        return (Pattern) kp.get("regex");
    }
    public void setRegex(Pattern regex) {
        kp.put("regex",regex); 
    }
    
    /**
     * Usual constructor. 
     */
    public MatchesRegexDecideRule() {
    }
    
    
    /**
     * Evaluate whether given object's string version
     * matches configured regex
     * 
     * @param object
     * @return true if regex is matched
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        Pattern p = getRegex();
        return p.matcher(getString(uri)).matches();
    }
    
    protected String getString(CrawlURI uri) {
        return uri.toString();
    }
}
