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
    protected HashSet<CharSequence> hashSet; 
    
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
