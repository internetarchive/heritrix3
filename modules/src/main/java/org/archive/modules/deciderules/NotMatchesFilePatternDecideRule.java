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

import org.archive.modules.CrawlURI;


/**
 * Rule applies configured decision to any URIs which do *not*
 * match the supplied (file-pattern) regex.
 *
 * @author gojomo
 */
public class NotMatchesFilePatternDecideRule
extends MatchesFilePatternDecideRule {

    private static final long serialVersionUID = -8161371026787859554L;
    //private static final Logger logger =
    //    Logger.getLogger(NotMatchesRegexDecideRule.class.getName());

    /**
     * Usual constructor. 
     */
    public NotMatchesFilePatternDecideRule() {
    }
    
    /**
     * Evaluate whether given object's string version does not match 
     * configured regex (by reversing the superclass's answer).
     * 
     * @param uri CrawlURI to make decision about.
     * @return true if the regex is not matched
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        return ! super.evaluate(uri);
    }
}
