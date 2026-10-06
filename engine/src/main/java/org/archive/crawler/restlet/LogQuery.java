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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.LongPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * A small search-engine style query language for filtering log lines in the
 * paged log viewer.
 *
 * <pre>
 * query := term (WS term)*            terms are ANDed
 * term  := ['-'] (field op value | value)
 * op    := ':'                        substring, case-insensitive, '*' wildcard
 *        | '='                        exact match
 *        | '~'                        Java regex (find)
 *        | ''                         before '&lt;' or '&gt;' in a numeric field
 * value := bareword | "quoted string"
 * </pre>
 *
 * A bare value searches the whole line. The {@code line} field also refers to
 * the whole line and is available for every log. The remaining fields refer to
 * columns of the crawl.log and are only available when filtering a crawl.log:
 * {@code status}, {@code size}, {@code duration}, {@code depth},
 * {@code hops}, {@code url}, {@code host}, {@code via}, {@code viahost},
 * {@code type} and {@code annot}. The numeric fields (status, size, duration
 * and depth) take a list of values, comparisons and ranges with either ':' or
 * '=', and don't support '~'. A comparison needs no operator: 'size&gt;1MB'.
 *
 * @see CrawlLogLine
 */
public class LogQuery {

    /** Thrown when a query cannot be parsed. Message is shown to the user. */
    public static class QueryException extends Exception {
        public QueryException(String message) {
            super(message);
        }
    }

    /** Thrown when matching exceeds the deadline (e.g. a slow regex). */
    public static class DeadlineExceededException extends RuntimeException {
        public DeadlineExceededException() {
            super("search time limit exceeded", null, false, false);
        }
    }

    protected enum Field {
        LINE, STATUS, SIZE, DURATION, DEPTH, HOPS, URL, HOST, VIA, VIAHOST, TYPE, ANNOT;

        boolean isNumeric() {
            return this == STATUS || this == SIZE || this == DURATION
                    || this == DEPTH;
        }

        static Field lookup(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /** A single parsed query term. */
    protected static class Term {
        final boolean negated;
        final Field field;
        /** matcher for string fields; null for numeric fields */
        final Pattern pattern;
        /** matcher for numeric fields; null for string fields */
        final LongPredicate numberPredicate;

        Term(boolean negated, Field field, char op, String value)
                throws QueryException {
            this.negated = negated;
            this.field = field;
            if (field.isNumeric()) {
                if (op == '~') {
                    throw new QueryException("'~' isn't supported for "
                            + field.name().toLowerCase(Locale.ROOT)
                            + "; use comparisons or ranges");
                }
                this.numberPredicate = parseNumbers(field, value);
                this.pattern = null;
            } else {
                this.numberPredicate = null;
                this.pattern = compile(field, op, value);
            }
        }
    }

    protected final List<Term> terms;
    protected final boolean needsFields;
    protected long deadlineNanos = Long.MAX_VALUE;

    protected LogQuery(List<Term> terms) {
        this.terms = terms;
        boolean nf = false;
        for (Term t : terms) {
            if (t.field != Field.LINE) {
                nf = true;
                break;
            }
        }
        this.needsFields = nf;
    }

    /**
     * Parse a query string.
     *
     * @param query the query text
     * @param crawlLog true if the lines being filtered are crawl.log lines, in
     *        which case the crawl.log fields are available
     * @throws QueryException if the query is invalid
     */
    public static LogQuery parse(String query, boolean crawlLog)
            throws QueryException {
        List<Term> terms = new ArrayList<>();
        int i = 0;
        int n = query.length();
        while (i < n) {
            char c = query.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }

            boolean negated = false;
            if (c == '-' && i + 1 < n
                    && !Character.isWhitespace(query.charAt(i + 1))) {
                negated = true;
                i++;
            }

            // try to read a field name followed by an operator, or by a
            // comparison for a numeric field; anything else, e.g. an unknown
            // field or a URI, is searched for as text
            Field field = Field.LINE;
            char op = ':';
            int j = i;
            while (j < n && Character.isLetter(query.charAt(j))) {
                j++;
            }
            char next = j < n ? query.charAt(j) : ' ';
            boolean compare = next == '<' || next == '>';
            if (j > i && (isOperator(next) || compare)) {
                String name = query.substring(i, j);
                Field f = Field.lookup(name);
                if (f != null && (!compare || f.isNumeric())) {
                    if (!crawlLog && f != Field.LINE) {
                        throw new QueryException("field '" + name
                                + "' is only available when viewing crawl.log");
                    }
                    field = f;
                    if (!compare) { // the comparison starts the value
                        op = next;
                        j++;
                    }
                    i = j;
                }
            }

            // read the value
            StringBuilder value = new StringBuilder();
            if (i < n && query.charAt(i) == '"') {
                i++;
                boolean closed = false;
                while (i < n) {
                    char vc = query.charAt(i);
                    if (vc == '\\' && i + 1 < n && query.charAt(i + 1) == '"') {
                        value.append('"');
                        i += 2;
                    } else if (vc == '"') {
                        i++;
                        closed = true;
                        break;
                    } else {
                        value.append(vc);
                        i++;
                    }
                }
                if (!closed) {
                    throw new QueryException("unclosed quote");
                }
            } else {
                while (i < n && !Character.isWhitespace(query.charAt(i))) {
                    value.append(query.charAt(i));
                    i++;
                }
            }

            if (value.isEmpty()) {
                if (field == Field.LINE && op == ':' && !negated) {
                    continue; // stray quotes ""
                }
                throw new QueryException("missing value in '"
                        + query.substring(Math.max(0, i - 1)).trim() + "'");
            }
            terms.add(new Term(negated, field, op, value.toString()));
        }
        return new LogQuery(Collections.unmodifiableList(terms));
    }

