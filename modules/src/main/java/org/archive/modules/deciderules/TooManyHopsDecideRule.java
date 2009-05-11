/* AcceptRule
*
* $Id$
*
* Created on Apr 1, 2005
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
*/
package org.archive.modules.deciderules;

import org.archive.modules.ProcessorURI;



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
    protected boolean evaluate(ProcessorURI uri) {
        String hops = uri.getPathFromSeed();
        if (hops == null) {
            return false;
        }
        if (hops.length() <= getMaxHops()) {
            return false;
        }
        return true;
    }

}
