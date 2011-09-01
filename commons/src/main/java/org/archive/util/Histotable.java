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

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;


/**
 * Collect and report frequency information. 
 * 
 * Assumes external synchronization.
 * 
 * TODO: Histotable and TopNSet that they could possibly
 * have a closer relationship and share some code (even though Histotable 
 * tracks 'all' keys, and handles increments, while TopNSet only remembers
 * a small subset of keys, and requires a fresh full value on each update.)
 * 
 * @contributor gojomo
 */
public class Histotable<K> extends TreeMap<K,Long> {    
    private static final long serialVersionUID = 310306238032568623L;
    
    /**
     * Record one more occurence of the given object key.
     * 
     * @param key Object key.
     */
    public void tally(K key) {
        tally(key,1L);
    }
    
    /**
     * Record <i>count</i> more occurence(s) of the given object key.
     * 
     * @param key Object key.
     */
    public synchronized void tally(K key,long count) {
        long tally = containsKey(key) ? get(key) : 0; 
        tally += count; 
        if(tally!=0) {
            put(key,tally);
        } else {
            remove(key);
        }
    }
    
    /**
     * Get a SortedSet that, when filled with (String key)->(long count) 
     * Entry instances, sorts them by (count, key) descending, as is useful 
     * for most-frequent displays. 
     * 
     * Static to allow reuse elsewhere (TopNSet) until a better home for
     * this utility method is found. 
     * 
     * @return TreeSet with suitable Comparator
     */
    public static TreeSet<Map.Entry<?, Long>> getEntryByFrequencySortedSet() {
        // sorted by count
        TreeSet<Map.Entry<?,Long>> sorted = 
          new TreeSet<Map.Entry<?,Long>>(
           new Comparator<Map.Entry<?,Long>>() {
            public int compare(Map.Entry<?,Long> e1, 
                    Map.Entry<?,Long> e2) {
                long firstVal = e1.getValue();
                long secondVal = e2.getValue();
                if (firstVal < secondVal) { return 1; }
                if (secondVal < firstVal) { return -1; }
                // If the values are the same, sort by keys.
                String firstKey = ((Map.Entry<?,Long>) e1).getKey().toString();
                String secondKey = ((Map.Entry<?,Long>) e2).getKey().toString();
                return firstKey.compareTo(secondKey);
            }
        });
        return sorted;
    }
    
    /**
     * @return Return an up-to-date count-descending sorted version of the totaled info.
     */
    public TreeSet<Map.Entry<?,Long>> getSortedByCounts() {
        TreeSet<java.util.Map.Entry<?, Long>> sorted = getEntryByFrequencySortedSet();
        sorted.addAll(entrySet());
        return sorted; 
    }
    
    /**
     * @return Return an up-to-date sorted version of the totaled info.
     */
    public Set<Map.Entry<K,Long>> getSortedByKeys() {
        return entrySet();
    }
    
    /**
     * Return the largest value of any key that is larger than 0. If no 
     * values or no value larger than zero, return zero. 
     * 
     * @return long largest value or zero if none larger than zero
     */
    public long getLargestValue() {
        long largest = 0; 
        for (Long el : values()) {
            if (el > largest) {
                largest = el;
            }
        }
        return largest;
    }
    
    /**
     * Return the total of all tallies. 
     * 
     * @return long total of all tallies
     */
    public long getTotal() {
        long total = 0; 
        for (Long el : values()) {
            total += el; 
        }
        return total;
    }
    
    /**
     * Utility method to convert a key-&gt;Long into
     * the string "count key".
     * 
     * @param e Map key.
     * @return String 'count key'.
     */
    @SuppressWarnings("unchecked")
    public static String entryString(Object e) {
        Map.Entry<?,Long> entry = (Map.Entry<?,Long>) e;
        return entry.getValue() + " " + entry.getKey();
    }
    
    public long add(Histotable<K> ht) {
        long net = 0;
        for (K key : ht.keySet()) {
            long change = ht.get(key);
            net += change;
            tally(key,change);
        }
        return net;
    }
    public long subtract(Histotable<K> ht) {
        long net = 0; 
        for (K key : ht.keySet()) {
            long change = ht.get(key);
            net -= change;
            tally(key,-change);
        }
        return net;
    }

    /** Return 0 instead of null for absent keys. 
     * 
     * @see java.util.TreeMap#get(java.lang.Object)
     */
    @Override
    public Long get(Object key) {
        Long val = super.get(key);
        return val == null ? 0 : val;
    }
    
    
}
