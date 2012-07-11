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

import java.io.InputStream;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.io.ReplayInputStream;
import org.archive.io.SeekReader;
import org.archive.io.SeekReaderCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.ms.Doc;

/**
 *  This class allows the caller to extract href style links from word97-format word documents.
 *
 * @author Parker Thompson
 *
 */
public class ExtractorDOC extends ContentExtractor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;
    
    private static Pattern PATTERN = Pattern.compile("HYPERLINK.*?\"(.*?)\"");

    private static Logger logger =
        Logger.getLogger("org.archive.crawler.extractor.ExtractorDOC");

    /**
     * @param name
     */
    public ExtractorDOC() {
    }

    
    @Override
    protected boolean shouldExtract(CrawlURI uri) {
        String mimeType = uri.getContentType();
        if (mimeType == null) {
            return false;
        }
        return mimeType.toLowerCase().startsWith("application/msword");
    }
    
    
    /**
     *  Processes a word document and extracts any hyperlinks from it.
     *  This only extracts href style links, and does not examine the actual
     *  text for valid URIs.
     * @param curi CrawlURI to process.
     */
    protected boolean innerExtract(CrawlURI curi){
        int links = 0;
        InputStream contentStream = null;
        ReplayInputStream documentStream = null; 
        SeekReader docReader = null;

        // Get the doc as a repositionable reader
        try
        {
            contentStream = curi.getRecorder().getContentReplayInputStream();
            if (contentStream==null) {
                // TODO: note problem
                return false;
            }
            documentStream = new ReplayInputStream(contentStream);
           
            
            docReader = Doc.getText(documentStream);
        } catch(Exception e){
            curi.getNonFatalFailures().add(e);
            return false;
        } finally {
            IOUtils.closeQuietly(contentStream); 
        }

        CharSequence cs = new SeekReaderCharSequence(docReader, 0);
        Matcher m = PATTERN.matcher(cs);
        while (m.find()) {
            links++;
            addLink(curi, m.group(1));
        }
        documentStream.destroy(); 
        logger.fine(curi + " has " + links + " links.");
        return true;
    }
    
    
    private void addLink(CrawlURI curi, String hyperlink) {
        try {
            UURI dest = UURIFactory.getInstance(curi.getUURI(), hyperlink);
            LinkContext lc = LinkContext.NAVLINK_MISC;
            Link link = new Link(curi.getUURI(), dest, lc, Hop.NAVLINK);
            curi.getOutLinks().add(link);
        } catch (URIException e1) {
            logUriError(e1, curi.getUURI(), hyperlink);
        }
        numberOfLinksExtracted.incrementAndGet();   
    }
}
