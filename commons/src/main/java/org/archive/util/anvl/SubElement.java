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