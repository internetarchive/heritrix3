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

import java.util.SortedSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * Utility class for maintaining sorted set of string prefixes.
 * Redundant prefixes are coalesced into the shorter prefix. 
 */
public class PrefixSet extends ConcurrentSkipListSet<String> {
    private static final long serialVersionUID = -6054697706348411992L;
    
    public PrefixSet() {
        super();
    }

    /**
     * Test whether the given String is prefixed by one
     * of this set's entries. 
     * 
     * @param s
     * @return True if contains prefix.
     */
    public boolean containsPrefixOf(String s) {
        SortedSet<String> sub = headSet(s);
        // because redundant prefixes have been eliminated,
        // only a test against last item in headSet is necessary
        if (!sub.isEmpty() && s.startsWith((String)sub.last())) {
            return true; // prefix substring exists
        } // else: might still exist exactly (headSet does not contain boundary)
        return contains(s); // exact string exists, or no prefix is there
    }
    
    /** 
     * Maintains additional invariant: if one entry is a 
     * prefix of another, keep only the prefix. 
     * 
     * @see java.util.Collection#add(java.lang.Object)
     */
    public boolean add(String s) {
        SortedSet<String> sub = headSet(s);
        if (!sub.isEmpty() && s.startsWith((String)sub.last())) {
            // no need to add; prefix is already present
            return false;
        }
        boolean retVal = super.add(s);
        sub = tailSet(s+"\0");
        while(!sub.isEmpty() && ((String)sub.first()).startsWith(s)) {
            // remove redundant entries
            sub.remove(sub.first());
        }
        return retVal;
    }
    
}