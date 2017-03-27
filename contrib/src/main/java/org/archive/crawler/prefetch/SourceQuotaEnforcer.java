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

package org.archive.crawler.prefetch;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.archive.crawler.prefetch.QuotaEnforcer;
import org.archive.crawler.prefetch.RuntimeLimitEnforcer;
import org.archive.crawler.reporting.StatisticsTracker;
import org.archive.crawler.util.CrawledBytesHistotable;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.FetchChain;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.modules.seeds.SeedModule;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Processor for enforcing quotas by source tag (normally the seed url if
 * enabled). Should be configured early in {@link FetchChain} in
 * crawler-beans.cxml. Supports quotas on any of the fields tracked in
 * {@link CrawledBytesHistotable}. Only takes effect if
 * {@link SeedModule#getSourceTagSeeds()} (disabled by default) and
 * {@link StatisticsTracker#getTrackSources()} (enabled by default) are both
 * enabled.
 *
 * @contributor nlevitt
 * 
 * @see {@link QuotaEnforcer}
 * @see {@link RuntimeLimitEnforcer}
 */
public class SourceQuotaEnforcer extends Processor {

    protected String sourceTag;
    public void setSourceTag(String sourceTag) {
        this.sourceTag = sourceTag;
    }
    public String getSourceTag() {
        return sourceTag;
    }

    protected Map<String, Long> quotas = new HashMap<String, Long>();
    public Map<String, Long> getQuotas() {
        return quotas;
    }
    /**
     * Keys can be any of the {@link CrawledBytesHistotable} keys.
     */
    public void setQuotas(Map<String, Long> quotas) {
        this.quotas = quotas;
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
    protected boolean shouldProcess(CrawlURI curi) {
        return curi.containsDataKey(CoreAttributeConstants.A_SOURCE_TAG)
                && sourceTag.equals(curi.getSourceTag())
                && statisticsTracker.getSourceStats(curi.getSourceTag()) != null;
    }

    protected void innerProcess(CrawlURI curi) {
        throw new AssertionError();
    }

    protected ProcessResult innerProcessResult(CrawlURI curi) {
        if (!shouldProcess(curi)) {
            return ProcessResult.PROCEED;
        }

        CrawledBytesHistotable stats = statisticsTracker.getSourceStats(curi.getSourceTag());

        for (Entry<String, Long> quota: quotas.entrySet()) {
            if (stats.get(quota.getKey()) >= quota.getValue()) {
                curi.getAnnotations().add("sourceQuota:" + quota.getKey());
                curi.setFetchStatus(FetchStatusCodes.S_BLOCKED_BY_QUOTA);
                return ProcessResult.FINISH;
            }
        }

        return ProcessResult.PROCEED;
    }
}
