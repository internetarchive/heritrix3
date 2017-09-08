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


public class FetchStatusNotMatchesRegexDecideRule
extends FetchStatusMatchesRegexDecideRule {

    private static final long serialVersionUID = -2220182698344063577L;
//    private  final Logger logger = Logger.getLogger(this.getClass().getName());
    
    /**
     * Usual constructor. 
     */
    public FetchStatusNotMatchesRegexDecideRule() {
    }

    /**
     * Evaluate whether given object's FetchStatus does not match 
     * configured regex (by reversing the superclass's answer).
     * 
     * @param object Object to make decision about.
     * @return true if the regex is not matched
     */
    @Override
    protected boolean evaluate(CrawlURI object) {
        return ! super.evaluate(object);
    }
}