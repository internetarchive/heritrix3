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
