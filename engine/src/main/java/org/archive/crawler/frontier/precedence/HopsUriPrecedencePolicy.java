/* HopsUriPrecedencePolicy
*
* $Id: CostAssignmentPolicy.java 4981 2007-03-12 07:06:01Z paul_jack $
*
* Created on Nov 21, 2007
*
* Copyright (C) 2007 Internet Archive.
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
*/
package org.archive.crawler.frontier.precedence;

import org.archive.modules.CrawlURI;

/**
 * UriPrecedencePolicy which assigns URIs a precedence equal to the number
 * of hops in its hops-path-from-seed (either all hops or just navlink ('L')
 * hops. 
 */
public class HopsUriPrecedencePolicy extends BaseUriPrecedencePolicy {
    private static final long serialVersionUID = 2602303562177294731L;

    /** whether to count only navlinks ('L'), or all hops */
    {
        setNavlinksOnly(true);
    }
    public boolean getNavlinksOnly() {
        return (Boolean) kp.get("navlinksOnly");
    }
    public void setNavlinksOnly(boolean navsOnly) {
        kp.put("navlinksOnly",navsOnly);
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.precedence.BaseUriPrecedencePolicy#calculatePrecedence(org.archive.crawler.datamodel.CrawlURI)
     */
    @Override
    protected int calculatePrecedence(CrawlURI curi) {
        return super.calculatePrecedence(curi) + 
            ((getNavlinksOnly()) 
                    ? curi.getLinkHopCount() 
                    : curi.getPathFromSeed().length());
    }
}
