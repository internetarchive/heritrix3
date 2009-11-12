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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.net.UURI;

public class ServerCacheUtil {

    private static Logger logger =
        Logger.getLogger(ServerCacheUtil.class.getName());
    
    private ServerCacheUtil() {
    }
    
    public static CrawlServer getServerFor(ServerCache cache, UURI uuri) {
        CrawlServer cs = null;
        try {
            String key = CrawlServer.getServerKey(uuri);
            // TODOSOMEDAY: make this robust against those rare cases
            // where authority is not a hostname.
            if (key != null) {
                cs = cache.getServerFor(key);
            }
        } catch (URIException e) {
            logger.log(
                Level.FINE, "No server key obtainable: "+uuri.toString(), e);
        } catch (NullPointerException npe) {
            logger.log(
                Level.FINE, "No server key obtainable: "+uuri.toString(), npe);
        }
        return cs;
    }


}
