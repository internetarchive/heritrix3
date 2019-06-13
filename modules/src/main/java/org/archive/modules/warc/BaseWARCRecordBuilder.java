package org.archive.modules.warc;

import static org.archive.modules.CoreAttributeConstants.A_DNS_SERVER_IP_LABEL;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.UUID;

import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseWARCRecordBuilder implements WARCRecordBuilder {

    transient protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    public static URI generateRecordID() {
        try {
            return new URI("urn:uuid:" + UUID.randomUUID());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e); // impossible 
        }
    }
    
    /**
     * Return IP address of given URI suitable for recording (as in a
     * classic ARC 5-field header line).
     * 
     * @param curi CrawlURI
     * @return String of IP address
     */
    protected String getHostAddress(CrawlURI curi) {
        // special handling for DNS URIs: want address of DNS server
        if (curi.getUURI().getScheme().toLowerCase().equals("dns")) {
            return (String)curi.getData().get(A_DNS_SERVER_IP_LABEL);
        }
        // otherwise, host referenced in URI
        // TODO:FIXME: have fetcher insert exact IP contacted into curi,
        // use that rather than inferred by CrawlHost lookup 
        CrawlHost h = getServerCache().getHostFor(curi.getUURI());
        if (h == null) {
            throw new NullPointerException("Crawlhost is null for " +
                curi + " " + curi.getVia());
        }
        InetAddress a = h.getIP();
        if (a == null) {
            throw new NullPointerException("Address is null for " +
                curi + " " + curi.getVia() + ". Address " +
                ((h.getIpFetched() == CrawlHost.IP_NEVER_LOOKED_UP)?
                     "was never looked up.":
                     (System.currentTimeMillis() - h.getIpFetched()) +
                         " ms ago."));
        }
        return h.getIP().getHostAddress();
    }

}
