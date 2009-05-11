/* Cp1252
*
* Created on September 12, 2006
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
package org.archive.util.ms;


import java.io.UnsupportedEncodingException;


/**
 * A fast implementation of code page 1252.  This is used to convert bytes
 * to characters in .doc files that don't use unicode.
 * 
 * <p>The Java Charset APIs seemed like overkill for these translations,
 * since 1 byte always translates into 1 character.
 * 
 * @author pjack
 */
public class Cp1252 {


    /**
     * The translation table.  If x is an unsigned byte from a .doc
     * text stream, then XLAT[x] is the Unicode character that byte
     * represents.
     */
    final private static char[] XLAT = createTable();


    /**
     * Static utility library, do not instantiate.
     */            
    private Cp1252() {
    }


    /**
     * Generates the translation table.  The Java String API is used for each
     * possible byte to determine the corresponding Unicode character.
     * 
     * @return  the Cp1252 translation table
     */
    private static char[] createTable() {
        char[] result = new char[256];
        byte[] b = new byte[1];
        for (int i = 0; i < 256; i++) try {
            b[0] = (byte)i;
            String s = new String(b, "Cp1252");
            result[i] = s.charAt(0);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return result;
    }


    /**
     * Returns the Unicode character for the given Cp1252 byte.
     * 
     * @param b   an unsigned byte from 0 to 255
     * @return  the Unicode character corresponding to that byte
     */
    public static char decode(int b) {
        return XLAT[b];
    }


}
