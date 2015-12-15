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
package org.archive.crawler.prefetch;

import java.util.HashMap;
import java.util.Map;

import org.archive.modules.CrawlURI;
import org.archive.modules.FetchChain;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.fetcher.FetchStats;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.net.InternetDomainName;

/**
 * Enforces quotas on a host. Should be configured early in {@link FetchChain} in
 * crawler-beans.cxml. Supports quotas on any of the fields tracked in
 * {@link FetchStats}.
 * 
 * @see QuotaEnforcer
 * @see SourceQuotaEnforcer
 * @contributor nlevitt
 */
public class HostQuotaEnforcer extends Processor {

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    protected String host;
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }

    protected boolean applyToSubdomains = false;
    public boolean getApplyToSubdomains() {
        return applyToSubdomains;
    }
    /**
     * Whether to apply the quotas to each subdomain of {@link #host}
     * (separately, not cumulatively).
     */
    public void setApplyToSubdomains(boolean applyToSubdomains) {
        this.applyToSubdomains = applyToSubdomains;
    }

    protected Map<String,Long> quotas = new HashMap<String, Long>();
    public Map<String, Long> getQuotas() {
        return quotas;
    }
    /**
     * Keys can be any of the {@link FetchStats} keys.
     */
    public void setQuotas(Map<String, Long> quotas) {
        this.quotas = quotas;
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        String uriHostname = serverCache.getHostFor(curi.getUURI()).getHostName();
        if (getApplyToSubdomains() && InternetDomainName.isValid(host) && InternetDomainName.isValid(uriHostname)) {
            InternetDomainName h = InternetDomainName.from(host);
            InternetDomainName uriHostOrAncestor = InternetDomainName.from(uriHostname);
            while (true) {
                if (uriHostOrAncestor.equals(h)) {
                    return true;
                }
                if (uriHostOrAncestor.hasParent()) {
                    uriHostOrAncestor = uriHostOrAncestor.parent();
                } else {
                    break;
                }
            }

            return false;
        } else {
            return serverCache.getHostFor(curi.getUURI()) == serverCache.getHostFor(host);
        }

    }

    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        throw new AssertionError();
    }

    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) throws InterruptedException {
        if (!shouldProcess(curi)) {
            return ProcessResult.PROCEED;
        }

        final CrawlHost host = serverCache.getHostFor(curi.getUURI());

        for (String k: quotas.keySet()) {
            if (host.getSubstats().get(k) >= quotas.get(k)) {
                curi.getAnnotations().add("hostQuota:" + k);
                curi.setFetchStatus(FetchStatusCodes.S_BLOCKED_BY_QUOTA);
                return ProcessResult.FINISH;
            }
        }

        return ProcessResult.PROCEED;
    }

}
