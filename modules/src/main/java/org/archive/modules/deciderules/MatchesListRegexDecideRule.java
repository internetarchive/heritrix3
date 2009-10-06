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

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.archive.modules.CrawlURI;

/**
 * Rule applies configured decision to any CrawlURIs whose String URI
 * matches the supplied regexs.
 * <p>
 * The list of regular expressions can be considered logically AND or OR.
 *
 * @author Kristinn Sigurdsson
 * 
 * @see MatchesRegexDecideRule
 */
public class MatchesListRegexDecideRule extends PredicatedDecideRule {
    private static final long serialVersionUID = 3L;
    private static final Logger logger =
        Logger.getLogger(MatchesListRegexDecideRule.class.getName());

    /**
     * The list of regular expressions to evalute against the URI.
     */
    {
        setRegexList(new ArrayList<Pattern>());
    }
    @SuppressWarnings("unchecked")
    public List<Pattern> getRegexList() {
        return (List<Pattern>) kp.get("regexList");
    }
    public void setRegexList(List<Pattern> patterns) {
        kp.put("regexList", patterns);
    }

    /**
     * True if the list of regular expression should be considered as logically
     * AND when matching. False if the list of regular expressions should be
     * considered as logically OR when matching.
     */
    {
        setListLogicalOr(true);
    }
    public boolean getListLogicalOr() {
        return (Boolean) kp.get("listLogicalOr");
    }
    public void setListLogicalOr(boolean listLogicalOr) {
        kp.put("listLogicalOr",listLogicalOr);
    }

    /**
     * Usual constructor. 
     */
    public MatchesListRegexDecideRule() {
    }

    /**
     * Evaluate whether given object's string version
     * matches configured regexes
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        List<Pattern> regexes = getRegexList();
        if(regexes.size()==0){
            return false;
        }

        String str = uri.toString();
        boolean listLogicOR = getListLogicalOr();

        for (Pattern p: regexes) {
            boolean matches = p.matcher(str).matches();

            if (logger.isLoggable(Level.FINER)) {
                logger.finer("Tested '" + str + "' match with regex '" +
                    p.pattern() + " and result was " + matches);
            }
            
            if(matches){
                if(listLogicOR){
                    // OR based and we just got a match, done!
                    logger.fine("Matched: " + str);
                    return true;
                }
            } else {
                if(listLogicOR == false){
                    // AND based and we just found a non-match, done!
                    return false;
                }
            }
        }
        
        if (listLogicOR) {
            return false;
        } else {
            return true;
        }
    }
    
}