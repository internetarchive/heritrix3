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

import org.apache.commons.httpclient.URIException;
import org.apache.http.client.CookieStore;
import org.archive.modules.CrawlURI;

public interface FetchHTTPCookieStore extends CookieStore {
    /**
     * Returns a {@link CookieStore} whose {@link CookieStore#getCookies()}
     * returns all the cookies from {@code host} and each of its
     * parent domains, if applicable.
     */
    public CookieStore cookieStoreFor(String host);

    /**
     * Returns a {@link CookieStore} whose {@link CookieStore#getCookies()}
     * returns all the cookies that could possibly apply {@code curi}.
     */
    public CookieStore cookieStoreFor(CrawlURI curi) throws URIException;
}
