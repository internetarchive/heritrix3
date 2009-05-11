/* PreJ15Utils
*
* $Id$
*
* Created on May 20, 2005
*
* Copyright (C) 2005 Internet Archive.
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

/**
 * A collection of utility methods doing things that are easier in Java 1.5. 
 * 
 * @author gojomo
 * @deprecated Will be removed post 1.10.0 Heritrix.
 */
public class PreJ15Utils {

    /**
     * Version of 1.5's StringBuffer.append(CharSequence s, int start, int finish)
     * @param buffer StringBuffer to append to
     * @param cs CharSequence with material to append
     * @param start position from which to begin appending
     * @param end position at which to stop appending (exclusive)
     */
    public static StringBuffer append(StringBuffer buffer, CharSequence cs, int start, int end) {
        // in 1.5, this would be builder.append(cs, start, end);
        for(int i = start; i<end; i++) {
            buffer.append(cs.charAt(i));
        }
        return buffer;
    }

}
