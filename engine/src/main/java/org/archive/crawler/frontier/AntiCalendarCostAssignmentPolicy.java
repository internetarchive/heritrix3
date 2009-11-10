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
