/*
 * Histotable.java
 * 
 * Created on Aug 5, 2004
 *
 * $Id$
 *
 * Copyright (C) 2007 Internet Archive.
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
 * @author gojomo
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
    public void tally(K key,long count) {
        long tally = containsKey(key) ? get(key) : 0; 
        tally += count; 
        if(tally!=0) {
            put(key,tally);
        } else {
            remove(key);
        }
    }
    
    /**
     * @return Return an up-to-date sorted version of the totalled info.
     */
    public TreeSet<Map.Entry<K,Long>> getSortedByCounts() {
        // sorted by count
        TreeSet<Map.Entry<K,Long>> sorted = 
          new TreeSet<Map.Entry<K,Long>>(
           new Comparator<Map.Entry<K,Long>>() {
            public int compare(Map.Entry<K,Long> e1, 
                    Map.Entry<K,Long> e2) {
                long firstVal = e1.getValue();
                long secondVal = e2.getValue();
                if (firstVal < secondVal) { return 1; }
                if (secondVal < firstVal) { return -1; }
                // If the values are the same, sort by keys.
                String firstKey = ((Map.Entry<K,Long>) e1).getKey().toString();
                String secondKey = ((Map.Entry<K,Long>) e2).getKey().toString();
                return firstKey.compareTo(secondKey);
            }
        });
        
        sorted.addAll(entrySet());
        return sorted;
    }
    
    /**
     * @return Return an up-to-date sorted version of the totalled info.
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
