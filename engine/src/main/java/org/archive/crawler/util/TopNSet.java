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

/**
 * Counting Set which only remembers the 'top N' of all String values 
 * reported (with counts) to it. Assumes counts reported for a 
 * certain String key only ever increase. 
 * 
 * @contributor gojomo
 */
public class TopNSet implements Serializable {
    
    private static final long serialVersionUID = 1L;

    int maxsize;
    HashMap<String, Long> set;
    long smallestKnownValue;
    String smallestKnownKey;
    
    public TopNSet(int size){
        maxsize = size;
        set = new HashMap<String, Long>(size);
    }
    
    public void update(String key, long value){
        if(set.containsKey(key)) {
            // Update the value of an existing key
            set.put(key,value); 
            // This may promote the key if it was the smallest
            if(smallestKnownKey == null || smallestKnownKey.equals(key)){
                updateSmallest();
            }
        } else if(set.size()<maxsize) {
            // Can add a new key/value pair as we still have space
            set.put(key, value);
            // Check if this is new smallest known value
            if(value<smallestKnownValue){
                smallestKnownValue = value;
                smallestKnownKey = key;
            }
        } else {
            // Determine if value is large enough for inclusion
            if(value>smallestKnownValue){
                // Replace current smallest
                set.remove(smallestKnownKey);
                updateSmallest();
                set.put(key, value);
            } // Else do nothing.
        }
    }
    
    private void updateSmallest(){
        // Need to scan through for new smallest value.
        long oldSmallest = smallestKnownValue;
        smallestKnownValue = Long.MAX_VALUE;
        for(String k : set.keySet()){
            long v = set.get(k);
            if(v<smallestKnownValue){
                smallestKnownValue = v;
                smallestKnownKey = k;
                if(v==oldSmallest){
                    // Found another key matching old smallest known value
                    // Can not be anything smaller.
                    return;
                }
            }
        }
    }
    
    public String[] keySet(){
        return set.keySet().toArray(new String[0]);

    }
}
