/* FrontierScheduler
 * 
 * $Id$
 *
 * Created on June 6, 2005
 * 
 * Copyright (C) 2005 Internet Archive.
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


import static org.archive.modules.fetcher.FetchStatusCodes.S_DEFERRED;

import java.util.concurrent.locks.ReentrantLock;

import org.archive.crawler.datamodel.CrawlURI;
import org.archive.crawler.framework.Frontier;
import org.archive.modules.PostProcessor;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorURI;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * 'Schedule' with the Frontier CrawlURIs being carried by the passed
 * CrawlURI.
 * Adds either prerequisites or whatever is in CrawlURI outlinks to the
 * Frontier.  Run a Scoper ahead of this processor so only links that
 * are in-scope get scheduled.
 * @author stack
 */
public class FrontierScheduler extends Processor 
implements PostProcessor {

    private static final long serialVersionUID = -3L;

    ReentrantLock lock = new ReentrantLock(true);
    
    /**
     * The frontier to use.
     */
    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    /**
     */
    public FrontierScheduler() {
    }
    
    protected boolean shouldProcess(ProcessorURI puri) {
        return puri instanceof CrawlURI;
    }

    @Override
    protected void innerProcess(final ProcessorURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        // Handle any prerequisites when S_DEFERRED for prereqs
        if (curi.hasPrerequisiteUri() && curi.getFetchStatus() == S_DEFERRED) {
            handlePrerequisites(curi);
            return;
        }

        try {
            lock.lock();
            for (CrawlURI cauri: curi.getOutCandidates()) {
                schedule(cauri);
            }
        } finally {
            lock.unlock();
        }
    }

    protected void handlePrerequisites(CrawlURI curi) {
        schedule((CrawlURI)curi.getPrerequisiteUri());
    }

    /**
     * Schedule the given {@link CrawlURI CrawlURI} with the Frontier.
     * @param caUri The CrawlURI to be scheduled.
     */
    protected void schedule(CrawlURI caUri) {
        frontier.schedule(caUri);
    }
}
