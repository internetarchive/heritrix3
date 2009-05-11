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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

/**
 * URI Canonicalizatioon Policy
 * 
 * @contributor stack
 * @contributor gojomo
 */
public class RulesCanonicalizationPolicy 
    extends UriCanonicalizationPolicy
    implements HasKeyedProperties {
    private static Logger logger =
        Logger.getLogger(RulesCanonicalizationPolicy.class.getName());
    
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    {
        setRules(getDefaultRules());
    }
    @SuppressWarnings("unchecked")
    public List<CanonicalizationRule> getRules() {
        return (List<CanonicalizationRule>) kp.get("rules");
    }
    public void setRules(List<CanonicalizationRule> rules) {
        kp.put("rules", rules);
    }
    
    /**
     * Run the passed uuri through the list of rules.
     * @param context Url to canonicalize.
     * @param rules Iterator of canonicalization rules to apply (Get one
     * of these on the url-canonicalizer-rules element in order files or
     * create a list externally).  Rules must implement the Rule interface.
     * @return Canonicalized URL.
     */
    public String canonicalize(String before) {
        String canonical = before;
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("Canonicalizing: "+before);
        }
        for (CanonicalizationRule rule : getRules()) {
            if(rule.getEnabled()) {
                canonical = rule.canonicalize(canonical);
            }
            if (logger.isLoggable(Level.FINER)) {
                logger.finer(
                    "Rule " + rule.getClass().getName() + " "
                    + (rule.getEnabled()
                            ? canonical :" (disabled)"));
            }
        }
        return canonical;
    }
    
    /**
     * A reasonable set of default rules to use, if no others are
     * provided by operator configuration.
     */
    public static List<CanonicalizationRule> getDefaultRules() {
        List<CanonicalizationRule> rules = new ArrayList<CanonicalizationRule>(6);
        rules.add(new LowercaseRule());
        rules.add(new StripUserinfoRule());
        rules.add(new StripWWWNRule());
        rules.add(new StripSessionIDs());
        rules.add(new StripSessionCFIDs());
        rules.add(new FixupQueryString());
        return rules;
    }
}
