/* CharSubSequence.java
 *
 * Created on Sep 30, 2003
 *
 * Copyright (C) 2003 Internet Archive.
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