    protected static boolean isOperator(char c) {
        return c == ':' || c == '=' || c == '~';
    }

    /** Builds the pattern used to match a string field. */
    protected static Pattern compile(Field field, char op, String value)
            throws QueryException {
        switch (op) {
            case '~':
                try {
                    return Pattern.compile(value);
                } catch (PatternSyntaxException e) {
                    throw new QueryException("invalid regex '" + value + "': "
                            + e.getDescription());
                }
            case '=':
                if (field == Field.HOST || field == Field.VIAHOST
                        || field == Field.TYPE) {
                    return Pattern.compile("^" + Pattern.quote(value) + "$",
                            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                }
                return Pattern.compile("^" + Pattern.quote(value) + "$");
            default:
                int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
                if ((field == Field.HOST || field == Field.VIAHOST)
                        && value.indexOf('*') < 0) {
                    // the domain itself or any subdomain
                    String v = value.startsWith(".") ? value.substring(1) : value;
                    return Pattern.compile("(?:^|\\.)" + Pattern.quote(v) + "$",
                            flags);
                }
                if (field == Field.TYPE && value.indexOf(',') >= 0) {
                    List<String> alts = new ArrayList<>();
                    for (String alt : value.split(",")) {
                        if (!alt.isEmpty()) {
                            alts.add(wildcardToRegex(alt, false));
                        }
                    }
                    return Pattern.compile(String.join("|", alts), flags);
                }
                return Pattern.compile(wildcardToRegex(value, true), flags);
        }
    }

    /**
     * Convert a value containing '*' wildcards to a regex. Without wildcards
     * the value matches as a substring; with wildcards it must match the whole
     * text, so 'url:*.pdf' means the url ends with '.pdf'.
     */
    protected static String wildcardToRegex(String value, boolean standalone) {
        if (value.indexOf('*') < 0) {
            return Pattern.quote(value);
        }
        StringBuilder sb = new StringBuilder(standalone ? "^" : "(?:^");
        int start = 0;
        int star;
        while ((star = value.indexOf('*', start)) >= 0) {
            if (star > start) {
                sb.append(Pattern.quote(value.substring(start, star)));
            }
            sb.append(".*");
            start = star + 1;
        }
        if (start < value.length()) {
            sb.append(Pattern.quote(value.substring(start)));
        }
        sb.append(standalone ? "$" : "$)");
        return sb.toString();
    }

    /**
     * Parses the value of a numeric field: a comma separated list of exact
     * values, comparisons ('&lt;0', '&gt;1MB') or ranges ('500..599').
     * Status also takes classes ('4xx').
     */
    protected static LongPredicate parseNumbers(Field field, String value)
            throws QueryException {
        LongPredicate result = null;
        for (String part : value.split(",")) {
            if (part.isEmpty()) {
                continue;
            }
            LongPredicate p = parseNumberPart(field, value, part);
            result = result == null ? p : result.or(p);
        }
        if (result == null) {
            throw invalid(field, value);
        }
        return result;
    }

    private static final Pattern STATUS_CLASS = Pattern.compile("([1-9])xx",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPARE = Pattern.compile("(<=|>=|<|>)(.+)");
    private static final Pattern RANGE = Pattern.compile("(.+?)\\.\\.(.+)");

    protected static LongPredicate parseNumberPart(Field field, String value,
            String part) throws QueryException {
        Matcher m = STATUS_CLASS.matcher(part);
        if (field == Field.STATUS && m.matches()) {
            int lo = Integer.parseInt(m.group(1)) * 100;
            return s -> s >= lo && s <= lo + 99;
        }
        m = COMPARE.matcher(part);
        if (m.matches()) {
            long v = parseNumber(field, value, m.group(2));
            return switch (m.group(1)) {
                case "<" -> s -> s < v;
                case "<=" -> s -> s <= v;
                case ">" -> s -> s > v;
                default -> s -> s >= v;
            };
        }
        m = RANGE.matcher(part);
        if (m.matches()) {
            long lo = parseNumber(field, value, m.group(1));
            long hi = parseNumber(field, value, m.group(2));
            long min = Math.min(lo, hi);
            long max = Math.max(lo, hi);
            return s -> s >= min && s <= max;
        }
        long v = parseNumber(field, value, part);
        return s -> s == v;
    }

    /**
     * Sizes are bytes, optionally with a K, M, G or T unit, all powers of
     * 1024 to match how Heritrix displays sizes, optionally followed by B or
     * iB.
     */
    private static final Pattern SIZE_VALUE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)(?:([kmgt])(?:i?b)?|b)?", Pattern.CASE_INSENSITIVE);
    /** durations are milliseconds, optionally with a ms, s, m or h unit */
    private static final Pattern DURATION_VALUE = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)(ms|s|m|min|h)?", Pattern.CASE_INSENSITIVE);

