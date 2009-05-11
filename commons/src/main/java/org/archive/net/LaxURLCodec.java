/* IAURLCodec
*
* $Id$
*
* Created on Jul 21, 2005
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
package org.archive.net;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.BitSet;

import org.apache.commons.codec.net.URLCodec;

/**
 * @author gojomo
 */
public class LaxURLCodec extends URLCodec {
    public static LaxURLCodec DEFAULT = new LaxURLCodec("UTF-8");

    // passthrough constructor
    public LaxURLCodec(String encoding) {
        super(encoding);
    }

    /**
     * Decodes an array of URL safe 7-bit characters into an array of 
     * original bytes. Escaped characters are converted back to their 
     * original representation.
     * 
     * Differs from URLCodec.decodeUrl() in that it throws no 
     * exceptions; bad or incomplete escape sequences are ignored
     * and passed into result undecoded. This matches the behavior
     * of browsers, which will use inconsistently-encoded URIs
     * in HTTP request-lines. 
     *
     * @param bytes array of URL safe characters
     * @return array of original bytes 
     */
    public static final byte[] decodeUrlLoose(byte[] bytes) 
    {
        if (bytes == null) {
            return null;
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); 
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i];
            if (b == '+') {
                buffer.write(' ');
                continue;
            }
            if (b == '%') {
                if(i+2<bytes.length) {
                    int u = Character.digit((char)bytes[i+1], 16);
                    int l = Character.digit((char)bytes[i+2], 16);
                    if (u > -1 && l > -1) {
                        // good encoding
                        int c = ((u << 4) + l);
                        buffer.write((char)c);
                        i += 2;
                        continue;
                    } // else: bad encoding digits, leave '%' in place
                } // else: insufficient encoding digits, leave '%' in place
            }
            buffer.write(b);
        }
        return buffer.toByteArray(); 
    }

    /**
     * A more expansive set of ASCII URI characters to consider as 'safe' to
     * leave unencoded, based on actual browser behavior.
     */
    public static BitSet EXPANDED_URI_SAFE = new BitSet(256);
    static {
        // alpha characters
        for (int i = 'a'; i <= 'z'; i++) {
            EXPANDED_URI_SAFE.set(i);
        }
        for (int i = 'A'; i <= 'Z'; i++) {
            EXPANDED_URI_SAFE.set(i);
        }
        // numeric characters
        for (int i = '0'; i <= '9'; i++) {
            EXPANDED_URI_SAFE.set(i);
        }
        // special chars
        EXPANDED_URI_SAFE.set('-');
        EXPANDED_URI_SAFE.set('~');
        EXPANDED_URI_SAFE.set('_');
        EXPANDED_URI_SAFE.set('.');
        EXPANDED_URI_SAFE.set('*');
        EXPANDED_URI_SAFE.set('/');
        EXPANDED_URI_SAFE.set('=');
        EXPANDED_URI_SAFE.set('&');
        EXPANDED_URI_SAFE.set('+');
        EXPANDED_URI_SAFE.set(',');
        EXPANDED_URI_SAFE.set(':');
        EXPANDED_URI_SAFE.set(';');
        EXPANDED_URI_SAFE.set('@');
        EXPANDED_URI_SAFE.set('$');
        EXPANDED_URI_SAFE.set('!');
        EXPANDED_URI_SAFE.set(')');
        EXPANDED_URI_SAFE.set('(');
        // experiments indicate: Firefox (1.0.6) never escapes '%'
        EXPANDED_URI_SAFE.set('%');
        // experiments indicate: Firefox (1.0.6) does not escape '|' or '''
        EXPANDED_URI_SAFE.set('|'); 
        EXPANDED_URI_SAFE.set('\'');
    }
    
    public static BitSet QUERY_SAFE = new BitSet(256);
    static {
        QUERY_SAFE.or(EXPANDED_URI_SAFE);
        // Tests indicate Firefox (1.0.7-1) doesn't escape curlies in query str.
        QUERY_SAFE.set('{');
        QUERY_SAFE.set('}');
        // nor any of these: [ ] ^ ? 
        QUERY_SAFE.set('[');
        QUERY_SAFE.set(']');
        QUERY_SAFE.set('^');
        QUERY_SAFE.set('?');
    }
    
    /**
     * Encodes a string into its URL safe form using the specified
     * string charset. Unsafe characters are escaped.
     * 
     * This method is analogous to superclass encode() methods,
     * additionally offering the ability to specify a different
     * 'safe' character set (such as EXPANDED_URI_SAFE). 
     * 
     * @param safe BitSet of characters that don't need to be encoded
     * @param pString String to encode
     * @param cs Name of character set to use
     * @return Encoded version of <code>pString</code>.
     * @throws UnsupportedEncodingException
     */
    public String encode(BitSet safe, String pString, String cs)
    throws UnsupportedEncodingException {
        if (pString == null) {
            return null;
        }
        return new String(encodeUrl(safe,pString.getBytes(cs)), "US-ASCII");
    }
}
