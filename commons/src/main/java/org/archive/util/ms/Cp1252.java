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
