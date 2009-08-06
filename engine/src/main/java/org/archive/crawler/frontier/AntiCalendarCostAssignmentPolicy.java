/* AntiCalendarCostAssignmentPolicy
*
* $Id$
*
* Created on Dec 15, 2004
*
* Copyright (C) 2004 Internet Archive.
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
package org.archive.crawler.frontier;

import java.util.regex.Matcher;

import org.archive.modules.CrawlURI;
import org.archive.util.TextUtils;

/**
 * CostAssignmentPolicy that further penalizes URIs with
 * calendar-suggestive strings in them, with an extra unit 
 * of cost. 
 * 
 * Will catch some 'innocent' URIs, but only when uncaught 
 * large-volume chaff is ranked higher than caught 'wheat' 
 * will this cause notable problems.
 * 
 * @author gojomo
 */
public class AntiCalendarCostAssignmentPolicy extends UnitCostAssignmentPolicy {

    private static final long serialVersionUID = 3L;

    public static String CALENDARISH =
            "(?i)(calendar)|(year)|(month)|(day)|(date)|(viewcal)" +
            "|(\\D19\\d\\d\\D)|(\\D20\\d\\d\\D)|(event)|(yr=)" +
            "|(calendrier)|(jour)";
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.CostAssignmentPolicy#costOf(org.archive.crawler.datamodel.CrawlURI)
     */
    public int costOf(CrawlURI curi) {
        int cost = super.costOf(curi);
        Matcher m = TextUtils.getMatcher(CALENDARISH, curi.toString());
        if (m.find()) {
            cost++;
            // TODO: consider if multiple occurences should cost more
        }
        TextUtils.recycleMatcher(m);
        return cost;
    }
}
