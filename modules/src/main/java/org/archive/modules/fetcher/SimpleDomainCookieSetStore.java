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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

public class SimpleDomainCookieSetStore extends AbstractDomainCookieSetStore {

    @SuppressWarnings("rawtypes")
    protected Map<String, TreeSet> cookiesByHost;

    @SuppressWarnings("rawtypes")
    protected void prepare() {
        cookiesByHost = new LinkedHashMap<String, TreeSet>();
    }

    @SuppressWarnings("rawtypes")
    @Override
    protected Map<String, TreeSet> getCookiesByHost() {
        return cookiesByHost;
    }
}
