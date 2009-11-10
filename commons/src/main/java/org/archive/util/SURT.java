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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.net.UURIFactory;

/**
 * Sort-friendly URI Reordering Transform.
 * 
 * Converts URIs of the form:
 * 
 *   scheme://userinfo@domain.tld:port/path?query#fragment
 * 
 * ...into...
 * 
 *   scheme://(tld,domain,:port@userinfo)/path?query#fragment
 * 
 * The '(' ')' characters serve as an unambiguous notice that the so-called 
 * 'authority' portion of the URI ([userinfo@]host[:port] in http URIs) has 
 * been transformed; the commas prevent confusion with regular hostnames.
 * 
 * This remedies the 'problem' with standard URIs that the host portion of a 
 * regular URI, with its dotted-domains, is actually in reverse order from 
 * the natural hierarchy that's usually helpful for grouping and sorting.
 * 
 * The value of respecting URI case variance is considered negligible: it
 * is vanishingly rare for case-variance to be meaningful, while URI case-
 * variance often arises from people's confusion or sloppiness, and they
 * only correct it insofar as necessary to avoid blatant problems. Thus 
 * the usual SURT form is considered to be flattened to all lowercase, and 
 * not completely reversible. 
 * 
 * @author gojomo
 */
public class SURT {
    static char DOT = '.';
    static String BEGIN_TRANSFORMED_AUTHORITY = "(";
    static String TRANSFORMED_HOST_DELIM = ",";
    static String END_TRANSFORMED_AUTHORITY = ")";
    
    // 1: scheme://
    // 2: userinfo (if present)
    // 3: @ (if present)
    // 4: dotted-quad host
    // 5: other host
    // 6: :port
    // 7: path
    static String URI_SPLITTER = 
            "^(\\w+://)(?:([-\\w\\.!~\\*'\\(\\)%;:&=+$,]+?)(@))?"+
    //        1           2                                 3    
            "(?:((?:\\d{1,3}\\.){3}\\d{1,3})|(\\S+?))(:\\d+)?(/\\S*)?$";
    //           4                            5       6       7
    
    // RFC2396 
    //       reserved    = ";" | "/" | "?" | ":" | "@" | "&" | "=" | "+" |
    //                     "$" | ","
    //       unreserved  = alphanum | mark
    //       mark        = "-" | "_" | "." | "!" | "~" | "*" | "'" | "(" | ")"
    //       userinfo    = *( unreserved | escaped |
    //                     ";" | ":" | "&" | "=" | "+" | "$" | "," )
    //       escaped     = "%" hex hex


    /**
     * Utility method for creating the SURT form of the URI in the
     * given String.
     * 
     * By default, does not preserve casing. 
     * 
     * @param s String URI to be converted to SURT form
     * @return SURT form 
     */
    public static String fromURI(String s) {
        return fromURI(s,false);
    }
    
    /**
     * Utility method for creating the SURT form of the URI in the
     * given String.
     * 
     * If it appears a bit convoluted in its approach, note that it was
     * optimized to minimize object-creation after allocation-sites profiling 
     * indicated this method was a top source of garbage in long-running crawls.
     * 
     * Assumes that the String URI has already been cleaned/fixed (eg
     * by UURI fixup) in ways that put it in its crawlable form for 
     * evaluation.
     * 
     * @param s String URI to be converted to SURT form
     * @param preserveCase whether original case should be preserved
     * @return SURT form 
     */
    public static String fromURI(String s, boolean preserveCase) {
        Matcher m = TextUtils.getMatcher(URI_SPLITTER,s);
        if(!m.matches()) {
            // not an authority-based URI scheme; return unchanged
            TextUtils.recycleMatcher(m);
            return s;
        }
        // preallocate enough space for SURT form, which includes
        // 3 extra characters ('(', ')', and one more ',' than '.'s
        // in original)
        StringBuffer builder = new StringBuffer(s.length()+3);
        append(builder,s,m.start(1),m.end(1)); // scheme://
        builder.append(BEGIN_TRANSFORMED_AUTHORITY); // '('
        
        if(m.start(4)>-1) {
            // dotted-quad ip match: don't reverse
            append(builder,s,m.start(4),m.end(4));
        } else {
            // other hostname match: do reverse
            int hostSegEnd = m.end(5);
            int hostStart = m.start(5); 
            for(int i = m.end(5)-1; i>=hostStart; i--) {
                if(s.charAt(i-1)!=DOT && i > hostStart) {
                    continue;
                }
                append(builder,s,i,hostSegEnd); // rev host segment
                builder.append(TRANSFORMED_HOST_DELIM);     // ','
                hostSegEnd = i-1;
            }
        }

        append(builder,s,m.start(6),m.end(6)); // :port
        append(builder,s,m.start(3),m.end(3)); // at
        append(builder,s,m.start(2),m.end(2)); // userinfo
        builder.append(END_TRANSFORMED_AUTHORITY); // ')'
        append(builder,s,m.start(7),m.end(7)); // path
        if (!preserveCase) {
            for(int i = 0; i < builder.length(); i++) {
                builder.setCharAt(i,Character.toLowerCase(builder.charAt((i))));
            }
        }
        TextUtils.recycleMatcher(m);
        return builder.toString();
    }
    
