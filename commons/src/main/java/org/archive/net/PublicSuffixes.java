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
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
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
 * 
 * this version of PublicSuffixes uses suffix-tree data structure for generating less
 * redundant regular expression. It may be even possible to write a light-weight,
 * thread-safe matcher based on this class.
 * @author Kenji Nagahashi 
 */
public class PublicSuffixes {
    protected static Pattern topmostAssignedSurtPrefixPattern;
    protected static String topmostAssignedSurtPrefixRegex;

    /**
     * prefix tree node. each Node represents sequence of letters (prefix)
     * and alternative sequences following it (list of Node's). Nodes in 
     * {@code branches} are sorted for skip list like lookup and for generating
     * effective regular expression (see {@link #compareTo(Node)} and {@link #compareTo(char).)
     * 
     * as is intended for internal use only, there's no access methods. procedures for updating
     * prefix tree with new input are defined within this class ({@link #addBranch(CharSequence)}).
     * 
     * terminal node could be represented in two different form: 1) Node with zero branches,
     * or 2) Node with zero-length {@code cs}. So, root node must be initialized with empty (not null)
     * {@code branches} unless empty string matches the overall pattern. 
     * {@code cs} must not be null except for root node. 
     */
    public static class Node implements Comparable<Node> {
        CharSequence cs;
        List<Node> branches;
        public Node() {
            this("", null);
        }
        protected Node(CharSequence cs) {
            this(cs, null);
        }
        protected Node(CharSequence cs, List<Node> branches) {
            this.cs = cs;
            this.branches = branches;
        }
        public void addBranch(CharSequence s) {
            if (branches == null) {
                branches = new ArrayList<Node>();
                branches.add(new Node("", null));
            }
            for (int i = 0; i < branches.size(); i++) {
                Node alt = branches.get(i);
                if (alt.add(s)) return;
                if (alt.compareTo(s.charAt(0)) > 0) {
                    Node alt1 = new Node(s, null);
                    branches.add(i, alt1);
                    return;
                }
            }
            Node alt2 = new Node(s, null);
            branches.add(alt2);
        }
        public boolean add(CharSequence s) {
            int l = Math.min(s.length(), cs.length());
            int i = 0;
            while (i < l && s.charAt(i) == cs.charAt(i))
                i++;
            // zero-length match holds only when both cs and s are empty.
            if (i == 0) return cs.length() == 0 && s.length() == 0;
            if (i < cs.length()) {
                CharSequence cs0 = cs.subSequence(0, i);
                CharSequence cs1 = cs.subSequence(i, cs.length());
                CharSequence cs2 = s.subSequence(i, s.length());
                cs = cs0;
                Node alt1 = new Node(cs1, branches);
                (branches = new ArrayList<Node>()).add(alt1);
                addBranch(cs2);
            } else {
                assert i == cs.length();
                addBranch(s.subSequence(i, s.length()));
            }
            return true;
        }
        public int compareTo(Node other) {
            if (other.cs == null || other.cs.length() == 0)
                return (cs == null || cs.length() == 0) ? 0 : -1;
            return compareTo(other.cs.charAt(0));
        }
        public int compareTo(char oc) {
            if (cs == null || cs.length() == 0) return 1;
            // '!' and '*' must come after ordinary letters, in this order, for regexp
            // to work as intended.
            char c = cs.charAt(0);
            if (c == oc) return 0;
            if (c == '!') return oc == '*' ? -1 : 1;
            if (c == '*') return 1;
            if (oc == '*' || oc == '!') return -1;
            return Character.valueOf(c).compareTo(oc);
            // for generating the same regexp as previous version.
            //return Character.valueOf(oc).compareTo(c);
        }        
    }
    
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
        InputStream is;
        if (args.length == 0 || "=".equals(args[0])) {
            // use bundled list
            is = PublicSuffixes.class.getClassLoader().getResourceAsStream(
            "effective_tld_names.dat");
        } else {
            is = new FileInputStream(args[0]);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        String regex = getTopmostAssignedSurtPrefixRegex(reader);
        IOUtils.closeQuietly(is);
        
        boolean needsClose = false;
        BufferedWriter writer;
        if (args.length >= 2) {
            // write to specified file
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
     * SURT-ordering. Leaves glob-style '*' wildcarding in place. Returns root
     * node of SURT-ordered prefix tree.
     * 
     * @param reader
     * @return root of prefix tree node.
     * @throws IOException
     */
    protected static Node readPublishedFileToSurtTrie(BufferedReader reader) throws IOException {
        // initializing with empty Alt list prevents empty pattern from being
        // created for the first addBranch()
        Node alt = new Node(null, new ArrayList<Node>());
        String line;
        while ((line = reader.readLine()) != null) {
            // discard whitespace, empty lines, comments, exceptions
            line = line.trim();
            if (line.length() == 0 || line.startsWith("//")) continue;
            // discard utf8 notation after entry
            line = line.split("\\s+")[0];
            // TODO: maybe we don't need to create lower-cased String
            line = line.toLowerCase();
            // SURT-order domain segments
            String[] segs = line.split("\\.");
            StringBuilder sb = new StringBuilder();
            for (int i = segs.length - 1; i >= 0; i--) {
                if (segs[i].length() == 0) continue;
                sb.append(segs[i]).append(',');
            }
            alt.addBranch(sb.toString());
        }
        return alt;
    }
    /**
     * utility function for dumping prefix tree structure. intended for debug use.
     * @param alt root of prefix tree.
     * @param lv indent level. 0 for root (no indent).
     * @param out writer to send output to.
     */
    public static void dump(Node alt, int lv, PrintWriter out) {
        for (int i = 0; i < lv; i++)
            out.print("  ");
        out.println(alt.cs != null ? ('"'+alt.cs.toString()+'"') : "(null)");
        if (alt.branches != null) {
            for (Node br : alt.branches) {
                dump(br, lv + 1, out);
            }
        }
    }
    /**
     * bulids regular expression from prefix-tree {@code alt} into buffer {@code sb}.
     * @param alt prefix tree root.
     * @param sb StringBuffer to store regular expression.
     */
    protected static void buildRegex(Node alt, StringBuilder sb) {
        String close = null;
        if (alt.cs != null) {
            // actually '!' always be the first character, because it is
            // always used along with '*'.
            for (int i = 0; i < alt.cs.length(); i++) {
                char c = alt.cs.charAt(i);
                if (c == '!') {
                    if (close != null)
                        throw new RuntimeException("more than one '!'");
                    sb.append("(?=");
                    close = ")";
                } else if (c == '*') {
                    sb.append("[-\\w]+");
                } else {
                    sb.append(c);
                }
            }
        }
        if (alt.branches != null) {
            // alt.branches.size() should always be > 1
            if (alt.branches.size() > 1) {
                sb.append("(?:");
            }
            String sep = "";
            for (Node alt1 : alt.branches) {
                sb.append(sep); sep = "|";
                buildRegex(alt1, sb);
            }
            if (alt.branches.size() > 1) {
                sb.append(")");
            }
        }
        if (close != null)
            sb.append(close);
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
    private static String surtPrefixRegexFromTrie(Node trie) {
        StringBuilder regex = new StringBuilder();
        regex.append("(?ix)^\n");
        trie.addBranch("*,"); // for new/unknown TLDs
        buildRegex(trie, regex);
        regex.append("\n([-\\w]+,)");
        return regex.toString();
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
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        PublicSuffixes.class.getClassLoader().getResourceAsStream(
                        "effective_tld_names.dat"), "UTF-8"));
                topmostAssignedSurtPrefixRegex = getTopmostAssignedSurtPrefixRegex(reader);
                IOUtils.closeQuietly(reader);
            } catch (UnsupportedEncodingException ex) {
                // should never happen
                throw new RuntimeException(ex);
            }
        }
        return topmostAssignedSurtPrefixRegex;
    }

    public static String getTopmostAssignedSurtPrefixRegex(BufferedReader reader) {
        try {
            Node trie = readPublishedFileToSurtTrie(reader);
            return surtPrefixRegexFromTrie(trie);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
