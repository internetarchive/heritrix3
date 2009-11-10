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
package org.archive.io;


/**
 * Provides a subsequence view onto a CharSequence.
 *
 * @author gojomo
 * @version $Revision$, $Date$
 */
public class CharSubSequence implements CharSequence {

    CharSequence inner;
    int start;
    int end;

    public CharSubSequence(CharSequence inner, int start, int end) {
        if (end < start) {
            throw new IllegalArgumentException("Start " + start + " is > " +
                " than end " + end);
        }

        if (end < 0 || start < 0) {
            throw new IllegalArgumentException("Start " + start + " or end " +
                end + " is < 0.");
        }

        if (inner ==  null) {
            throw new NullPointerException("Passed charsequence is null.");
        }

        this.inner = inner;
        this.start = start;
        this.end = end;
    }

    /*
     *  (non-Javadoc)
     * @see java.lang.CharSequence#length()
     */
    public int length() {
        return this.end - this.start;
    }

    /*
     *  (non-Javadoc)
     * @see java.lang.CharSequence#charAt(int)
     */
    public char charAt(int index) {
        return this.inner.charAt(this.start + index);
    }

    /*
     *  (non-Javadoc)
     * @see java.lang.CharSequence#subSequence(int, int)
     */
    public CharSequence subSequence(int begin, int finish) {
        return new CharSubSequence(this, begin, finish);
    }

    /*
     *  (non-Javadoc)
     * @see java.lang.CharSequence#toString()
     */
    public String toString() {
        StringBuffer sb = new StringBuffer(length());
        // could use StringBuffer.append(CharSequence) if willing to do 1.5 & up
        for (int i = 0;i<length();i++) {
            sb.append(charAt(i)); 
        }
        return sb.toString();
    }
}
