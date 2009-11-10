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


/**
 * Transforms objects from one thing into another.
 * 
 * @author pjack
 *
 * @param <Original>  the type of the original objects
 * @param <Transformed>  the type of the transformed objects
 */
public interface Transformer<Original,Transformed> {

    /**
     * Transforms the given object.
     * 
     * @param o  the object to transform
     * @return  the transformed object
     */
    Transformed transform(Original o);
    
}
