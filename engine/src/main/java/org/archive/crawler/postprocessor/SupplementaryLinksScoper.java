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

package org.archive.crawler.postprocessor;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.framework.Scoper;
import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.AcceptDecideRule;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.DecideRule;


/**
 * Run CrawlURI links carried in the passed CrawlURI through a filter
 * and 'handle' rejections.
 * Used to do supplementary processing of links after they've been scope
 * processed and ruled 'in-scope' by LinkScoper.  An example of
 * 'supplementary processing' would check that a Link is intended for
 * this host to crawl in a multimachine crawl setting. Configure filters to
 * rule on links.  Default handler writes rejected URLs to disk.  Subclass
 * to handle rejected URLs otherwise.
 * @author stack
 */
public class SupplementaryLinksScoper extends Scoper {

    private static final long serialVersionUID = -3L;

    private static Logger LOGGER =
        Logger.getLogger(SupplementaryLinksScoper.class.getName());
    

    /**
     * DecideRules which if their final decision on a link is
     * REJECT, cause the link to be ruled out-of-scope, even 
     * if it had previously been accepted by the main scope.
     */
    {
        setSupplementaryRule(new AcceptDecideRule());
    }
    public DecideRule getSupplementaryRule() {
        return (DecideRule) kp.get("supplementaryRule");
    }
    public void setSupplementaryRule(DecideRule rule) {
        kp.put("supplementaryRule", rule);
    }
    
    /**
     * @param name Name of this filter.
     */
    public SupplementaryLinksScoper() {
        super();
    }

    
    protected boolean shouldProcess(CrawlURI puri) {
        return puri instanceof CrawlURI;
    }
    
    
    protected void innerProcess(final CrawlURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        
        // If prerequisites or no links, nothing to be done in here.
        if (curi.hasPrerequisiteUri() || curi.getOutLinks().isEmpty()) {
            return;
        }
        
//        Collection<CrawlURI> inScopeLinks = new HashSet<CrawlURI>();
        Iterator<CrawlURI> iter = curi.getOutCandidates().iterator();
        while (iter.hasNext()) {
            CrawlURI cauri = iter.next();
            if (!isInScope(cauri)) {
                iter.remove();
            }
        }
//        for (CrawlURI cauri: curi.getOutCandidates()) {
//            if (isInScope(cauri)) {
//                inScopeLinks.add(cauri);
//            }
//        }
        // Replace current links collection w/ inscopeLinks.  May be
        // an empty collection.
//        curi.replaceOutlinks(inScopeLinks);
    }
    
    protected boolean isInScope(CrawlURI caUri) {
        // TODO: Fix filters so work on CrawlURI.
        CrawlURI curi = (caUri instanceof CrawlURI)?
            (CrawlURI)caUri:
            new CrawlURI(caUri.getUURI());
        boolean result = false;
        DecideRule seq = getSupplementaryRule();
        if (seq.decisionFor(curi) == DecideResult.ACCEPT) {
            result = true;
            if (LOGGER.isLoggable(Level.FINER)) {
                LOGGER.finer("Accepted: " + caUri);
            }
        } else {
            outOfScope(caUri);
        }
        return result;
    }
    
    /**
     * Called when a CrawlURI is ruled out of scope.
     * @param caUri CrawlURI that is out of scope.
     */
    protected void outOfScope(CrawlURI caUri) {
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        LOGGER.info(caUri.getUURI().toString());
    }
}
