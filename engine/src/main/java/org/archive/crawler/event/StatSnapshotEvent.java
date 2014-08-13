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
 
package org.archive.crawler.event;

import org.archive.crawler.reporting.CrawlStatSnapshot;
import org.archive.crawler.reporting.StatisticsTracker;
import org.springframework.context.ApplicationEvent;


/**
 * ApplicationEvent published when the StatisticsTracker takes its
 * sample of various statistics. Other modules can observe this event
 * to perform periodic checks related to overall statistics. 
 * 
 * @contributor gojomo
 */
public class StatSnapshotEvent extends ApplicationEvent {
    private static final long serialVersionUID = 1L;
    protected CrawlStatSnapshot snapshot;
    
    public StatSnapshotEvent(StatisticsTracker stats, CrawlStatSnapshot snapshot) {
        super(stats);
        this.snapshot = snapshot;
    }

    public CrawlStatSnapshot getSnapshot() {
        return snapshot;
    }
}
