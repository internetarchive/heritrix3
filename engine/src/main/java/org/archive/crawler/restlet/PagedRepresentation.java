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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.LongRange;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.util.FileUtils;
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
    
    // created when file is scanned
    /** text lines **/
    protected List<String> lines;
    /** position range [start-of-first-line, past-end-of-last-line] in file **/
    protected LongRange range;
    /** File **/ 
    protected File file; 
    // TODO: maybe, freeze length for more consistent display of growing files
    // (now, as length/%/bumper are written after lines retrieved, they 
    // sometimes are indicative the file has grown before the page is 
    // even rendered)
    
    public PagedRepresentation(FileRepresentation representation,
            EnhDirectoryResource resource, String pos, String lines,
            String reverse) {
        super(MediaType.TEXT_HTML);
        fileRepresentation = representation;
        dirResource = resource; 
        
        position = StringUtils.isBlank(pos) ? 0 : Long.parseLong(pos);
        lineCount = StringUtils.isBlank(lines) ? 128 : Integer.parseInt(lines);
        reversedOrder = "y".equals(reverse);
        
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
        this.file = fileRepresentation.getFile();
        this.lines = new LinkedList<String>();
        this.range = FileUtils.pagedLines(file, position, lineCount, lines, 128);
        // bounce against the front of the file: don't show runt (fewer
        // lines than requested) unless absolutely necessary)
        if(lines.size()<Math.abs(lineCount) 
                && range.getMinimum() == 0
                && range.getMaximum()<file.length()) {
            this.lines = new LinkedList<String>();
            this.range = FileUtils.pagedLines(file, 0, Math.abs(lineCount), lines, 128);
        }
        if(reversedOrder) {
            Collections.reverse(lines);
        }
    }
    
    /** 
     * Write the paged HTML. 
     * 
     * @see org.restlet.representation.Representation#write(java.io.Writer)
     */
    @Override
    public void write(Writer writer) throws IOException {
        loadLines();
        
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<b>Paged view:</b> "+file);
        emitControls(pw);

        Function<String, String> syntaxHighlighter = Function.identity();
        if (file.getName().equals("crawl.log")) {
            pw.println("<style>\n" +
                    ".status-neg { color: #777; }\n" +
                    ".status-2xx { color: #070; }\n" +
                    ".status-3xx { color: #007; }\n" +
                    ".status-4xx { color: #770; }\n" +
                    ".status-5xx { color: #770; }\n" +
                    "</style>");
            syntaxHighlighter = this::highlightCrawlLogLine;
        }

        pw.println("<pre>");
        emitBumper(pw, true);
        for(String line : lines) {
            pw.println(syntaxHighlighter.apply(StringEscapeUtils.escapeHtml4(line)));
        }
        emitBumper(pw, false);
        pw.println("</pre>");
        
        emitControls(pw); 
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
     * Performs basic syntax highlighting of a crawl log line. Assumes the line is already HTML escaped.
     */
    public String highlightCrawlLogLine(String line) {
        Matcher m = CRAWL_LOG_PATTERN.matcher(line);
        if (m.matches()) {
            String date = m.group(1);
            String status = m.group(2);
            String rest = m.group(3);

            int code = Integer.parseInt(status);
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
            if (reason == null) reason = HttpStatus.getMessage(code);

            return date + "<abbr class='" + clazz + "' title='" + reason + "'>" + status + "</abbr>" + rest;
        } else {
            return line;
        }
    }

    /**
     * Emit a "start" or "EOF" bumper as appropriate to prominently 
     * indicate if page borders start- or end- of-file. 
     * 
     * @param pw PrintWriter
     * @param atTop boolean, true if at top of page
     */
    protected void emitBumper(PrintWriter pw, boolean atTop) {
        if((!reversedOrder ^ atTop)&&(range.getMaximum()==file.length())) {
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
                    Math.min(file.length()-1, range.getMaximum()),Math.abs(lineCount),reversedOrder));
            pw.println("'>&lsaquo; later</a>");
            pw.println("bytes "
                    +range.getMaximum()
                    +"-"+range.getMinimum()
                    +"/"+file.length()
                    +" "
                    +(int)(100*(range.getMaximum()/(float)file.length()))
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
                    +"/"+file.length()
                    +" "
                    +(int)(100*(range.getMaximum()/(float)file.length()))
                    +"%");
            pw.print("<a href='");
            pw.print(getControlUri(
                    Math.min(file.length()-1, range.getMaximum()),Math.abs(lineCount),reversedOrder));
            pw.println("'>later &rsaquo;</a>");
            pw.print("<a href='");
            pw.print(getControlUri(file.length(),-Math.abs(lineCount),reversedOrder));
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
     * @return String URI appropriate to navigate to desired view
     */
    protected String getControlUri(long pos, int lines, boolean reverse) {
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
        Reference viewRef = dirResource.getRequest().getOriginalRef().clone(); 
        viewRef.setQuery(query.getQueryString());
        
        return viewRef.toString(); 
    }
}
