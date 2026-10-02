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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.LongRange;
import org.archive.util.ArchiveUtils;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.eclipse.jetty.http.HttpStatus;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.representation.CharacterRepresentation;
import org.restlet.representation.FileRepresentation;

/**
 * Representation wrapping a FileRepresentation, displaying its contents
 * in batches of lines at a time, with forward and backward navigation. 
 * 
 * @author gojomo
 */
public class PagedRepresentation extends CharacterRepresentation {
    // passed-in at construction
    /** wrapped FileRepresentation **/
    protected FileRepresentation fileRepresentation;
    /** wrapped EnhDirectoryResource; used to formulate self-links **/
    protected EnhDirectoryResource dirResource;
    
    /** position in file around which to fetch lines **/
    protected long position;
    /** desired line count; negative to go back from position; default 128 **/
    protected int lineCount;
    /** whether to display lines in reversed order (latest first) **/
    protected boolean reversedOrder; 
    /** filter query text; null for no filtering **/
    protected String queryText;
    /** whether to include the generations of the log rotated at checkpoints **/
    protected boolean includeRotated;
    
    // created when file is scanned
    /** text lines **/
    protected List<String> lines;
    /** position range [start-of-first-line, past-end-of-last-line] in file **/
    protected LongRange range;
    /** File **/ 
    protected File file; 
    /** the file, or all its generations if includeRotated **/
    protected LogSeries series;
    /** length of the series when it was opened **/
    protected long seriesLength;
    /** position in the series of the start of each line, in display order **/
    protected List<Long> lineStarts;
    /** parsed filter query; null if not filtering **/
    protected LogQuery query;
    /** true if a filtered scan stopped because it ran out of time **/
    protected boolean budgetExhausted;
    /** true if the last scan read forward through the file **/
    protected boolean scannedForward;

    /** maximum time to spend scanning for matches per request **/
    protected static final long SCAN_TIME_LIMIT_MS = 5 * 60 * 1000;
    /** maximum time to spend highlighting matches per request **/
    protected static final long HIGHLIGHT_TIME_LIMIT_MS = 500;
    // TODO: maybe, freeze length for more consistent display of growing files
    // (now, as length/%/bumper are written after lines retrieved, they 
    // sometimes are indicative the file has grown before the page is 
    // even rendered)
    
    public PagedRepresentation(FileRepresentation representation,
            EnhDirectoryResource resource, String pos, String lines,
            String reverse, String q, String all) {
        super(MediaType.TEXT_HTML);
        fileRepresentation = representation;
        dirResource = resource; 
        
        position = StringUtils.isBlank(pos) ? 0 : Long.parseLong(pos);
        lineCount = StringUtils.isBlank(lines) ? 128 : Integer.parseInt(lines);
        reversedOrder = "y".equals(reverse);
        queryText = StringUtils.isBlank(q) ? null : q.trim();
        includeRotated = "y".equals(all);
        
        // TODO: remove if not necessary in future?
        setCharacterSet(CharacterSet.UTF_8);
    }

    @Override
    public Reader getReader() throws IOException {
        int estimatedSize = (Math.abs(lineCount) * 128) + 500; 
        StringWriter writer = new StringWriter(estimatedSize);
        write(writer); 
        return new StringReader(writer.toString());
    }

    /**
     * Actually read the requested lines, and reverses if appropriate. 
     * 
     * If at file start, refuses to show fewer lines than are possible
     * ('bounces' against start). 
     * 
     * @throws IOException
     */
    protected void loadLines() throws IOException {
        loadLines(line -> true, Long.MAX_VALUE, null);
    }

    /**
     * Read the requested number of lines matching the query, scanning
     * forward or backward from the position for at most
     * {@link #SCAN_TIME_LIMIT_MS}.
     *
     * @param listener receives progress updates during the scan
     */
    protected void loadFilteredLines(FilteredLineScanner.ProgressListener listener)
            throws IOException {
        long deadline = System.nanoTime() + SCAN_TIME_LIMIT_MS * 1_000_000;
        query.setDeadline(deadline);
        loadLines(query::matches, deadline, listener);
    }

