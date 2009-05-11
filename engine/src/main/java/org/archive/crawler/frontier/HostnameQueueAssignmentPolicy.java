/* HostnameQueueAssignmentPolicy
*
* $Id$
*
* Created on Oct 5, 2004
*
* Copyright (C) 2004 Internet Archive.
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
package org.archive.crawler.frontier;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.datamodel.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

/**
 * QueueAssignmentPolicy based on the hostname:port evident in the given
 * CrawlURI.
 * 
 * @author gojomo
 */
public class HostnameQueueAssignmentPolicy extends QueueAssignmentPolicy {

    private static final long serialVersionUID = 3L;

    private static final Logger logger = Logger
        .getLogger(HostnameQueueAssignmentPolicy.class.getName());
    /**
     * When neat host-based class-key fails us
     */
    private static String DEFAULT_CLASS_KEY = "default...";
    
    private static final String DNS = "dns";

    public String getClassKey(CrawlURI cauri) {
        String scheme = cauri.getUURI().getScheme();
        String candidate = null;
        try {
            if (scheme.equals(DNS)){
                if (cauri.getVia() != null) {
                    // Special handling for DNS: treat as being
                    // of the same class as the triggering URI.
                    // When a URI includes a port, this ensures 
                    // the DNS lookup goes atop the host:port
                    // queue that triggered it, rather than 
                    // some other host queue
                	UURI viaUuri = UURIFactory.getInstance(cauri.flattenVia());
                    candidate = viaUuri.getAuthorityMinusUserinfo();
                    // adopt scheme of triggering URI
                    scheme = viaUuri.getScheme();
                } else {
                    candidate= cauri.getUURI().getReferencedHost();
                }
            } else {
                candidate =  cauri.getUURI().getAuthorityMinusUserinfo();
            }
            
            if(candidate == null || candidate.length() == 0) {
                candidate = DEFAULT_CLASS_KEY;
            }
        } catch (URIException e) {
            logger.log(Level.INFO,
                    "unable to extract class key; using default", e);
            candidate = DEFAULT_CLASS_KEY;
        }
        if (scheme != null && scheme.equals(UURIFactory.HTTPS)) {
            // If https and no port specified, add default https port to
            // distinguish https from http server without a port.
            if (!candidate.matches(".+:[0-9]+")) {
                candidate += UURIFactory.HTTPS_PORT;
            }
        }
        // Ensure classKeys are safe as filenames on NTFS
        return candidate.replace(':','#');
    }

}
