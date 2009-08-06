/* UriUniqFilter
 * 
 * Created on Apr 17, 2003
 * 
 * Copyright (C) 2003 Internet Archive.
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
package org.archive.crawler.datamodel;

import java.io.File;

import org.archive.modules.CrawlURI;

/**
 * A UriUniqFilter passes URI objects to a destination
 * (receiver) if the passed URI object has not been previously seen.
 * 
 * If already seen, the passed URI object is dropped.
 *
 * <p>For efficiency in comparison against a large history of
 * seen URIs, URI objects may not be passed immediately, unless 
 * the addNow() is used or a flush() is forced.
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public interface UriUniqFilter {
    /**
     * @return Count of already seen URIs.
     */
    public long count();
    
    /**
     * Count of items added, but not yet filtered in or out. 
     * 
     * Some implementations may buffer up large numbers of pending
     * items to be evaluated in a later large batch/scan/merge with 
     * disk files. 
     * 
     * @return Count of items added not yet evaluated 
     */
    public long pending();

    /**
     * Receiver of uniq URIs.
     * 
     * Items that have not been seen before are pass through to this object.
     * @param receiver Object that will be passed items. Must implement
     * HasUriReceiver interface.
     */
    public void setDestination(CrawlUriReceiver receiver);
    
    /**
     * Add given uri, if not already present.
     * @param key Usually a canonicalized version of <code>value</code>.
     * This is the key used doing lookups, forgets and insertions on the
     * already included list.
     * @param value item to add.
     */
    public void add(String key, CrawlURI value);
    
    /**
     * Immediately add uri.
     * @param key Usually a canonicalized version of <code>uri</code>.
     * This is the key used doing lookups, forgets and insertions on the
     * already included list.
     * @param value item to add.
     */
    public void addNow(String key, CrawlURI value);
    
    /**
     * Add given uri, all the way through to underlying destination, even 
     * if already present.
     * 
     * (Sometimes a URI must be fetched, or refetched, for example when
     * DNS or robots info expires or the operator forces a refetch. A
     * normal add() or addNow() would drop the URI without forwarding
     * on once it is determmined to already be in the filter.) 
     * 
     * @param key Usually a canonicalized version of <code>uri</code>.
     * This is the key used doing lookups, forgets and insertions on the
     * already included list.
     * @param value item to add.
     */
    public void addForce(String key, CrawlURI value);
    
    /**
     * Note item as seen, without passing through to receiver.
     * @param key Usually a canonicalized version of an <code>URI</code>.
     * This is the key used doing lookups, forgets and insertions on the
     * already included list.
     */
    public void note(String key);
    
    /**
     * Forget item was seen
     * @param key Usually a canonicalized version of an <code>URI</code>.
     * This is the key used doing lookups, forgets and insertions on the
     * already included list.
     * @param value item to add.
     */
    public void forget(String key, CrawlURI value);
    
    /**
     * Request that any pending items be added/dropped. Implementors
     * may ignore the request if a flush would be too expensive/too 
     * soon. 
     * 
     * @return Number added.
     */
    public long requestFlush();
    
    /**
     * Close down any allocated resources.
     * Makes sense calling this when checkpointing.
     */
    public void close();
    
    /**
     * Set a File to receive a log for replay profiling. 
     */
    public void setProfileLog(File logfile);
    
    /**
     * URIs that pass the filter (are new / unique / not already-seen) 
     * are passed to this object, typically a frontier. 
     * 
     */
    public interface CrawlUriReceiver {
        /**
         * @param item CrawlURI that passed uniqueness testing
         */
        public void receive(CrawlURI item);
    }
}