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

package org.archive.modules.extractor;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;


/**
 * Extracts URIs from HTTP response headers.
 * @author gojomo
 */
public class ExtractorHTTP extends Extractor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    public ExtractorHTTP() {
    }

    /** should all HTTP URIs be used to infer a link to the site's root? */
    protected boolean inferRootPage = false; 
    public boolean getInferRootPage() {
        return inferRootPage;
    }
    public void setInferRootPage(boolean inferRootPage) {
        this.inferRootPage = inferRootPage;
    }


    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        if (uri.getFetchStatus() <= 0) {
            return false;
        }
        FetchType ft = uri.getFetchType();
        return (ft == FetchType.HTTP_GET) || (ft == FetchType.HTTP_POST);
    }


    @Override
    protected void extract(CrawlURI curi) {
        // discover headers if present
        addHeaderLink(curi, "Location");
        addContentLocationHeaderLink(curi, "Content-Location");
        addRefreshHeaderLink(curi, "Refresh");

        // try /favicon.ico for every HTTP(S) URI
        addOutlink(curi, "/favicon.ico", LinkContext.INFERRED_MISC, Hop.INFERRED);
        if(getInferRootPage()) {
            addOutlink(curi, "/", LinkContext.INFERRED_MISC, Hop.INFERRED);
        }
    }

    protected void addRefreshHeaderLink(CrawlURI curi, String headerKey) {
        String headerValue = curi.getHttpResponseHeader(headerKey);
        if (headerValue != null) {
            // parsing logic copied from ExtractorHTML meta-refresh handling
            int urlIndex = headerValue.indexOf("=") + 1;
            if (urlIndex > 0) {
                String refreshUri = headerValue.substring(urlIndex);
                addHeaderLink(curi, headerKey, refreshUri);
            }
        }
    }

    protected void addHeaderLink(CrawlURI curi, String headerKey) {
        String headerValue = curi.getHttpResponseHeader(headerKey);
        if (headerValue != null) {
            addHeaderLink(curi, headerKey, headerValue);
        }
    }

    protected void addContentLocationHeaderLink(CrawlURI curi, String headerKey) {
        String headerValue = curi.getHttpResponseHeader(headerKey);
        if (headerValue != null) {
            try {
                UURI dest = UURIFactory.getInstance(curi.getUURI(), headerValue);
                addOutlink(curi, dest.toString(), LinkContext.INFERRED_MISC, Hop.INFERRED);
                numberOfLinksExtracted.incrementAndGet();
            } catch (URIException e) {
                logUriError(e, curi.getUURI(), headerValue);
            }
        }
    }
    
    protected void addHeaderLink(CrawlURI curi, String headerName, String url) {
        try {
            UURI dest = UURIFactory.getInstance(curi.getUURI(), url);
            LinkContext lc = HTMLLinkContext.get(headerName+":");
            addOutlink(curi, dest.toString(), lc, Hop.REFER);
            numberOfLinksExtracted.incrementAndGet();
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), url);
        }
    }
}
