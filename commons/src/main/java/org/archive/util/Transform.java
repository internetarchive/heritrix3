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
