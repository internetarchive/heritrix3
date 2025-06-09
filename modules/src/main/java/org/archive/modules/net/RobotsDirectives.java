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
package org.archive.modules.net;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

import org.archive.bdb.AutoKryo;


/**
 * Represents the directives that apply to a user-agent (or set of
 * user-agents)
 */
public class RobotsDirectives implements Serializable {
    private static final long serialVersionUID = 5386542759286155384L;
    
    protected PatternSet disallows = new PatternSet();
    protected PatternSet allows = new PatternSet();
    protected float crawlDelay = -1;
    public transient boolean hasDirectives = false;

    /**
     * A set of robots.txt path patterns. Patterns without wildcards are stored in a NavigableSet for faster matching.
     */
    protected static class PatternSet {
        private final NavigableSet<String> prefixes = new ConcurrentSkipListSet<>();
        private final Set<WildcardPattern> wildcards = new HashSet<>();

        public void add(String pattern) {
            if (pattern.endsWith("$") || pattern.contains("*")) {
                wildcards.add(new WildcardPattern(pattern));
            } else {
                prefixes.add(pattern);
            }
        }

        /**
         * Returns the length of the longest pattern matching the given path, or zero if no patterns match.
         */
        private int longestMatch(String path) {
            int longestMatch = longestPrefixLength(path);
            for (WildcardPattern pattern : wildcards) {
                if (pattern.length > longestMatch && pattern.matches(path)) {
                    longestMatch = pattern.length;
                }
            }
            return longestMatch;
        }

        /**
         * @return length of longest entry in {@code prefixes} that prefixes {@code str}, or zero
         *         if no entry prefixes {@code str}
         */
        private int longestPrefixLength(String str) {
            String possiblePrefix = prefixes.floor(str);
            if (possiblePrefix != null && str.startsWith(possiblePrefix)) {
                return possiblePrefix.length();
            } else {
                return 0;
            }
        }

    }

    protected static class WildcardPattern {
        private final String[] segments;
        private final int length;
        private final boolean anchored;

        public WildcardPattern(String pattern) {
            this.length = pattern.length();
            if (pattern.endsWith("$")) {
                pattern = pattern.substring(0, pattern.length() - 1);
                anchored = !pattern.endsWith("*"); // *$ is effectively unanchored
            } else {
                anchored = false;
            }
            segments = pattern.split("\\*", -1);
        }

        public boolean matches(String path) {
            int position = 0;
            if (!segments[0].isEmpty()) {
                if (!path.startsWith(segments[0])) {
                    return false;
                }
                position = segments[0].length();
            }

            for (int i = 1; i < segments.length; i++) {
                String segment = segments[i];
                if (segment.isEmpty()) continue;
                int match = path.indexOf(segment, position);
                if (match < 0) return false;
                position = match + segment.length();
            }

            if (anchored) {
                return position == path.length();
            }

            return true;
        }

        @Override
        public boolean equals(Object object) {
            if (object == null || getClass() != object.getClass()) return false;
            WildcardPattern that = (WildcardPattern) object;
            return length == that.length && anchored == that.anchored && Objects.deepEquals(segments, that.segments);
        }

        @Override
        public int hashCode() {
            return Objects.hash(length, Arrays.hashCode(segments), anchored);
        }
    }

    public boolean allows(String path) {
        return disallows.longestMatch(path) <= allows.longestMatch(path);
    }

    public void addDisallow(String path) {
        hasDirectives = true;
        if(path.length()==0) {
            // ignore empty-string disallows 
            // (they really mean allow, when alone)
            return;
        }
        disallows.add(path);
    }

    public void addAllow(String path) {
        hasDirectives = true;
        allows.add(path);
    }

    public void setCrawlDelay(float i) {
        hasDirectives = true;
        crawlDelay=i;
    }

    public float getCrawlDelay() {
        return crawlDelay;
    }
    
    // Kryo support
    public static void autoregisterTo(AutoKryo kryo) {
        kryo.register(RobotsDirectives.class);
        kryo.register(PatternSet.class);
        kryo.register(WildcardPattern.class);
        kryo.register(HashSet.class);
        kryo.useReferencesFor(RobotsDirectives.class);
        kryo.autoregister(ConcurrentSkipListSet.class); // now used instead of PrefixSet in RobotsDirectives
    }

}
