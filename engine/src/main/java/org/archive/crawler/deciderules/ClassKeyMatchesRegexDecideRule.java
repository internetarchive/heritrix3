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
package org.archive.crawler.deciderules;


import org.archive.crawler.framework.CrawlController;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.MatchesRegexDecideRule;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Rule applies configured decision to any CrawlURI class key -- i.e.
 * {@link CrawlURI#getClassKey()} -- matches matches supplied regex.
 *
 * @author gojomo
 */
public class ClassKeyMatchesRegexDecideRule extends MatchesRegexDecideRule {

    private static final long serialVersionUID = 3L;

    protected CrawlController controller;
    public CrawlController getCrawlController() {
        return this.controller;
    }
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }
    
    /**
     * Usual constructor. 
     */
    public ClassKeyMatchesRegexDecideRule() {
    }

    
    @Override
    protected String getString(CrawlURI uri) {
        CrawlURI curi = (CrawlURI)uri;
        return controller.getFrontier().getClassKey(curi);
    }

}