    protected void loadLines(Predicate<String> filter, long deadline,
            FilteredLineScanner.ProgressListener listener) throws IOException {
        FilteredLineScanner.Result result = FilteredLineScanner.scan(
                series, position, lineCount, filter, deadline, listener);
        scannedForward = lineCount >= 0;
        // bounce against the front of the file: don't show runt (fewer
        // lines than requested) unless absolutely necessary
        if (result.lines.size() < Math.abs(lineCount)
                && !result.budgetExhausted
                && result.range.getMinimum() == 0
                && result.range.getMaximum() < seriesLength) {
            result = FilteredLineScanner.scan(series, 0, Math.abs(lineCount),
                    filter, deadline, listener);
            scannedForward = true;
        }
        this.lines = new ArrayList<>(result.lines);
        this.lineStarts = new ArrayList<>(result.lineStarts);
        this.range = result.range;
        this.budgetExhausted = result.budgetExhausted;
        if (reversedOrder) {
            Collections.reverse(lines);
            Collections.reverse(lineStarts);
        }
    }

    protected boolean isCrawlLog() {
        return LogSeries.baseName(file.getName()).equals("crawl.log");
    }

    /** 
     * Write the paged HTML. 
     * 
     * @see org.restlet.representation.Representation#write(java.io.Writer)
     */
    @Override
    public void write(Writer writer) throws IOException {
        this.file = fileRepresentation.getFile();
        
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<b>Paged view:</b> "+StringEscapeUtils.escapeHtml4(file.toString()));
        series = includeRotated ? LogSeries.all(file) : LogSeries.single(file);
        try {
            seriesLength = series.length();
            emitSeriesInfo(pw);
            writeBody(pw);
        } finally {
            series.close();
        }
    }

    protected void writeBody(PrintWriter pw) throws IOException {
        emitSearchForm(pw);
        if (queryText != null) {
            try {
                query = LogQuery.parse(queryText, isCrawlLog());
            } catch (LogQuery.QueryException e) {
                pw.println("<p class='queryError' style='color:#a00'><b>Invalid search:</b> "
                        + StringEscapeUtils.escapeHtml4(e.getMessage()) + "</p>");
                return;
            }
            SearchProgress progress = new SearchProgress(pw);
            loadFilteredLines(progress);
            progress.finish();
            emitSearchSummary(pw);
        } else {
            loadLines();
        }
        emitControls(pw);

        if (isCrawlLog()) {
            pw.println("<style>\n" +
                    ".status-neg { color: #777; }\n" +
                    ".status-2xx { color: #070; }\n" +
                    ".status-3xx { color: #007; }\n" +
                    ".status-4xx { color: #770; }\n" +
                    ".status-5xx { color: #770; }\n" +
                    "</style>");
        }

        if (query != null) {
            query.setDeadline(System.nanoTime() + HIGHLIGHT_TIME_LIMIT_MS * 1_000_000);
        }
        pw.println("<pre>");
        emitBumper(pw, true);
        int fileIndex = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (series.getFiles().size() > 1) {
                // label each run of lines with the file they come from
                int index = series.fileIndexAt(lineStarts.get(i));
                if (index != fileIndex) {
                    emitFileMarker(pw, series.getFiles().get(index));
                    fileIndex = index;
                }
            }
            pw.println(renderLine(lines.get(i)));
        }
        emitBumper(pw, false);
        pw.println("</pre>");
        
