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

import java.io.Serializable;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.archive.util.Histotable;

/**
 * Counting Set which only remembers the 'top N' of all String values 
 * reported (with counts) to it. Precise if counts reported for a 
 * certain String key only ever increase. Otherwise, the current top N 
 * might decrement to be smaller than other tallies, which won't be noticed
 * until those other, previously ignored tallies, are re-reported. 
 *
 * TODO: Histotable and TopNSet that they could possibly
 * have a closer relationship and share some code (even though Histotable 
 * tracks 'all' keys, and handles increments, while TopNSet only remembers
 * a small subset of keys, and requires a fresh full value on each update.)
 *
 * @contributor gojomo
 */
public class TopNSet implements Serializable {
    
    private static final long serialVersionUID = 1L;

    int maxsize;
    HashMap<String, Long> set;
    long smallestKnownValue;
    String smallestKnownKey;
    long largestKnownValue;
    String largestKnownKey; 
    
    public TopNSet(int size){
        maxsize = size;
        set = new HashMap<String, Long>(size);
    }
    
    /**
     * Update the given String key with a new total value, perhaps displacing
     * an existing top-valued entry, and updating the fields recording max/min
     * keys in any case. 
     * 
     * @param key String key to update
     * @param value long new total value (*not* increment/decrement)
     */
    public synchronized void update(String key, long value){
        if(set.containsKey(key) || set.size() < maxsize || value > smallestKnownValue) {
            set.put(key,value); 
            if(set.size() > maxsize) {
                set.remove(smallestKnownKey);
                updateBounds();
            } else if(value < smallestKnownValue 
                    || value > largestKnownValue 
                    || key.equals(smallestKnownKey)
                    || key.equals(largestKnownKey)) {
                updateBounds(); 
            }
        }
    }
    
    public String getLargest() {
        return largestKnownKey;
    }
    
    public String getSmallest() {
        return smallestKnownKey;
    }
    
    /**
     * After an operation invalidating the previous largest/smallest entry,
     * find the new largest/smallest. 
     */
    protected void updateBounds(){
        // freshly determine 
        smallestKnownValue = Long.MAX_VALUE;
        largestKnownValue = Long.MIN_VALUE;
        for(String k : set.keySet()){
            long v = set.get(k);
            if(v<smallestKnownValue){
                smallestKnownValue = v;
                smallestKnownKey = k;
            }
            if(v>largestKnownValue){
                largestKnownValue = v;
                largestKnownKey = k;
            }
        }
    }
    
    /**
     * Make internal map available (for checkpoint/restore purposes). 
     * @return HashMap<String,Long>
     */
    public HashMap<String,Long> getTopSet() {
        return set;
    }
    
    public int size() {
        return set.size();
    }
    
    /**
     * Get descending ordered list of key,count Entries.
     * 
     * @return SortedSet of Entry<key, count> descending-frequency 
     */
    public SortedSet<Entry<?, Long>> getEntriesDescending() {
        TreeSet<Entry<?, Long>> sorted = Histotable.getEntryByFrequencySortedSet();
        sorted.addAll(getTopSet().entrySet());
        return sorted; 
    }
    
    public int getMaxSize() {
        return maxsize;
    }
    public void setMaxSize(int max) {
        maxsize = max; 
    }
}
