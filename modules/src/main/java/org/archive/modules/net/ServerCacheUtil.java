/* Copyright (C) 2006 Internet Archive.
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
 * ServerCacheUtil.java
 * Created on December 13, 2006
 *
 * $Id$
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
    
    
    public static CrawlHost getHostFor(ServerCache cache, UURI uuri) {
        try {
            return cache.getHostFor(uuri.getReferencedHost());
        } catch (URIException e) {
            logger.log(Level.SEVERE, uuri.toString(), e);
            return null;
        }
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
