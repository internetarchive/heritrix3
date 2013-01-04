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
package org.archive.net;

import org.apache.commons.httpclient.URIException;
import org.archive.url.UsableURI;
import org.archive.url.UsableURIFactory;

/**
 * Factory that returns UURIs. Mostly wraps {@link UsableURIFactory}.
 * 
 */
public class UURIFactory extends UsableURIFactory {
        
    private static final long serialVersionUID = -7969477276065915936L;
    
    /**
     * The single instance of this factory.
     */
    private static final UURIFactory factory = new UURIFactory();

    /**
     * @param uri URI as string.
     * @return An instance of UURI
     * @throws URIException
     */
    public static UURI getInstance(String uri) throws URIException {
        return (UURI) UURIFactory.factory.create(uri);
    }

    /**
     * @param base Base uri to use resolving passed relative uri.
     * @param relative URI as string.
     * @return An instance of UURI
     * @throws URIException
     */
    public static UURI getInstance(UURI base, String relative)
            throws URIException {
        return (UURI) UURIFactory.factory.create(base, relative);
    }

    @Override
    protected UURI makeOne(String fixedUpUri, boolean escaped, String charset)
            throws URIException {
        return new UURI(fixedUpUri, escaped, charset);
    }
    
    @Override
    protected UsableURI makeOne(UsableURI base, UsableURI relative) throws URIException {
        // return new UURI(base, relative);
        return new UURI(base, relative);
    }

}