        emitControls(pw); 
    }

    /**
     * Emit the files included in the view, with a link to toggle including
     * the generations of the log rotated at checkpoints.
     */
    protected void emitSeriesInfo(PrintWriter pw) {
        if (includeRotated) {
            List<File> files = series.getFiles();
            pw.print("<div class='seriesInfo'>Including all " + files.size()
                    + " generations of this log:");
            for (File f : files) {
                pw.print(" <code>" + StringEscapeUtils.escapeHtml4(f.getName())
                        + "</code>");
            }
            pw.println(". <a href='" + getControlUri(toFilePosition(position),
                    lineCount, reversedOrder, queryText, false)
                    + "'>view this file only</a></div>");
        } else {
            int rotated = LogSeries.countRotated(file);
            if (rotated > 0) {
                pw.println("<div class='seriesInfo'><a href='"
                        + getControlUri(toSeriesPosition(position), lineCount,
                                reversedOrder, queryText, true)
                        + "'>include " + rotated + " checkpoint "
                        + (rotated == 1 ? "log" : "logs") + "</a></div>");
            }
        }
    }

    /**
     * Converts a position in the file to the corresponding position in the
     * series of all its generations.
     */
    protected long toSeriesPosition(long pos) {
        if (pos < 0) {
            return pos;
        }
        long start = LogSeries.startOfInAll(file);
        return start < 0 ? 0 : start + pos;
    }

    /**
     * Converts a position in the series to the corresponding position in
     * the file, clamped to the file.
     */
    protected long toFilePosition(long pos) {
        if (pos < 0) {
            return pos;
        }
        long start = LogSeries.startOfInAll(file);
        if (start < 0) {
            return 0;
        }
        return Math.max(0, pos - start);
    }

    /** Emit a label for the lines that follow, naming their file. */
    protected void emitFileMarker(PrintWriter pw, File f) {
        // a block element already ends the line, so no newline after it
        pw.print("<span class='fileMarker' style='display:block;"
                + " background:#eee; color:#444; border-top:1px solid #aaa'>"
                + "&#x2500;&#x2500; " + StringEscapeUtils.escapeHtml4(f.getName())
                + " &#x2500;&#x2500;</span>");
    }

    /**
     * Emit the search box.
     */
    protected void emitSearchForm(PrintWriter pw) {
        Reference action = dirResource.getRequest().getOriginalRef().clone();
        action.setQuery(null);
        String placeholder = isCrawlLog()
                ? "status:4xx host:example.com -type:image"
                : "text to find";
        pw.println("<form id='search' method='get' style='position:relative' action='"
                + StringEscapeUtils.escapeHtml4(action.toString()) + "'>");
        pw.println("<input type='hidden' name='format' value='paged'>");
        if (includeRotated) {
            pw.println("<input type='hidden' name='all' value='y'>");
        }
        // a new search starts from the start, or from the end if reversed
        int absLines = Math.max(1, Math.abs(lineCount));
        if (reversedOrder) {
            pw.println("<input type='hidden' name='pos' value='-1'>");
            pw.println("<input type='hidden' name='lines' value='-" + absLines + "'>");
            pw.println("<input type='hidden' name='reverse' value='y'>");
        } else if (absLines != 128) {
            pw.println("<input type='hidden' name='lines' value='" + absLines + "'>");
        }
        pw.println("<input type='search' name='q' size='60' placeholder='"
                + StringEscapeUtils.escapeHtml4(placeholder) + "' value='"
                + StringEscapeUtils.escapeHtml4(queryText == null ? "" : queryText)
                + "'>");
        pw.println("<input type='submit' value='Filter'>");
        if (queryText != null) {
            pw.println("<a href='"
                    + getControlUri(position, lineCount, reversedOrder, null)
                    + "'>clear</a>");
        }
        emitSearchHelp(pw);
        pw.println("</form>");
    }

    /**
     * Emit a summary of the matches found, with a link to continue
     * searching if the scan ran out of time.
     */
    protected void emitSearchSummary(PrintWriter pw) {
        pw.print("<div class='searchSummary'>" + lines.size()
                + " matching lines in bytes " + range.getMinimum()
                + "-" + range.getMaximum());
        if (budgetExhausted) {
            String next = scannedForward
                    ? getControlUri(range.getMaximum(), Math.abs(lineCount), reversedOrder)
                    : getControlUri(Math.max(0, range.getMinimum() - 1),
                            -Math.abs(lineCount), reversedOrder);
            pw.print("; search stopped at the time limit. <a href='" + next
                    + "'>continue searching &rsaquo;</a>");
        }
        pw.println("</div>");
    }

    /**
     * Streams search progress to the browser while the scan runs, as script
     * tags updating a progress bar. Flushing also detects a closed
     * connection, which aborts the scan.
     */
    protected static class SearchProgress
            implements FilteredLineScanner.ProgressListener {
        protected final PrintWriter pw;
        protected boolean started;

        protected SearchProgress(PrintWriter pw) {
            this.pw = pw;
        }

        @Override
        public void progress(long bytesScanned, long bytesToScan, int matches)
                throws IOException {
            if (!started) {
                pw.println("<div id='searchProgress'><progress max='1000'></progress> <span></span></div>");
                pw.println("<script>function searchProgress(value, text) {"
                        + " var e = document.getElementById('searchProgress');"
                        + " e.firstChild.value = value; e.lastChild.textContent = text; }</script>");
                started = true;
            }
            long permille = bytesToScan > 0 ? 1000 * bytesScanned / bytesToScan : 0;
            pw.println("<script>searchProgress(" + permille + ", 'Searching: "
                    + ArchiveUtils.formatBytesForDisplay(bytesScanned) + " of "
                    + ArchiveUtils.formatBytesForDisplay(bytesToScan) + ", "
                    + matches + " matches')</script>");
            if (pw.checkError()) { // flushes
                throw new IOException("client disconnected");
            }
        }

        protected void finish() {
            if (started) {
                pw.println("<script>document.getElementById('searchProgress').remove()</script>");
            }
        }
    }

    protected void emitSearchHelp(PrintWriter pw) {
        // the help drops down below the form, over the log, rather than
        // pushing the log down
        pw.println("<details style='display:inline-block; vertical-align:top'>"
                + "<summary style='cursor:pointer'>help</summary>"
                + "<div style='position:absolute; z-index:10; top:100%; left:0;"
                + " max-width:min(60em, 95vw);"
                + " font-size:smaller; background:#fff; color:#000;"
                + " border:1px solid #999; padding:0.5em 0.75em;"
                + " box-shadow:0 2px 6px rgba(0,0,0,0.25)'>");
        pw.println("Terms are separated by spaces and must all match. "
                + "Prefix a term with <code>-</code> to exclude matches. "
                + "Use <code>\"double quotes\"</code> for values containing spaces.");
        pw.println("<table>");
        pw.println("<tr><td><code>word</code></td><td>line contains text (case-insensitive)</td></tr>");
        pw.println("<tr><td><code>field:value</code></td><td>field contains value "
                + "(case-insensitive); <code>*</code> is a wildcard matching the whole field, "
                + "e.g. <code>url:*.pdf</code></td></tr>");
        pw.println("<tr><td><code>field=value</code></td><td>field is exactly value</td></tr>");
        pw.println("<tr><td><code>field~regex</code></td><td>field matches Java regex, "
                + "e.g. <code>url~^https?://[^/]+/$</code> "
                + "(not for status, size, duration or depth)</td></tr>");
        pw.println("<tr><td><code>line</code></td><td>the whole line</td></tr>");
        if (isCrawlLog()) {
            pw.println("<tr><td><code>status</code></td><td>fetch status: "
                    + "<code>status:404</code>, <code>status:4xx</code>, <code>status&lt;0</code>, "
                    + "<code>status:500..599</code>, <code>status:404,410</code></td></tr>");
            pw.println("<tr><td><code>size</code></td><td>content size in bytes, "
                    + "or with a unit (K, M, G, T, all powers of 1024): "
                    + "<code>size&gt;1MB</code>, <code>size&lt;=512</code>, "
                    + "<code>size:100KB..10MB</code>, <code>size:0</code></td></tr>");
            pw.println("<tr><td><code>depth</code></td><td>number of hops from the seed: "
                    + "<code>depth:0</code> for seeds, <code>depth&gt;5</code>, "
                    + "<code>depth:1..3</code></td></tr>");
            pw.println("<tr><td><code>hops</code></td><td>the hop path from the seed, "
                    + "e.g. <code>hops:*E</code> for URIs reached through an embed</td></tr>");
            pw.println("<tr><td><code>duration</code></td><td>fetch duration in "
                    + "milliseconds, or with a unit (ms, s, m, h): "
                    + "<code>duration&gt;10s</code>, <code>duration:500..2000</code></td></tr>");
            pw.println("<tr><td><code>url</code></td><td>the URI</td></tr>");
            pw.println("<tr><td><code>host</code></td><td>host of the URI; "
                    + "<code>host:example.com</code> includes subdomains</td></tr>");
            pw.println("<tr><td><code>via</code>, <code>viahost</code></td><td>the URI "
                    + "it was discovered from, and its host, as for url and host</td></tr>");
            pw.println("<tr><td><code>type</code></td><td>mime type, "
                    + "e.g. <code>type:html</code>, <code>type:pdf,image</code></td></tr>");
            pw.println("<tr><td><code>annot</code></td><td>an annotation, "
                    + "e.g. <code>annot:duplicate</code></td></tr>");
        }
        pw.println("</table></div></details>");
    }

    /**
     * Render a line as HTML, highlighting query matches and, for crawl.log,
     * the fetch status.
     */
    protected String renderLine(String line) {
        List<int[]> marks = Collections.emptyList();
        if (query != null) {
            try {
                marks = query.highlights(line);
            } catch (LogQuery.DeadlineExceededException e) {
                // leave this line unhighlighted; the highlight time limit
                // has passed and matching it would take a long time
            }
        }
        StringBuilder sb = new StringBuilder(line.length() + 64);
        Matcher m = CRAWL_LOG_PATTERN.matcher(line);
        String statusTag = null;
        if (isCrawlLog() && m.matches()) {
            try {
                statusTag = statusAbbrTag(Integer.parseInt(m.group(2)));
            } catch (NumberFormatException e) {
                // too long to be a status
            }
        }
        if (statusTag != null) {
            // marks are clipped to each part, so <mark> nests inside <abbr>
            appendMarked(sb, line, 0, m.end(1), marks);
            sb.append(statusTag);
            appendMarked(sb, line, m.start(2), m.end(2), marks);
            sb.append("</abbr>");
            appendMarked(sb, line, m.start(3), line.length(), marks);
        } else {
            appendMarked(sb, line, 0, line.length(), marks);
        }
        return sb.toString();
    }

    /**
     * Appends line[from, to) HTML escaped, wrapping the parts of it within
     * the given sorted, non-overlapping [start, end) ranges in mark tags.
     */
    protected static void appendMarked(StringBuilder sb, String line, int from,
            int to, List<int[]> marks) {
        int pos = from;
        for (int[] mark : marks) {
            int start = Math.max(mark[0], pos);
            int end = Math.min(mark[1], to);
            if (start < end) {
                sb.append(StringEscapeUtils.escapeHtml4(line.substring(pos, start)))
                        .append("<mark>")
                        .append(StringEscapeUtils.escapeHtml4(line.substring(start, end)))
                        .append("</mark>");
                pos = end;
            }
        }
        sb.append(StringEscapeUtils.escapeHtml4(line.substring(pos, to)));
    }

    /**
     * Map of fetch status codes to names.
     */
    private static final Map<Integer, String> FETCH_STATUS_NAMES = new HashMap<>();

    static {
        for (Field field : FetchStatusCodes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (!field.getType().equals(int.class)) continue;
            try {
                FETCH_STATUS_NAMES.put((Integer)field.get(null), field.getName());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final Pattern CRAWL_LOG_PATTERN = Pattern.compile("([^ ]+ +)(-?[0-9]+)( +.*)");

    /**
     * @return opening abbr tag for a crawl.log status code, with a class for
     *         coloring and the status name as its title
     */
    protected String statusAbbrTag(int code) {
        String clazz = "";
        if (code < 0) {
            clazz = "status-neg";
        } else if (code >= 200 && code <= 299) {
            clazz = "status-2xx";
        } else if (code >= 300 && code <= 399) {
            clazz = "status-3xx";
        } else if (code >= 400 && code <= 499) {
            clazz = "status-4xx";
        } else if (code >= 500 && code <= 599) {
            clazz = "status-5xx";
        }

        String reason = FETCH_STATUS_NAMES.get(code);
        // HttpStatus throws for codes outside its table, e.g. 999
        if (reason == null) reason = code >= 0 && code <= HttpStatus.MAX_CODE
                ? HttpStatus.getMessage(code) : Integer.toString(code);

        return "<abbr class='" + clazz + "' title='"
                + StringEscapeUtils.escapeHtml4(reason) + "'>";
    }

    /**
     * Emit a "start" or "EOF" bumper as appropriate to prominently 
     * indicate if page borders start- or end- of-file. 
     * 
     * @param pw PrintWriter
     * @param atTop boolean, true if at top of page
     */
    protected void emitBumper(PrintWriter pw, boolean atTop) {
        if((!reversedOrder ^ atTop)&&(range.getMaximum()==seriesLength)) {
            pw.println("<span class='endBumper' style='font-weight:bold; color:white; background-color:#400'>&laquo;EOF&raquo;</span>");
            return; 
        }
        if((reversedOrder ^ atTop)&&(range.getMinimum()==0)) {
            pw.println("<span class='startBumper' style='font-weight:bold; color:white; background-color:#040'>&laquo;START&raquo;</span>");
        }
    }

    /**
     * Emit the navigational controls. 
     * 
     * TODO: ugh! templatize, reduce duplication as possible
     * @param pw PrintWriter
     */
    protected void emitControls(PrintWriter pw) {
        pw.println("<table id='controls' width='100%'><tr>");

        if(reversedOrder) {
            pw.print("<td style='text-align:left'>");
            pw.print("<a href='");
            pw.print(getControlUri(-1,-Math.abs(lineCount),reversedOrder));
            pw.println("'>&laquo; end</a>");
            pw.print("<a href='");
            pw.print(getControlUri(
                    Math.min(seriesLength-1, range.getMaximum()),Math.abs(lineCount),reversedOrder));
            pw.println("'>&lsaquo; later</a>");
            pw.println("bytes "
                    +range.getMaximum()
                    +"-"+range.getMinimum()
                    +"/"+seriesLength
                    +" "
                    +(int)(100*(range.getMaximum()/(float)seriesLength))
                    +"%");
            pw.print("<a href='");
            pw.print(getControlUri(
                    Math.max(0, range.getMinimum()-1),-Math.abs(lineCount),reversedOrder));
            pw.println("'>earlier &rsaquo;</a>");
            pw.print("<a href='");
            pw.print(getControlUri(0,Math.abs(lineCount),reversedOrder));
            pw.println("'>start &raquo;</a>");
            pw.println("</td>");
            
            pw.println("<td style='text-align:right'>");
            pw.println("<a href='"+getControlUri(position,lineCount,false)+"'>forward</a>");
            pw.println("| <b>reversed</b>"); 
        } else {
            pw.print("<td style='text-align:left'>");
            pw.print("<a href='");
            pw.print(getControlUri(0,Math.abs(lineCount),reversedOrder));
            pw.println("'>&laquo; start</a>");
            pw.print("<a href='");pw.print(getControlUri(
                    Math.max(0, range.getMinimum()-1),-Math.abs(lineCount),reversedOrder));
            pw.println("'>&lsaquo; earlier</a>");
            pw.println("bytes "
                    +range.getMinimum()
                    +"-"+range.getMaximum()
                    +"/"+seriesLength
                    +" "
                    +(int)(100*(range.getMaximum()/(float)seriesLength))
                    +"%");
            pw.print("<a href='");
            pw.print(getControlUri(
                    Math.min(seriesLength-1, range.getMaximum()),Math.abs(lineCount),reversedOrder));
            pw.println("'>later &rsaquo;</a>");
            pw.print("<a href='");
            pw.print(getControlUri(seriesLength,-Math.abs(lineCount),reversedOrder));
            pw.println("'>end &raquo;</a>");
            pw.println("</td>");
            
            pw.println("<td style='text-align:right'><b>forward</b>"); 
            pw.println("| <a href='"+getControlUri(position,lineCount,true)+"'>reversed</a>"); 
        }
                
        pw.print("<a href='");
        pw.println(getControlUri(position,lineCount*2,reversedOrder));
        pw.println("'>&nbsp;+&nbsp;</a>"); 
        pw.println(lines.size());
        pw.print("<a href='"+getControlUri(position,lineCount/2,reversedOrder));
        pw.println("'>&nbsp;-&nbsp;</a> lines</td>"); 
        
        pw.println("</tr></table>");        
    }

    /**
     * Construct navigational URI for given parameters.
     * 
     * @param pos desired position in file
     * @param lines desired signed line count
     * @param reverse if line ordering should be displayed in reverse
     * @return String URI appropriate to navigate to desired view, HTML escaped
     */
    protected String getControlUri(long pos, int lines, boolean reverse) {
        return getControlUri(pos, lines, reverse, queryText);
    }

    /**
     * Construct navigational URI for given parameters.
     *
     * @param q filter query, or null for none
     */
    protected String getControlUri(long pos, int lines, boolean reverse,
            String q) {
        return getControlUri(pos, lines, reverse, q, includeRotated);
    }

    /**
     * Construct navigational URI for given parameters.
     *
     * @param q filter query, or null for none
     * @param all whether to include the rotated generations of the log
     */
    protected String getControlUri(long pos, int lines, boolean reverse,
            String q, boolean all) {
        Form query = new Form(); 
        query.add("format","paged");
        if(pos!=0) {
            query.add("pos", Long.toString(pos));
        }
        if(lines!=128) {
            if(Math.abs(lines)<1) {
                lines = 1;
            }
            query.add("lines",Integer.toString(lines));
        }
        if(reverse) {
            query.add("reverse","y");
        }
        if(q != null) {
            query.add("q", q);
        }
        if(all) {
            query.add("all", "y");
        }
        Reference viewRef = dirResource.getRequest().getOriginalRef().clone(); 
        viewRef.setQuery(query.getQueryString());
        
        return StringEscapeUtils.escapeHtml4(viewRef.toString()); 
    }
}
