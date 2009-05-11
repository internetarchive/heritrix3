/* Copyright (C) 2003 Internet Archive.
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
 *
 * SimpleHTTPExtractor.java
 * Created on Jul 3, 2003
 *
 * $Header$
 */
package org.archive.modules.extractor;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.ProcessorURI;
import org.archive.modules.ProcessorURI.FetchType;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;


/**
 * Extracts URIs from HTTP response headers.
 * @author gojomo
 */
public class ExtractorHTTP extends Extractor {

    private static final long serialVersionUID = 3L;

//    protected long numberOfCURIsHandled = 0;
    protected long numberOfLinksExtracted = 0;

    public ExtractorHTTP() {
    }

    
    
    @Override
    protected boolean shouldProcess(ProcessorURI uri) {
        if (uri.getFetchStatus() <= 0) {
            return false;
        }
        FetchType ft = uri.getFetchType();
        return (ft == FetchType.HTTP_GET) || (ft == FetchType.HTTP_POST);
    }
    
    
    @Override
    protected void extract(ProcessorURI curi) {
        HttpMethod method = curi.getHttpMethod();
        addHeaderLink(curi, method.getResponseHeader("Location"));
        addHeaderLink(curi, method.getResponseHeader("Content-Location"));
    }

    protected void addHeaderLink(ProcessorURI curi, Header loc) {
        if (loc == null) {
            // If null, return without adding anything.
            return;
        }
        // TODO: consider possibility of multiple headers
        try {
            UURI dest = UURIFactory.getInstance(curi.getUURI(), loc.getValue());
            LinkContext lc = new HTMLLinkContext(loc.getName()); // FIXME
            Link link = new Link(curi.getUURI(), dest, lc, Hop.REFER);
            curi.getOutLinks().add(link);
            numberOfLinksExtracted++;
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), loc.getValue());
        }

    }

    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: org.archive.crawler.extractor.ExtractorHTTP\n");
        ret.append("  Function:          " +
            "Extracts URIs from HTTP response headers\n");
        ret.append("  CrawlURIs handled: " + this.getURICount());
        ret.append("  Links extracted:   " + numberOfLinksExtracted + "\n\n");
        return ret.toString();
    }
}
