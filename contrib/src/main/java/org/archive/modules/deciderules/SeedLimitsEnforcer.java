/*
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Licensed to the Internet Archive (IA) by one or more individual
 * contributors.
 *
 * The IA licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.archive.modules.deciderules;

import org.archive.crawler.prefetch.QuotaEnforcer;
import org.archive.crawler.prefetch.RuntimeLimitEnforcer;
import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.crawler.util.CrawledBytesHistotable;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DecideRule that rejects any CrawlURI with {@code sourceTag} matching the
 * configured {@code seed}, if more than {@code maxBytesDownload} bytes or
 * {@code maxDocumentsDownload} urls have been downloaded from the same source
 * seed.
 * 
 * @contributor nlevitt
 * 
 * @see {@link QuotaEnforcer}
 * @see {@link RuntimeLimitEnforcer}
 */
public class SeedLimitsEnforcer extends DecideRule {

    private static final long serialVersionUID = 1l;

    private String seed;
    public void setSeed(String seed) {
        this.seed = seed;
    }
    public String getSeed() {
        return seed;
    }

    protected Long maxBytesDownload;
    public Long getMaxBytesDownload() {
        return maxBytesDownload;
    }
    public void setMaxBytesDownload(Long maxBytesDownload) {
        this.maxBytesDownload = maxBytesDownload;
    }

    protected Long maxDocumentsDownload;
    public Long getMaxDocumentsDownload() {
        return maxDocumentsDownload;
    }
    public void setMaxDocumentsDownload(Long maxDocumentsDownload) {
        this.maxDocumentsDownload = maxDocumentsDownload;
    }

    protected StatisticsTracker statisticsTracker;
    public StatisticsTracker getStatisticsTracker() {
        return this.statisticsTracker;
    }
    @Autowired
    public void setStatisticsTracker(StatisticsTracker statisticsTracker) {
        this.statisticsTracker = statisticsTracker;
    }

    @Override
    protected DecideResult innerDecide(CrawlURI curi) {
        if (!curi.getData().containsKey(CoreAttributeConstants.A_SOURCE_TAG)) {
            return DecideResult.NONE;
        }
        CrawledBytesHistotable stats = statisticsTracker.getSourceStats(curi.getSourceTag());
        if (stats == null) {
            return DecideResult.NONE;
        }

        if (maxBytesDownload != null
                && stats.get(CrawledBytesHistotable.NOVEL) >= maxBytesDownload) {
            return DecideResult.REJECT;
        }

        if (maxDocumentsDownload != null
                && stats.get(CrawledBytesHistotable.NOVELCOUNT) >= maxDocumentsDownload) {
            return DecideResult.REJECT;
        }

        return DecideResult.NONE;
    }

}
