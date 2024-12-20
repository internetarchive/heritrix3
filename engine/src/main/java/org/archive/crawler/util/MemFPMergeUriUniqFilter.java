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

import java.util.Arrays;
import java.util.Iterator;

/**
 * Crude all-in-memory FP-merging UriUniqFilter. 
 * 
 * @author gojomo
 */
public class MemFPMergeUriUniqFilter extends FPMergeUriUniqFilter {
    protected LongArrayList allFps = new LongArrayList(16);
    protected LongArrayList newFps;
    
    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#beginFpMerge()
     */
    protected Iterator<Long> beginFpMerge() {
        newFps = new LongArrayList((int) (allFps.size()+(pending()/2)));
        return allFps.iterator();
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#addNewFp(java.lang.Long)
     */
    protected void addNewFp(long currFp) {
        newFps.add(currFp);
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#finishFpMerge()
     */
    protected void finishFpMerge() {
        allFps = newFps;
        newFps = null; 
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#count()
     */
    public long count() {
        return allFps.size();
    }


    protected static class LongArrayList {
        private long[] array;
        private int size;

        public LongArrayList(int capacity) {
            this.array = new long[capacity];
        }

        public void add(long value) {
            if (size >= array.length) {
                array = Arrays.copyOf(array, array.length*2);
            }
            array[size++] = value;
        }

        public int size() {
            return size;
        }

        public Iterator<Long> iterator() {
            return new Iterator<>() {
                private int position = 0;

                @Override
                public boolean hasNext() {
                    return position < size;
                }

                @Override
                public Long next() {
                    return array[position++];
                }
            };
        }
    }

}
