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

import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.SortedSet;

import org.apache.commons.lang.StringUtils;

import com.sleepycat.collections.StoredSortedKeySet;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.collections.StoredSortedValueSet;


/**
 * Utility class for extracting prefixes of a given string from a SortedMap.
 * 
 * @author pjack
 */
public class PrefixFinder {


    /**
     * Extracts prefixes of a given string from a SortedSet.  If an element
     * of the given set is a prefix of the given string, then that element
     * is added to the result list.
     * 
     * <p>Put another way, for every element in the result list, the following 
     * expression will be true: <tt>string.startsWith(element.getKey())</tt>.
     * 
     * @param set     the sorted set containing potential prefixes
     * @param input   the string whose prefixes to find 
     * @return   the list of prefixes 
     */
    public static List<String> find(SortedSet<String> set, String input) {
        LinkedList<String> result = new LinkedList<String>();
        set = headSetInclusive(set, input);
        int opCount = 0;
        for (String last = last(set); last != null; last = last(set)) {
            opCount++;
            if (input.startsWith(last)) {
                result.push(last);
                set = set.headSet(last); 
            } else {
                // Find the longest common prefix.
                int p = StringUtils.indexOfDifference(input, last);
                if (p <= 0) {
                    return result;
                }
                last = input.substring(0, p);
                set = headSetInclusive(set, last);
            }
        }
        return result;
    }

    
    @SuppressWarnings("unchecked")
    protected static SortedSet<String> headSetInclusive(SortedSet<String> set, String input) {
        // use NavigableSet inclusive version if available
        if(set instanceof NavigableSet) {
            return ((NavigableSet)set).headSet(input, true);
        }
        // use Stored*Set inclusive version if available
        if(set instanceof StoredSortedKeySet) {
            return ((StoredSortedKeySet)set).headSet(input, true);
        }
        if(set instanceof StoredSortedValueSet) {
            return ((StoredSortedValueSet)set).headSet(input, true);
        }
        // Use synthetic "one above" trick
        // NOTE: because '\0' sorts in the middle in "java modified UTF-8",
        // used in the Stored* class StringBindings, this trick won't work
        // there
        return set.headSet(input+'\0');
    }


    private static String last(SortedSet<String> set) {
        return set.isEmpty() ? null : set.last();
    }


    public static List<String> findKeys(SortedMap<String,?> map, String input) {
        LinkedList<String> result = new LinkedList<String>();
        map = headMapInclusive(map, input);
        int opCount = 0;
        for (String last = last(map); last != null; last = last(map)) {
            opCount++;
            if (input.startsWith(last)) {
                result.push(last);
                map = map.headMap(last); 
            } else {
                // Find the longest common prefix.
                int p = StringUtils.indexOfDifference(input, last);
                if (p <= 0) {
                    return result;
                }
                last = input.substring(0, p);
                map = headMapInclusive(map, last);
            }
        }
        return result;
    }


    @SuppressWarnings("unchecked")
    private static SortedMap<String, ?> headMapInclusive(SortedMap<String, ?> map, String input) {
        // use NavigableMap inclusive version if available
        if(map instanceof NavigableMap) {
            return ((NavigableMap)map).headMap(input, true);
        }
        // use StoredSortedMap inclusive version if available
        if(map instanceof StoredSortedMap) {
            return ((StoredSortedMap)map).headMap(input, true);
        }
        // Use synthetic "one above" trick
        // NOTE: because '\0' sorts in the middle in "java modified UTF-8",
        // used in the Stored* class StringBindings, this trick won't work
        // there
        return map.headMap(input+'\0');
    }


    private static String last(SortedMap<String,?> map) {
        // TODO Auto-generated method stub
        return map.isEmpty() ? null : map.lastKey();
    }
}
