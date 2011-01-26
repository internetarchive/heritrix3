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
 * Rule REJECTs any CrawlURIs whose total number of hops (length of the 
 * hopsPath string, traversed links of any type) is over a threshold.
 * Otherwise returns PASS.
 *
 * @author gojomo
 */
public class TooManyHopsDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 3L;

    /** default for this class is to REJECT */
    {
        setDecision(DecideResult.REJECT);
    }
    
    /**
     * Max path depth for which this filter will match.
     */
    {
            setMaxHops(20);
    }
    public int getMaxHops() {
        return (Integer) kp.get("maxHops");
    }
    public void setMaxHops(int maxHops) {
        kp.put("maxHops", maxHops);
    }
    
    /**
     * Usual constructor. 
     */
    public TooManyHopsDecideRule() {
    }

    /**
     * Evaluate whether given object is over the threshold number of
     * hops.
     * 
     * @param object
     * @return true if the mx-hops is exceeded
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        return uri.getHopCount() > getMaxHops();
    }

}