    private static void append(StringBuffer b, CharSequence cs, int start, 
            int end) {
        if (start < 0) {
            return;
        }
        b.append(cs, start, end);
    }

    /**
     * Given a plain URI or hostname/hostname+path, deduce an implied SURT 
     * prefix from it. Results may be unpredictable on strings that cannot
     * be interpreted as URIs. 
     * 
     * UURI 'fixup' is applied to the URI that is built. 
     *
     * @param u URI or almost-URI to consider
     * @return implied SURT prefix form
     */
    public static String prefixFromPlain(String u) {
        u = fromPlain(u);
        // truncate to implied prefix
        u = SurtPrefixSet.asPrefix(u);
        return u;
    }

    /**
     * Given a plain URI or hostname/hostname+path, give its SURT form.
     * Results may be unpredictable on strings that cannot
     * be interpreted as URIs. 
     * 
     * UURI 'fixup' is applied to the URI before conversion to SURT 
     * form. 
     *
     * @param u URI or almost-URI to consider
     * @return implied SURT prefix form
     */
    public static String fromPlain(String u) {
        u = ArchiveUtils.addImpliedHttpIfNecessary(u);
        boolean trailingSlash = u.endsWith("/");
        // ensure all typical UURI cleanup (incl. IDN-punycoding) is done
        try {
            u = UURIFactory.getInstance(u).toString();
        } catch (URIException e) {
            e.printStackTrace();
            // allow to continue with original string uri
        }
        // except: don't let UURI-fixup add a trailing slash
        // if it wasn't already there (presence or absence of
        // such slash has special meaning specifying implied
        // SURT prefixes)
        if(!trailingSlash && u.endsWith("/")) {
            u = u.substring(0,u.length()-1);
        }
        // convert to full SURT
        u = SURT.fromURI(u);
        return u;
    }
    
    /**
     * Allow class to be used as a command-line tool for converting 
     * URL lists (or naked host or host/path fragments implied
     * to be HTTP URLs) to SURT form. Lines that cannot be converted
     * are returned unchanged. 
     * 
     *
     * Read from stdin or first file argument. Writes to stdout or 
     * second argument filename
     * 
     * @param args cmd-line arguments
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        InputStream in = args.length > 0 ? new BufferedInputStream(
                new FileInputStream(args[0])) : System.in;
        PrintStream out = args.length > 1 ? new PrintStream(
                new BufferedOutputStream(new FileOutputStream(args[1])))
                : System.out;
        BufferedReader br =
            new BufferedReader(new InputStreamReader(in));
        String line;
        while((line = br.readLine())!=null) {
            if(line.indexOf("#")>0) line=line.substring(0,line.indexOf("#"));
            line = line.trim();
            if(line.length()==0) continue;
            line = ArchiveUtils.addImpliedHttpIfNecessary(line);
            out.println(SURT.fromURI(line));
        }
        br.close();
        out.close();
    }
}
