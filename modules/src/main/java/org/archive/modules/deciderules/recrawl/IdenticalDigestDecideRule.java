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

package org.archive.modules.deciderules.recrawl;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_CONTENT_DIGEST;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_FETCH_HISTORY;

import java.util.Map;

import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.PredicatedDecideRule;

/**
 * Rule applies configured decision to any CrawlURIs whose prior-history
 * content-digest matches the latest fetch. 
 *
 * @author gojomo
 */
public class IdenticalDigestDecideRule extends PredicatedDecideRule {
    private static final long serialVersionUID = 4275993790856626949L;

    /** default for this class is to REJECT */
    {
        setDecision(DecideResult.REJECT);
    }
    
    /**
     * Usual constructor. 
     */
    public IdenticalDigestDecideRule() {
    }

    /**
     * Evaluate whether given CrawlURI's content-digest exactly 
     * matches that of preceding fetch. 
     *
     * @param object should be CrawlURI
     * @return true if current-fetch content-digest matches previous
     */
    protected boolean evaluate(CrawlURI curi) {
        return hasIdenticalDigest(curi);
    }


    /**
     * Utility method for testing if a CrawlURI's last two history 
     * entries (one being the most recent fetch) have identical 
     * content-digest information. 
     * 
     * @param curi CrawlURI to test
     * @return true if last two history entries have identical digests, 
     * otherwise false
     */
    @SuppressWarnings("unchecked")
    public static boolean hasIdenticalDigest(CrawlURI curi) {
        if(curi.containsDataKey(A_FETCH_HISTORY)) {
            Map<String,Object>[] history = 
                (Map<String,Object>[])curi.getData().get(A_FETCH_HISTORY);
            return history[0] != null 
                   && history[0].containsKey(A_CONTENT_DIGEST)
                   && history[1] != null
                   && history[1].containsKey(A_CONTENT_DIGEST)
                   && history[0].get(A_CONTENT_DIGEST).equals(
                           history[1].get(A_CONTENT_DIGEST));
        } else {
            return false;
        }
    }

}
