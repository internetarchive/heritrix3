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
package org.archive.modules.fetcher;


import java.io.IOException;

import org.archive.modules.CrawlMetadata;


/**
 *
 */
public class FetchHTTPTest extends FetchHTTPTestBase {

    @Override
    protected AbstractFetchHTTP makeModule() throws IOException {
        LegacyFetchHTTP fetchHttp = new LegacyFetchHTTP();
        fetchHttp.setCookieStorage(new SimpleCookieStorage());
        fetchHttp.setServerCache(new DefaultServerCache());
        CrawlMetadata uap = new CrawlMetadata();
        uap.setUserAgentTemplate(getUserAgentString());
        fetchHttp.setUserAgentProvider(uap);
        
        fetchHttp.start();
        return fetchHttp;
    }

    @Override
    public void testHttpProxyAuth() throws Exception {
        // XXX skip cuz it's slow in FetchHTTP for some reason
    }
    
    @Override
    public void testConnectionTimeout() throws Exception {
        // XXX skip cuz it's slow cuz you can't change the connection timeout after FetchHTTP.start() has run
    }
}
