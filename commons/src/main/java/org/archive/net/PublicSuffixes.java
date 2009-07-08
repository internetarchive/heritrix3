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

package org.archive.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.archive.util.TextUtils;

/**
 * Utility class for making use of the information about 'public suffixes' at
 * http://publicsuffix.org.
 * 
 * The public suffix list (once known as 'effective TLDs') was motivated by the
 * need to decide on which broader domains a subdomain was allowed to set
 * cookies. For example, a server at 'www.example.com' can set cookies for
 * 'www.example.com' or 'example.com' but not 'com'. 'www.example.co.uk' can set
 * cookies for 'www.example.co.uk' or 'example.co.uk' but not 'co.uk' or 'uk'.
 * The number of rules for all top-level-domains and 2nd- or 3rd- level domains
 * has become quite long; essentially the broadest domain a subdomain may assign
 * to is the one that was sold/registered to a specific name registrant.
 * 
 * This concept should be useful in other contexts, too. Grouping URIs (or
 * queues of URIs to crawl) together with others sharing the same registered
 * suffix may be useful for applying the same rules to all, such as assigning
 * them to the same queue or crawler in a multi- machine setup.
 * 
 * As of Heritrix3, we prefer the term 'Assignment Level Domain' (ALD) 
 * for such domains, by analogy to 'Top Level Domain' (TLD) or '2nd Level 
 * Domain' (2LD), etc. 
 * 
 * @author Gojomo
 */
public class PublicSuffixes {
    protected static Pattern topmostAssignedSurtPrefixPattern;
    protected static String topmostAssignedSurtPrefixRegex;

    /**
     * Utility method for dumping a regex String, based on a published public
     * suffix list, which matches any SURT-form hostname up through the broadest
     * 'private' (assigned/sold) domain-segment. That is, for any of the
     * SURT-form hostnames...
     * 
     * com,example, com,example,www, com,example,california,www
     * 
     * ...the regex will match 'com,example,'.
     * 
     * @param args
     * @throws IOException
     */
    public static void main(String args[]) throws IOException {

        String regex;
        
        if (args.length == 0 || "=".equals(args[0])) {
            // use bundled list
            regex = getTopmostAssignedSurtPrefixRegex();
        } else {
            // use specified filename
            BufferedReader reader = new BufferedReader(new FileReader(args[0]));
            regex = getTopmostAssignedSurtPrefixRegex(reader);
            IOUtils.closeQuietly(reader);
        }

        boolean needsClose = false;
        BufferedWriter writer;
        if (args.length >= 2) {
            // writer to specified file
            writer = new BufferedWriter(new FileWriter(args[1]));
            needsClose = true;
        } else {
            // write to stdout
            writer = new BufferedWriter(new OutputStreamWriter(System.out));
        }
        writer.append(regex);
        writer.flush();
        if (needsClose) {
            writer.close();
        }
    }

    /**
     * Reads a file of the format promulgated by publicsuffix.org, ignoring
     * comments and '!' exceptions/notations, converting domain segments to
     * SURT-ordering. Leaves glob-style '*' wildcarding in place. Returns sorted
     * list of unique SURT-ordered prefixes.
     * 
     * @param reader
     * @return
     * @throws IOException
     */
    public static List<String> readPublishedFileToSurtList(BufferedReader reader)
            throws IOException {
        String line;
        List<String> list = new ArrayList<String>();
        while ((line = reader.readLine()) != null) {

            // discard whitespace, empty lines, comments, exceptions
            line = line.trim();
            if (line.length() == 0 || line.startsWith("//")) {
                continue;
            }
            // discard utf8 notation after entry
            line = line.split("\\s+")[0];
            line = line.toLowerCase();

            // SURT-order domain segments
            String[] segs = line.split("\\.");
            StringBuilder surtregex = new StringBuilder();
            for (int i = segs.length - 1; i >= 0; i--) {
                if (segs[i].length() > 0) {
                    // current list has a stray '?' in a .no domain
                    String fixed = segs[i].replaceAll("\\?", "_");
                    // replace '!' with '+' to indicate lookahead-for-exceptions
                    // (gets those to sort before '*' at later build-step)
                    fixed = fixed.replaceAll("!", "+");
                    surtregex.append(fixed + ",");
                }
            }
            list.add(surtregex.toString());
        }

        Collections.sort(list);
        // uniq
        String last = "";
        Iterator<String> iter = list.iterator();
        while (iter.hasNext()) {
            String s = iter.next();
            if (s.equals(last)) {
                iter.remove();
                continue;
            }
            last = s;
//            System.out.println(s);
        }
        return list;
    }

