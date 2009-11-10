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
