/* Transform
*
* $Id$
*
* Created on September 26, 2006
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
package org.archive.util;

import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;


/**
 * A transformation of a collection.  The elements in the transform are based
 * on the elements of some other collection; the original collection's 
 * elements are transformed using a specified transformer.  Changes to the
 * original collection are automatically reflected in the transform and 
 * vice-versa.
 * 
 * <p>If the transformer returns null for a given original object, then that
 * object will not be included in the transform.  Thus the transform might
 * be smaller than the original collection.  Note that Transform instances
 * can never contain the null element.
 * 
 * <p>This collection implementation does not support the optional add
 * operation.
 * 
 * @author pjack
 *
 * @param <Original>  the type of the original elements in the collection
 * @param <Transformed>  the type of the tranformed elements
 */
public class Transform<Original,Transformed> 
extends AbstractCollection<Transformed> {

    /** The original collection. */
    final private Collection<? extends Original> delegate;
    
    /** Transforms the original objects. */
    final private Transformer<Original,Transformed> transformer;
    
    /**
     * Constructor.
     * 
     * @param delegate  The collection whose elements to transform.
     * @param transformer  Transforms the elements
     */
    public Transform(Collection<? extends Original> delegate, 
            Transformer<Original,Transformed> transformer) {
        this.delegate = delegate;
        this.transformer = transformer;
    }
    
    public int size() {
        int count = 0;
        Iterator<Transformed> iter = iterator();
        while (iter.hasNext()) {
            iter.next();
            count++;
        }
        return count;
    }

    public Iterator<Transformed> iterator() {
        return new TransformIterator<Original,Transformed>(
                delegate.iterator(), transformer);
    }
    
    
    /**
     * Returns a transform containing only objects of a given class.
     * 
     * @param <Target>  the target class
     * @param c    the collection to transform
     * @param cls  the class of objects to return 
     * @return  a collection containing only objects of class cls
     */
    public static <Target> Collection<Target> subclasses(
            Collection<? extends Object> c, 
            final Class<Target> cls) {
        Transformer<Object,Target> t = new Transformer<Object,Target>() {
            public Target transform(Object s) {
                if (cls.isInstance(s)) {
                    return cls.cast(s);
                } else {
                    return null;
                }
            }
        };
        return new Transform<Object,Target>(c, t);
    }
}


class TransformIterator<Original,Transformed> implements Iterator<Transformed> {

    final private Iterator<? extends Original> iterator;
    final private Transformer<Original,Transformed> transformer;
    private Transformed next;
    
    public TransformIterator(Iterator<? extends Original> iterator, 
            Transformer<Original,Transformed> transformer) {
        this.iterator = iterator;
        this.transformer = transformer;
    }
    
    public boolean hasNext() {
        if (next != null) {
            return true;
        }
        while (iterator.hasNext()) {
            Original o = iterator.next();
            next = transformer.transform(o);
            if (next != null) {                
                return true;
            }
        }
        return false;
    }

    public Transformed next() {
        if (!hasNext()) {
            throw new IllegalStateException();
        }
        Transformed r = next;
        next = null;
        return r;
    }

    // FIXME: this can break standard Iterator contract, for example
    // transformIterator.next();
    // if(transformIterator.hasNext()) {
    //   transformIterator.remove();
    // }
    // usual iterator contract is to remove the last object returned
    // by next; in this case the subsequent
    public void remove() {
        iterator.remove();
    }
    
}
