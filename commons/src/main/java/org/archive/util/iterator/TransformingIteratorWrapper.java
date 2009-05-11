/* TransformingIteratorWrapper
*
* $Id$
*
* Created on Mar 25, 2005
*
* Copyright (C) 2005 Internet Archive.
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
package org.archive.util.iterator;

import java.util.Iterator;

/**
 * Superclass for Iterators which transform and/or filter results
 * from a wrapped Iterator. Because transform() has the option of 
 * discarding an item from the inner Iterator (by returning null), 
 * this is a kind of LookaheadIterator. 
 * 
 * @author gojomo
 */
public abstract class TransformingIteratorWrapper<Original,Transformed> 
extends LookaheadIterator<Transformed> {
    protected Iterator<Original> inner;
    
    
    /* (non-Javadoc)
     * @see org.archive.util.iterator.LookaheadIterator#lookahead()
     */
    protected boolean lookahead() {
        assert next == null : "looking ahead when next is already loaded";
        while(inner.hasNext()) {
            next = transform(inner.next());
            if(next!=null) {
                return true;
            }
        }
        noteExhausted();
        return false;
    }

    /**
     * Any cleanup to occur when hasNext() is about to return false
     */
    protected void noteExhausted() {
        // by default, do nothing
        
    }

    /**
     * @param object Object to transform.
     * @return Transfomed object.
     */
    protected abstract Transformed transform(Original object);

}
