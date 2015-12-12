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
        return serverCache.getHostFor(curi.getUURI()) == serverCache.getHostFor(host);
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
                curi.getAnnotations().add("sourceQuota:" + k);
                curi.setFetchStatus(FetchStatusCodes.S_BLOCKED_BY_QUOTA);
                return ProcessResult.FINISH;
            }
        }

        return ProcessResult.PROCEED;
    }

}
