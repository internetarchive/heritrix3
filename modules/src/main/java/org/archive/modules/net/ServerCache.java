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

package org.archive.modules.net;

import org.apache.commons.collections.Closure;
import org.archive.net.UURI;

/**
 * Interface for crawl-global registry of CrawlServer (host:port) 
 * and CrawlHost (hostname) objects.
 * 
 * TODO?: Turn this into an abstract superclass which subsumes the 
 * utility methods of ServerCacheUtil.
 */
public interface ServerCache {

    CrawlHost getHostFor(String host);
    
    CrawlHost getHostFor(UURI uuri);
    
    CrawlServer getServerFor(String serverKey);


    /**
     * Utility for performing an action on every CrawlHost. 
     * 
     * @param action 1-argument Closure to apply to each CrawlHost
     */
    void forAllHostsDo(Closure action);
}
