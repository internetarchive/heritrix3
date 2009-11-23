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

import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;

/**
 * The most simple forced-rescheduling step possible: use a local
 * setting (perhaps overlaid to vary based on the URI) to set an exact
 * future reschedule time, as a delay from now. Unless the 
 * reschedulDelaySeconds value is changed from its default, URIs 
 * are not rescheduled.
 *
 * @author gojomo
 * @version $Date: 2009-11-16 22:10:42 -0800 (Mon, 16 Nov 2009) $, $Revision: 6665 $
 */
public class ReschedulingProcessor extends Processor {
    /**
     * amount of time to wait before forcing a URI to be rescheduled
     * default of -1 means "don't reschedule"
     */
    {
        setRescheduleDelaySeconds(-1L);
    }
    public long getRescheduleDelaySeconds() {
        return (Long) kp.get("rescheduleDelaySeconds");
    }
    public void setRescheduleDelaySeconds(long rescheduleDelaySeconds) {
        kp.put("rescheduleDelaySeconds",rescheduleDelaySeconds);
    }

    public ReschedulingProcessor() {
        super();
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return true;
    }
    
    @Override
    protected void innerProcess(CrawlURI curi) {
        if(curi.isPrerequisite()) {
            // never resched prereqs; they get rescheduled as needed
            curi.setRescheduleTime(-1); 
            return; 
        }
        long rds = getRescheduleDelaySeconds();
        if(rds>0) {
            curi.setRescheduleTime(System.currentTimeMillis()+(1000*rds));
        } else {
            curi.setRescheduleTime(-1); 
        }
    }
}