    /** Parses a single value of a numeric field. */
    protected static long parseNumber(Field field, String value, String s)
            throws QueryException {
        long multiplier;
        Matcher m;
        switch (field) {
            case STATUS:
            case DEPTH:
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    throw invalid(field, value);
                }
            case SIZE:
                m = SIZE_VALUE.matcher(s);
                if (!m.matches()) {
                    throw invalid(field, value);
                }
                multiplier = m.group(2) == null ? 1 : 1L << (10
                        * ("KMGT".indexOf(m.group(2).toUpperCase(Locale.ROOT)) + 1));
                break;
            default: // DURATION
                m = DURATION_VALUE.matcher(s);
                if (!m.matches()) {
                    throw invalid(field, value);
                }
                String u = m.group(2) == null ? "ms"
                        : m.group(2).toLowerCase(Locale.ROOT);
                multiplier = u.equals("ms") ? 1 : u.equals("s") ? 1000
                        : u.equals("h") ? 3_600_000 : 60_000;
        }
        try {
            return new BigDecimal(m.group(1))
                    .multiply(BigDecimal.valueOf(multiplier))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();
        } catch (ArithmeticException e) {
            throw new QueryException(field.name().toLowerCase(Locale.ROOT)
                    + " too large '" + value + "'");
        }
    }

