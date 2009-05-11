/* ExternalGeoLocationDecideRule
 * 
 * Created on May 25, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
package org.archive.modules.deciderules;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.apache.commons.httpclient.URIException;
import org.archive.modules.ProcessorURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.xbill.DNS.Address;

/**
 * A rule that can be configured to take alternate implementations
 * of the ExternalGeoLocationInterface.
 * If no implementation specified, or none found, returns configured decision.
 * If host in URI has been resolved checks CrawlHost for the country code
 * determination.
 * If country code is not present, does country lookup, and saves the country
 * code to <code>CrawlHost</code> for future consultation.
 * If country code is present in <code>CrawlHost</code>, compares it against
 * the configured code.
 * Note that if a host's IP address changes during the crawl, we still consider
 * the associated hostname to be in the country of its original IP address.
 * 
 * @author Igor Ranitovic
 */
public class ExternalGeoLocationDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 3L;

    private static final Logger LOGGER =
        Logger.getLogger(ExternalGeoLocationDecideRule.class.getName());

    ExternalGeoLookupInterface lookup = null; 
    public ExternalGeoLookupInterface getLookup() {
        return this.lookup;
    }
    public void setLookup(ExternalGeoLookupInterface lookup) {
        this.lookup = lookup; 
    }
    
    /**
     * Country code name.
     */
    List<String> countryCodes = new ArrayList<String>();
    public List<String> getCountryCodes() {
        return this.countryCodes;
    }
    public void setCountryCodes(List<String> codes) {
        this.countryCodes = codes; 
    }

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    public ExternalGeoLocationDecideRule() {
    }
    
    @Override
    protected boolean evaluate(ProcessorURI uri) {        
        ExternalGeoLookupInterface impl = getLookup();
        if (impl == null) {
            return false;
        }
        CrawlHost crawlHost = null;
        String host;
        InetAddress address;
        try {
            host = uri.getUURI().getHost();
            crawlHost = serverCache.getHostFor(host);
            if (crawlHost.getCountryCode() != null) {
                return countryCodes.contains(crawlHost.getCountryCode());
            }
            address = crawlHost.getIP();
            if (address == null) {
                // TODO: handle transient lookup failures better
                address = Address.getByName(host);
            }
            crawlHost.setCountryCode((String) impl.lookup(address));
            if (countryCodes.contains(crawlHost.getCountryCode())) {
                LOGGER.fine("Country Code Lookup: " + " " + host
                        + crawlHost.getCountryCode());
                return true;
            }
        } catch (UnknownHostException e) {
            LOGGER.log(Level.FINE, "Failed dns lookup " + uri, e);
            if (crawlHost != null) {
                crawlHost.setCountryCode("--");
            }
        } catch (URIException e) {
            LOGGER.log(Level.FINE, "Failed to parse hostname " + uri, e);
        }

        return false;
    }
    
}