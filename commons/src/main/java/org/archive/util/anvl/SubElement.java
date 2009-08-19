/* SubElement
*
* $Id$
*
* Created on July 26, 2006.
*
* Copyright (C) 2006 Internet Archive.
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
package org.archive.util.anvl;

/**
 * Abstract ANVL 'data element' sub-part.
 * Subclass to make a Comment, a Label, or a Value.
 * @author stack
 */
abstract class SubElement {
    private final String e;

    protected SubElement() {
        this(null);
    }

    public SubElement(final String s) {
        this.e = baseCheck(s);
    }

    protected String baseCheck(final String s) {
        // Check for null.
        if (s == null) {
            throw new IllegalArgumentException("Can't be null");
        }
        // Check for CRLF.
        for (int i = 0; i < s.length(); i++) {
            checkCharacter(s.charAt(i), s, i);
        }
        return s;
    }
    
    protected void checkCharacter(final char c, final String srcStr,
    		final int index) {
        checkControlCharacter(c, srcStr, index);
        checkCRLF(c, srcStr, index);
    }
    
    protected void checkControlCharacter(final char c, final String srcStr,
            final int index) {
        if (Character.isISOControl(c) && !Character.isWhitespace(c) ||
                !Character.isValidCodePoint(c)) {
            throw new IllegalArgumentException(srcStr +
                " contains a control character(s) or invalid code point: 0x" +
                Integer.toHexString(c));
        }
    }
    
    protected void checkCRLF(final char c, final String srcStr,
            final int index) {
        if (ANVLRecord.isCROrLF(c)) {
            throw new IllegalArgumentException(srcStr +
                " contains disallowed CRLF control character(s): 0x" +
                Integer.toHexString(c));
        }
    }
    
    @Override
    public String toString() {
        return e;
    }
}