    protected static QueryException invalid(Field field, String value) {
        return new QueryException("invalid "
                + field.name().toLowerCase(Locale.ROOT) + " '" + value + "'");
    }

    /**
     * Sets the time after which matching throws
     * {@link DeadlineExceededException}, as a {@link System#nanoTime()} value.
     */
    public void setDeadline(long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
    }

    /**
     * Tests whether a line matches all terms of the query.
     *
     * @throws DeadlineExceededException if the deadline passes while matching
     */
    public boolean matches(String line) {
        // whole-line terms first, so most lines are rejected without parsing
        for (Term t : terms) {
            if (t.field == Field.LINE && find(t.pattern, line) == t.negated) {
                return false;
            }
        }
        if (!needsFields) {
            return true;
        }
        CrawlLogLine fields = CrawlLogLine.parse(line);
        for (Term t : terms) {
            if (t.field != Field.LINE && matchesField(t, fields) == t.negated) {
                return false;
            }
        }
        return true;
    }

    protected boolean matchesField(Term t, CrawlLogLine fields) {
        if (fields == null) {
            return false; // not a crawl.log line
        }
        if (t.numberPredicate != null) {
            Long number = t.field == Field.STATUS ? fields.getStatus()
                    : t.field == Field.SIZE ? fields.getSize()
                    : t.field == Field.DURATION ? fields.getDuration()
                    : fields.getDepth();
            return number != null && t.numberPredicate.test(number);
        }
        return switch (t.field) {
            case HOPS -> find(t.pattern, fields.getHops());
            case URL -> find(t.pattern, fields.getUrl());
            case HOST -> find(t.pattern, fields.getHost());
            case VIA -> find(t.pattern, fields.getVia());
            case VIAHOST -> find(t.pattern, fields.getViaHost());
            case TYPE -> find(t.pattern, fields.getMimeType());
            case ANNOT -> {
                for (String annotation : fields.getAnnotations()) {
                    if (find(t.pattern, annotation)) {
                        yield true;
                    }
                }
                yield false;
            }
            default -> throw new IllegalStateException();
        };
    }

    protected boolean find(Pattern pattern, String text) {
        if (text == null) {
            return false;
        }
        CharSequence input = deadlineNanos == Long.MAX_VALUE ? text
                : new DeadlineCharSequence(text, deadlineNanos);
        return pattern.matcher(input).find();
    }

    /**
     * Returns the character ranges of a line matched by the positive
     * whole-line terms of the query, for highlighting. Ranges are
     * [start, end) pairs, sorted and non-overlapping.
     */
    public List<int[]> highlights(String line) {
        List<int[]> spans = new ArrayList<>();
        for (Term t : terms) {
            if (t.negated || t.field != Field.LINE) {
                continue;
            }
            CharSequence input = deadlineNanos == Long.MAX_VALUE ? line
                    : new DeadlineCharSequence(line, deadlineNanos);
            Matcher m = t.pattern.matcher(input);
            while (m.find()) {
                if (m.end() > m.start()) {
                    spans.add(new int[] {m.start(), m.end()});
                }
            }
        }
        spans.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] span : spans) {
            int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && span[0] <= last[1]) {
                last[1] = Math.max(last[1], span[1]);
            } else {
                merged.add(span);
            }
        }
        return merged;
    }

    /**
     * CharSequence wrapper that aborts regex matching once a deadline passes,
     * guarding against catastrophic backtracking in user-supplied patterns.
     */
    protected static class DeadlineCharSequence implements CharSequence {
        protected final CharSequence inner;
        protected final long deadlineNanos;
        protected int counter;

        DeadlineCharSequence(CharSequence inner, long deadlineNanos) {
            this.inner = inner;
            this.deadlineNanos = deadlineNanos;
        }

        @Override
        public char charAt(int index) {
            if ((++counter & 0xFFFF) == 0 && System.nanoTime() > deadlineNanos) {
                throw new DeadlineExceededException();
            }
            return inner.charAt(index);
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(inner.subSequence(start, end),
                    deadlineNanos);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }
}
