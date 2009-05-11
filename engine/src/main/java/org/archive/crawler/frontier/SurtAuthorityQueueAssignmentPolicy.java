/* SurtAuthorityQueueAssignmentPolicy
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
 * SurtAuthorityQueueAssignmentPolicy based on the surt form of hostname.
 */
public class SurtAuthorityQueueAssignmentPolicy
extends QueueAssignmentPolicy {

    private static final long serialVersionUID = 3L;

    private static final Logger logger = Logger
            .getLogger(SurtAuthorityQueueAssignmentPolicy.class.getName());
    
    /**
     * When neat host-based class-key fails us
     */
    private static String DEFAULT_CLASS_KEY = "default...";
    
    private static final String DNS = "dns";

    public String getClassKey(CrawlURI cauri) {
        String scheme = cauri.getUURI().getScheme();
        String candidate = null;
        try {
            if (scheme.equals(DNS)) {
            	UURI effectiveuuri;
                if (cauri.getVia() != null) {
                    // Special handling for DNS: treat as being
                    // of the same class as the triggering URI.
                    // When a URI includes a port, this ensures 
                    // the DNS lookup goes atop the host:port
                    // queue that triggered it, rather than 
                    // some other host queue
                	effectiveuuri = UURIFactory.getInstance(cauri.flattenVia());
                } else {
                	// To get the dns surt form, create a fake http version
                	// (Gordon suggestion).
                    effectiveuuri = UURIFactory.getInstance("http://" +
                        cauri.getUURI().getPath());
                }
                candidate = getSurtAuthority(effectiveuuri.getSurtForm());
            } else {
                candidate = getSurtAuthority(cauri.getUURI().getSurtForm());
            }
            
            if(candidate == null || candidate.length() == 0) {
                candidate = DEFAULT_CLASS_KEY;
            }
        } catch (URIException e) {
            logger.log(Level.INFO,
                    "unable to extract class key; using default", e);
            candidate = DEFAULT_CLASS_KEY;
        }
        // Ensure classKeys are safe as filenames on NTFS
        return candidate.replace(':','#');
    }

    protected String getSurtAuthority(String surt) {
        int indexOfOpen = surt.indexOf("://(");
        int indexOfClose = surt.indexOf(")");
        if (indexOfOpen == -1 || indexOfClose == -1
                || ((indexOfOpen + 4) >= indexOfClose)) {
            return DEFAULT_CLASS_KEY;
        }
        return surt.substring(indexOfOpen + 4, indexOfClose);
    }
}
