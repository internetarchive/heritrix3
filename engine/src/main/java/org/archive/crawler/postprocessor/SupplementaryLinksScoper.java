/* SupplementaryLinksScoper
 * 
 * $Id$
 *
 * Created on Oct 2, 2003
 * 
 * Copyright (C) 2003 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package org.archive.crawler.postprocessor;

import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.datamodel.CrawlURI;
import org.archive.crawler.framework.Scoper;
import org.archive.modules.PostProcessor;
import org.archive.modules.ProcessorURI;
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
public class SupplementaryLinksScoper extends Scoper implements PostProcessor {

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

    
    protected boolean shouldProcess(ProcessorURI puri) {
        return puri instanceof CrawlURI;
    }
    
    
    protected void innerProcess(final ProcessorURI puri) {
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
