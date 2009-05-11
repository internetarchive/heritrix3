/* Copyright (C) 2003 Internet Archive.
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
 *
 * Created on Jul 21, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package org.archive.crawler.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class Sorts {

    // Sorts by value not key
    public static StringIntPair[] sortStringIntHashMap (HashMap<String,Integer> hm){
        String[] keys = hm.keySet().toArray(new String[hm.size()]);
        Integer[] values = hm.values().toArray(new Integer[hm.size()]);

        ArrayList<StringIntPair> unsortedList = new ArrayList<StringIntPair>();

        for (int i = 0; i < keys.length; i++)
            unsortedList.add(i, new StringIntPair(keys[i], values[i]));

        StringIntPair[] sortedArray 
         = unsortedList.toArray(new StringIntPair[unsortedList.size()]);
        Arrays.sort(sortedArray, new StringIntPairComparator());

        return sortedArray;
    }

}
