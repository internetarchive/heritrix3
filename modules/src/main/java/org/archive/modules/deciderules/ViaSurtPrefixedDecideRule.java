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
import org.archive.util.SurtPrefixSet;
import org.springframework.beans.factory.annotation.Required;

/**
 * Rule applies the configured decision for any URI which has a 'via' whose
 * surtform matches any surt specified in the surtPrefixes list
 * 
 * 
 * @author adam-miller
 */
public class ViaSurtPrefixedDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 1L;
    
    protected SurtPrefixSet surtPrefixes = new SurtPrefixSet();

    public List<String> getSurtPrefixes() {
        return new ArrayList<String>(surtPrefixes);
    }
    @Required
    public void setSurtPrefixes(List<String> surtPrefixes) {
        this.surtPrefixes.clear();

        if(surtPrefixes!=null) {
            for(String surt : surtPrefixes) {
                this.surtPrefixes.considerAsAddDirective(surt);
            }
        }
    }

    /**
     * Evaluate whether given object's surt form
     * matches one of the supplied surts
     * 
     * @param object
     * @return true if a surt prefix matches
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        if (uri.getVia() != null && getSurtPrefixes() !=null){
            return surtPrefixes.containsPrefixOf(SurtPrefixSet.getCandidateSurt(uri.getVia()));
        }
        else
            return false;
    }

}