    /**
     * Converts SURT-ordered list of public prefixes into a Java regex which
     * matches the public-portion "plus one" segment, giving the domain on which
     * cookies can be set or other policy grouping should occur. Also adds to
     * regex a fallback matcher that for any new/unknown TLDs assumes the
     * second-level domain is assignable. (Eg: 'zzz,example,').
     * 
     * @param list
     * @return
     */
    private static String surtPrefixRegexFromSurtList(List<String> list) {
        StringBuilder regex = new StringBuilder();
        regex.append("(?ix)^\n");
        TreeSet<String> prefixes = new TreeSet<String>(Collections
                .reverseOrder());
        prefixes.addAll(list);
        prefixes.add("*,"); // for new/unknown TLDs
        buildRegex("", regex, prefixes);
        regex.append("\n([\\-\\w]+,)");
        String rstring = regex.toString();
        // convert glob-stars to word-char-runs
        rstring = rstring.replaceAll("\\*", "[\\\\-\\\\w]+");
        return rstring;
    }

    protected static void buildRegex(String stem, StringBuilder regex,
            SortedSet<String> prefixes) {
        if (prefixes.isEmpty()) {
            return;
        }
        if (prefixes.size() == 1 && prefixes.first().equals(stem)) {
            // avoid unnecessary "(?:)"
            return;
        }
        regex.append("(?:");
        if (stem.length() == 0) {
            regex.append("\n "); // linebreak-space before first character
        }
        Iterator<String> iter = prefixes.iterator();
        char c = 0;
        while (iter.hasNext()) {
            String s = iter.next();
            if (s.length() > stem.length()) {
                char d = s.charAt(stem.length());

                if (d == '+') {
                    // convert exception to zero-width-positive-lookahead
                    regex.append("(?=" + s.substring(stem.length() + 1) + ")");
                } else {
                    if (d == c) {
                        continue;
                    }
                    c = d;
                    regex.append(c);
                    String newStem = s.substring(0, stem.length() + 1);
                    SortedSet<String> tail = prefixes.tailSet(newStem);
                    SortedSet<String> range = null;
                    successor: for (String candidate : tail) {
                        if (!candidate.equals(newStem)) {
                            range = prefixes.subSet(s, candidate);
                            break successor;
                        }
                    }
                    if (range == null) {
                        range = prefixes.tailSet(s);
                    }
                    buildRegex(newStem, regex, range);
                }
                regex.append('|');
            } else {
                // empty suffix; insert dummy to be eaten when loop exits
                regex.append('@');
            }
        }
        // eat the trailing '|' (if no empty '@') or dummy
        regex.deleteCharAt(regex.length() - 1);
        regex.append(')');
        if (stem.length() == 1) {
            regex.append('\n'); // linebreak for TLDs
        }
    }

    public static synchronized Pattern getTopmostAssignedSurtPrefixPattern() {
        if (topmostAssignedSurtPrefixPattern == null) {
            topmostAssignedSurtPrefixPattern = Pattern
                    .compile(getTopmostAssignedSurtPrefixRegex());
        }
        return topmostAssignedSurtPrefixPattern;
    }

    public static synchronized String getTopmostAssignedSurtPrefixRegex() {
        if (topmostAssignedSurtPrefixRegex == null) {
            // use bundled list
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    PublicSuffixes.class.getClassLoader().getResourceAsStream(
                            "effective_tld_names.dat")));
            topmostAssignedSurtPrefixRegex = getTopmostAssignedSurtPrefixRegex(reader);
            IOUtils.closeQuietly(reader);
        }
        return topmostAssignedSurtPrefixRegex;
    }

    public static String getTopmostAssignedSurtPrefixRegex(BufferedReader reader) {
        List<String> list;
        try {
            list = readPublishedFileToSurtList(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return surtPrefixRegexFromSurtList(list);
    }

    /**
     * Truncate SURT to its topmost assigned domain segment; that is, 
     * the public suffix plus one segment, but as a SURT-ordered prefix. 
     * 
     * if the pattern doesn't match, the passed-in SURT is returned.
     * 
     * @param surt SURT to truncate
     * @return truncated-to-topmost-assigned SURT prefix
     */
    public static String reduceSurtToAssignmentLevel(String surt) {
        Matcher matcher = TextUtils.getMatcher(
                getTopmostAssignedSurtPrefixRegex(), surt);
        if (matcher.find()) {
            surt = matcher.group();
        }
        TextUtils.recycleMatcher(matcher);
        return surt;
    }
}
