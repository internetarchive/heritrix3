/* Copyright (C) 2003 Internet Archive.
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
 * MemUURISet.java
 * Created on Sep 15, 2003
 *
 * $Header$
 */
package org.archive.crawler.util;

import java.util.HashSet;

/**
 * A purely in-memory UriUniqFilter based on a HashSet, which remembers
 * every full URI string it sees. 
 * 
 * @author gojomo
 *
 */
public class MemUriUniqFilter
extends SetBasedUriUniqFilter {
    private static final long serialVersionUID = 1L;
    HashSet<CharSequence> hashSet; 
    
    protected synchronized boolean setAdd(CharSequence uri) {
        return hashSet.add(uri);
    }
    protected synchronized boolean setRemove(CharSequence uri) {
        return hashSet.remove(uri);
    }
    protected synchronized long setCount() {
        return (long)hashSet.size();
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.util.UriUniqFilterImpl#createUriSet()
     */
    protected void createUriSet() {
        hashSet = new HashSet<CharSequence>();
    }

}
