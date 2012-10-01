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

import org.archive.modules.CrawlURI;

/**
 * Rule applies the configured decision for any URI which has a
 * fetch status equal to the 'target-status' setting. 
 *
 * @contributor gojomo
 */
public class FetchStatusDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 3L;

    protected List<Integer> statusCodes = new ArrayList<Integer>();
    public List<Integer> getStatusCodes() {
        return this.statusCodes;
    }
    public void setStatusCodes(List<Integer> codes) {
        this.statusCodes = codes; 
    }
    
    /**
     * Usual constructor. 
     */
    public FetchStatusDecideRule() {
    }

    /**
     * Evaluate whether given object is equal to the configured status
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        return getStatusCodes().contains(uri.getFetchStatus());
    }

}
