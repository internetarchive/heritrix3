/*
 * Created on Dec 15, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.archive.crawler.frontier;

import org.archive.modules.CrawlURI;

/**
 * CostAssignmentPolicy considering all URIs costless -- essentially
 * disabling budgetting features.
 * 
 * @author gojomo
 */
public class ZeroCostAssignmentPolicy extends CostAssignmentPolicy {
    private static final long serialVersionUID = 1L;

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.CostAssignmentPolicy#costOf(org.archive.crawler.datamodel.CrawlURI)
     */
    public int costOf(CrawlURI curi) {
        return 0;
    }

}
