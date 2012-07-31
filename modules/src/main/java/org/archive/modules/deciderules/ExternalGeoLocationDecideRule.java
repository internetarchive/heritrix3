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
package org.archive.modules.deciderules;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
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

    protected ExternalGeoLookupInterface lookup = null; 
    public ExternalGeoLookupInterface getLookup() {
        return this.lookup;
    }
    public void setLookup(ExternalGeoLookupInterface lookup) {
        this.lookup = lookup; 
    }
    
    /**
     * Country code name.
     */
    protected List<String> countryCodes = new ArrayList<String>();
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
    protected boolean evaluate(CrawlURI uri) {        
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