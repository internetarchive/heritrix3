package org.archive.modules.deciderules;

import static org.archive.modules.CoreAttributeConstants.A_DNS_SERVER_IP_LABEL;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;

import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * IpAddressSetDecideRule must be used with
 * {@link org.archive.crawler.prefetch.Preselector#setRecheckScope(boolean)} set
 * to true because it relies on Heritrix' dns lookup to establish the ip address
 * for a URI before it can run.
 * 
 * <pre>
 * &lt;bean class="org.archive.modules.deciderules.IpAddressSetDecideRule"&gt;
 *  &lt;property name="ipAddresses"&gt;
 *   &lt;set&gt;
 *    &lt;value&gt;127.0.0.1&lt;/value&gt;
 *    &lt;value&gt;69.89.27.209&lt;/value&gt;
 *   &lt;/set&gt;
 *  &lt;/property&gt;
 *  &lt;property name='decision' value='REJECT' /&gt;
 * &lt;/bean&gt;
 * </pre>
 * 
 * @contributor Travis Wellman <travis@archive.org>
 */

public class IpAddressSetDecideRule extends PredicatedDecideRule {

//    private static final Logger LOGGER = Logger.getLogger(IpAddressSetDecideRule.class.getCanonicalName());
    private static final long serialVersionUID = -3670434739183271441L;
    private Set<String> ipAddresses;
    
    /**
     * @return the addresses being matched
     */
    public Set<String> getIpAddresses() {
        return Collections.unmodifiableSet(ipAddresses);
    }

    /**
     * @param ipAddresses the addresses to match
     */
    public void setIpAddresses(Set<String> ipAddresses) {
        this.ipAddresses = ipAddresses;
    }

    @Override
    protected boolean evaluate(CrawlURI curi) {
        String hostAddress = getHostAddress(curi);
        return hostAddress != null &&
                ipAddresses.contains(hostAddress.intern());
    }

    transient protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    /**
     * from WriterPoolProcessor
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
        CrawlHost crlh = getServerCache().getHostFor(curi.getUURI());
        if (crlh == null) {
            return null;
        }
        InetAddress inetadd = crlh.getIP();
        if (inetadd == null) {
            return null;
        }
        return inetadd.getHostAddress();
    }
}
