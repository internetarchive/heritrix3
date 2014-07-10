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
package org.archive.modules.extractor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.SimpleTextExtractionStrategy;
import com.itextpdf.text.pdf.parser.TextExtractionStrategy;

/**
 * PDF Content Extractor. This will parse the text content of a PDF and apply a
 * regex to search for links within the body of the text.
 * 
 * Requires itextpdf jar: http://repo1.maven.org/maven2/com/itextpdf/itextpdf/5.5.0/itextpdf-5.5.0.jar
 * 
 * @contributor adam
 */
public class ExtractorPDFContent extends ContentExtractor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    private static final Logger LOGGER =
        Logger.getLogger(ExtractorPDFContent.class.getName());
    
    public static final Pattern URLPattern = Pattern.compile(
            "(?i)\\(?(https?):\\/\\/"+                                                  // protocol
            "(([a-z0-9$_\\.\\+!\\*\\'\\(\\),;\\?&=-]|%[0-9a-f]{2})+"+           // username
            "(:([a-z0-9$_\\.\\+!\\*\\'\\(\\),;\\?&=-]|%[0-9a-f]{2})+)?"+        // password
            "@)?(?"+                                                            // auth requires @
            ")((([a-z0-9]\\.|[a-z0-9][a-z0-9-]*[a-z0-9]\\.)*"+                  // domain segments AND
            "[a-z][a-z0-9-]*[a-z0-9]"+                                          // top level domain  OR
            "|((\\d|[1-9]\\d|1\\d{2}|2[0-4][0-9]|25[0-5])\\.){3}"+
            "(\\d|[1-9]\\d|1\\d{2}|2[0-4][0-9]|25[0-5])"+                       // IP address
            ")(:\\d+)?)"+                                                       // port
            "(((\\/+([a-z0-9$_\\.\\+!\\*\\'\\(\\),;:@&=-]|%[0-9a-f]{2})*)*"+    // path
            "(\\?([a-z0-9$_\\.\\+!\\*\\'\\(\\),;:@&=-]|%[0-9a-f]{2})*)?)?)?"+   // query string
            "(\\n(?!http://)"+                                                             // possible newline (seems to happen in pdfs)
            "((\\/)?([a-z0-9$_\\.\\+!\\*\\'\\(\\),;:@&=-]|%[0-9a-f]{2})*)*"+    // continue possible path
            "(\\?([a-z0-9$_\\.\\+!\\*\\'\\(\\),;:@&=-]|%[0-9a-f]{2})*)?"+       // or possible query
            ")?");
    
    /**
     * The maximum size of PDF files to consider.  PDFs larger than this
     * maximum will not be searched for links.
     */
    {
        setMaxSizeToParse(10*1024*1024L); // 10MB
    }
    public long getMaxSizeToParse() {
        return (Long) kp.get("maxSizeToParse");
    }
    public void setMaxSizeToParse(long threshold) {
        kp.put("maxSizeToParse",threshold);
    }
    
    
    public ExtractorPDFContent() {
    }
    
    protected boolean innerExtract(CrawlURI curi){
        PdfReader documentReader;
        ArrayList<String> uris = new ArrayList<String>();
        
        try {
            documentReader = new PdfReader(curi.getRecorder().getContentReplayInputStream());

            for(int i=1; i<= documentReader.getNumberOfPages(); i++) { //Page numbers start at 1
                String pageParseText = extractPageText(documentReader,i);
                Matcher matcher = URLPattern.matcher(pageParseText);

                while(matcher.find()) {
                    String prospectiveURL = pageParseText.substring(matcher.start(),matcher.end()).trim();

                    //handle URLs wrapped in parentheses
                    if(prospectiveURL.startsWith("(")) {
                        prospectiveURL=prospectiveURL.substring(1,prospectiveURL.length());
                        if(prospectiveURL.endsWith(")"))
                            prospectiveURL=prospectiveURL.substring(0,prospectiveURL.length()-1);
                    }

                    uris.add(prospectiveURL);
                    
                    //parsetext URLs tend to end in a '.' if they are in a sentence, queue without trailing '.'
                    if(prospectiveURL.endsWith(".") && prospectiveURL.length()>2)
                        uris.add(prospectiveURL.substring(0, prospectiveURL.length()-1));
                    
                    //Full regex allows newlines which seem to be common, also add match without newline in case we are wrong
                    if(matcher.group(19)!=null) {
                        String alternateURL = matcher.group(1)+"://"+(matcher.group(2)!=null?matcher.group(2):"")+matcher.group(6)+matcher.group(13);

                        //Again, handle URLs wrapped in parentheses
                        if(prospectiveURL.startsWith("(") && alternateURL.endsWith(")"))
                            alternateURL=alternateURL.substring(0,alternateURL.length()-1);

                        uris.add(alternateURL);
                    }
                }
            }

        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            return false;
        } catch (RuntimeException e) {
            curi.getNonFatalFailures().add(e);
            return false;
        } 
        
        if (uris.size()<1) {
            return true;
        }

        for (String uri: uris) {
            try {
                LinkContext lc = LinkContext.NAVLINK_MISC;
                Hop hop = Hop.NAVLINK;
                CrawlURI out = curi.createCrawlURI(uri, lc, hop);
                curi.getOutLinks().add(out);
            } catch (URIException e1) {
                logUriError(e1, curi.getUURI(), uri);
            }
        }
        
        numberOfLinksExtracted.addAndGet(uris.size());

        LOGGER.fine(curi+" has "+uris.size()+" links.");
        // Set flag to indicate that link extraction is completed.
        return true;
    }
    
    public String extractPageText(PdfReader documentReader, int pageNum){
        String content ="";
        PdfReaderContentParser parser = new PdfReaderContentParser(documentReader);
        TextExtractionStrategy strat;
        try {
            strat = parser.processContent(pageNum, new SimpleTextExtractionStrategy());
            content = strat.getResultantText();
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to parse pdf text in "
                    + Thread.currentThread().getName(), e);
        }
        return content;
    }
    @Override
    protected boolean shouldExtract(CrawlURI uri) {
        long max = getMaxSizeToParse();
        if (uri.getRecorder().getRecordedInput().getSize() > max) {
            return false;
        }

        String ct = uri.getContentType();
        return (ct != null) && (ct.startsWith("application/pdf"));
    }
}
