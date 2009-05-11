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


/**
 * A UriUniqFilter that doesn't actually provide any uniqueness
 * filter on presented items: all are passed through. 
 * 
 * @author gojomo
 *
 */
public class NoopUriUniqFilter
extends SetBasedUriUniqFilter {
    private static final long serialVersionUID = 1L;

    protected synchronized boolean setAdd(CharSequence uri) {
        return true; // always consider as new
    }
    protected synchronized boolean setRemove(CharSequence uri) {
        return true; // always consider as succeeded
    }
    protected synchronized long setCount() {
        return -1; // return nonsense value
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.util.UriUniqFilterImpl#createUriSet()
     */
    protected void createUriSet() {
        // do nothing
    }

}
