/* CrawlHostCache.java
 *
 * $Id$
 *
 * Created on Nov 21, 2006
 *
 * Copyright (C) 2006 Internet Archive.
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
package org.archive.modules.net;

import org.apache.commons.collections.Closure;

/**
 * Interface for crawl-global registry of CrawlServer (host:port) 
 * and CrawlHost (hostname) objects.
 * 
 * TODO?: Turn this into an abstract superclass which subsumes the 
 * utility methods of ServerCacheUtil.
 */
public interface ServerCache {

    CrawlHost getHostFor(String host);
    
    CrawlServer getServerFor(String serverKey);


    /**
     * Utility for performing an action on every CrawlHost. 
     * 
     * @param action 1-argument Closure to apply to each CrawlHost
     */
    void forAllHostsDo(Closure action);
}
