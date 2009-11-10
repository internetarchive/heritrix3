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
