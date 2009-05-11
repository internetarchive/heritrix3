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
 * Rule REJECTs any CrawlURIs whose total number of path-segments (as
 * indicated by the count of '/' characters not including the first '//')
 * is over a given threshold.
 *
 * @author gojomo
 */
public class TooManyPathSegmentsDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 3L;

    /** default for this class is to REJECT */
    {
        setDecision(DecideResult.REJECT);
    }
    
    /**
     * Number of path segments beyond which this rule will reject URIs.
     */
    {
        setMaxPathDepth(20);
    }
    public int getMaxPathDepth() {
        return (Integer) kp.get("maxPathDepth");
    }
    public void setMaxPathDepth(int maxPathDepth) {
        kp.put("maxPathDepth", maxPathDepth);
    }
    /**
     * Usual constructor. 
     */
    public TooManyPathSegmentsDecideRule() {
    }

    /**
     * Evaluate whether given object is over the threshold number of
     * path-segments.
     * 
     * @param object
     * @return true if the path-segments is exceeded
     */
    @Override
    protected boolean evaluate(ProcessorURI curi) {
        String uri = curi.toString();
        int count = 0;
        int threshold = getMaxPathDepth();
        for (int i = 0; i < uri.length(); i++) {
            if (uri.charAt(i) == '/') {
                count++;
            }
            if (count > threshold) {
                return true;
            }
        }
        return false;
    }

}
