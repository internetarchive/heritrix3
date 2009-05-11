/* JavaLiterals
 * 
 * Created on Dec 31, 2003
 *
 * Copyright (C) 2004 Internet Archive.
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
 * Utility functions to escape or unescape Java literal strings.
 *
 * @author gojomo
 *
 */
public class JavaLiterals {

  public static String escape(String raw) {
    StringBuffer escaped = new StringBuffer();
    for(int i = 0; i<raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case '\b':
          escaped.append("\\b");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\f':
          escaped.append("\\f");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\"':
          escaped.append("\\\"");
          break;
        case '\'':
          escaped.append("\\'");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        default:
          if(Character.getType(c)==Character.CONTROL) {
            String unicode = Integer.toHexString((int)c);
            while(unicode.length()<4) {
              unicode = "0"+unicode;
            }
            escaped.append("\\u"+unicode);
          } else {
            escaped.append(c);
          }
      }

    }
    return escaped.toString();
  }

  public static String unescape(String escaped) {
    StringBuffer raw = new StringBuffer();
    for(int i = 0; i<escaped.length(); i++) {
      char c = escaped.charAt(i);
      if (c!='\\') {
        raw.append(c);
      } else {
        i++;
        if(i>=escaped.length()) {
          // trailing '/'
          raw.append(c);
          continue;
        }
        c = escaped.charAt(i);
        switch (c) {
          case 'b':
            raw.append('\b');
            break;
          case 't':
            raw.append('\t');
            break;
          case 'n':
            raw.append('\n');
            break;
          case 'f':
            raw.append('\f');
            break;
          case 'r':
            raw.append('r');
            break;
          case '"':
            raw.append('\"');
            break;
          case '\'':
            raw.append('\'');
            break;
          case '\\':
            raw.append('\\');
            break;
          case 'u':
            // unicode hex escape
            try {
              int unicode = Integer.parseInt(escaped.substring(i+1,i+5),16);
              raw.append((char)unicode);
              i = i + 4;
            } catch (IndexOutOfBoundsException e) {
              // err
              raw.append("\\u");
            }
            break;
          default:
              if(Character.isDigit(c)) {
                // octal escape
                int end = Math.min(i+4,escaped.length());
                int octal = Integer.parseInt(escaped.substring(i+1,end),8);
                if(octal<256) {
                  raw.append((char)octal);
                  i = end - 1;
                } else {
                  // err
                  raw.append('\\');
                  raw.append(c);
                }
              }
              break;
        }
      }
    }
    return raw.toString();
  }
}
