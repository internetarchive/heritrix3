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

package org.archive.crawler.restlet;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The space separated columns of a crawl.log line, as written by
 * {@link org.archive.crawler.io.UriProcessingFormatter}:
 *
 * <pre>
 * timestamp status size uri hopPath via mime #thread fetchTs+dur digest sourceTag annotations [extraInfo]
 * </pre>
 *
 * Empty values are logged as '-', so the columns are never empty.
 */
public class CrawlLogLine {
    protected static final int STATUS = 1;
    protected static final int SIZE = 2;
    protected static final int URL = 3;
    protected static final int HOP_PATH = 4;
    protected static final int VIA = 5;
    protected static final int MIME = 6;
    protected static final int FETCH_TIME = 8;
    protected static final int ANNOTATIONS = 11;
    /** columns after this one (extra info) are left unsplit */
    protected static final int MAX_FIELDS = 13;

    protected final String[] fields;

    protected CrawlLogLine(String[] fields) {
        this.fields = fields;
    }

    /**
     * Splits a crawl.log line into columns.
     *
     * @return the parsed line, or null if it has too few columns to be a
     *         crawl.log line
     */
    public static CrawlLogLine parse(String line) {
        String[] fields = new String[MAX_FIELDS];
        int count = 0;
        int i = 0;
        int n = line.length();
        while (i < n && count < MAX_FIELDS) {
            while (i < n && line.charAt(i) == ' ') {
                i++;
            }
            if (i >= n) {
                break;
            }
            int start = i;
            if (count == MAX_FIELDS - 1) {
                i = n;
            } else {
                while (i < n && line.charAt(i) != ' ') {
                    i++;
                }
            }
            fields[count++] = line.substring(start, i);
        }
        if (count <= ANNOTATIONS) {
            return null;
        }
        return new CrawlLogLine(fields);
    }

    /** @return the fetch status, or null if not a number */
    public Long getStatus() {
        return parseLong(fields[STATUS]);
    }

    /** @return the content size in bytes, or null if not a number (e.g. '-') */
    public Long getSize() {
        return parseLong(fields[SIZE]);
    }

    /**
     * @return the fetch duration in milliseconds, or null if not logged
     *         (e.g. '-' when there was no fetch)
     */
    public Long getDuration() {
        String text = fields[FETCH_TIME];
        int plus = text.indexOf('+');
        return plus < 0 ? null : parseLong(text.substring(plus + 1));
    }

    protected static Long parseLong(String s) {
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return the hop path from the seed, e.g. 'LLE', or '-' for a seed
     */
    public String getHops() {
        return fields[HOP_PATH];
    }

    /**
     * @return number of hops from the seed, or null if the hop path can't be
     *         read. Paths longer than 50 hops are logged as the number of
     *         hops left out, '+' and the last 50 hops.
     */
    public Long getDepth() {
        String path = fields[HOP_PATH];
        if (path.equals("-")) {
            return 0L;
        }
        int plus = path.indexOf('+');
        if (plus < 0) {
            return (long) path.length();
        }
        Long leftOut = parseLong(path.substring(0, plus));
        return leftOut == null ? null : leftOut + path.length() - plus - 1;
    }

    public String getUrl() {
        return fields[URL];
    }

    /** @return the lowercased host of the url, or null if it has none */
    public String getHost() {
        return findHost(fields[URL]);
    }

    /** @return the url this one was discovered from, or '-' for a seed */
    public String getVia() {
        return fields[VIA];
    }

    /** @return the lowercased host of the via url, or null if it has none */
    public String getViaHost() {
        return findHost(fields[VIA]);
    }

    protected static String findHost(String url) {
        int start;
        int schemeEnd = url.indexOf(':');
        if (schemeEnd < 0) {
            return null;
        }
        if (url.startsWith("//", schemeEnd + 1)) {
            start = schemeEnd + 3;
        } else {
            start = schemeEnd + 1; // e.g. dns:example.com
        }
        int end = start;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) {
            end++;
        }
        int at = url.lastIndexOf('@', end - 1);
        if (at >= start) {
            start = at + 1;
        }
        int portColon = url.lastIndexOf(':', end - 1);
        if (portColon >= start && url.indexOf(']', start) < portColon) {
            end = portColon;
        }
        if (end <= start) {
            return null;
        }
        return url.substring(start, end).toLowerCase(Locale.ROOT);
    }

    public String getMimeType() {
        return fields[MIME];
    }

    /** @return the annotations, or an empty list if there are none */
    public List<String> getAnnotations() {
        String annotations = fields[ANNOTATIONS];
        if ("-".equals(annotations)) {
            return Collections.emptyList();
        }
        return Arrays.asList(annotations.split(",", -1));
    }
}
