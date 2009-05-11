/* Copyright (C) 2003 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Created on Jul 11, 2003
 *
 */
package org.archive.modules.extractor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.io.SinkHandlerLogThread;
import org.archive.modules.ProcessorURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

/** Allows the caller to process a CrawlURI representing a PDF
 *  for the purpose of extracting URIs
 *
 * @author Parker Thompson
 *
 */
public class ExtractorPDF extends ContentExtractor {

    private static final long serialVersionUID = 3L;

    private static final Logger LOGGER =
        Logger.getLogger(ExtractorPDF.class.getName());

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

    final private AtomicLong numberOfLinksExtracted = new AtomicLong(0);

    public ExtractorPDF() {
    }
    
    @Override
    protected boolean shouldExtract(ProcessorURI uri) {
        long max = getMaxSizeToParse();
        if (uri.getRecorder().getRecordedInput().getSize() > max) {
            return false;
        }

        String ct = uri.getContentType();
        return (ct != null) && (ct.startsWith("application/pdf"));
    }
    
    
    protected boolean innerExtract(ProcessorURI curi){
        File tempFile;

        int sn;
	Thread thread = Thread.currentThread();
        if (thread instanceof SinkHandlerLogThread) {
            sn = ((SinkHandlerLogThread)thread).getSerialNumber();
        } else {
            sn = System.identityHashCode(thread);
        }
        try {
            tempFile = File.createTempFile("tt" + sn , "tmp.pdf");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }

        PDFParser parser;
        ArrayList<String> uris;
        try {
            curi.getRecorder().getRecordedInput().
                copyContentBodyTo(tempFile);
            parser = new PDFParser(tempFile.getAbsolutePath());
            uris = parser.extractURIs();
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            return false;
        } catch (RuntimeException e) {
            // Truncated/corrupt  PDFs may generate ClassCast exceptions, or
            // other problems
            curi.getNonFatalFailures().add(e);
            return false;
        } finally {
            tempFile.delete();
        }
        
        if (uris == null) {
            return true;
        }

        for (String uri: uris) {
            try {
                UURI src = curi.getUURI();
                UURI dest = UURIFactory.getInstance(uri);
                LinkContext lc = LinkContext.NAVLINK_MISC;
                Hop hop = Hop.NAVLINK;
                Link out = new Link(src, dest, lc, hop);
                curi.getOutLinks().add(out);
            } catch (URIException e1) {
                // There may not be a controller (e.g. If we're being run
                // by the extractor tool).
                logUriError(e1, curi.getUURI(), uri);
            }
        }
        
        numberOfLinksExtracted.addAndGet(uris.size());

        LOGGER.fine(curi+" has "+uris.size()+" links.");
        // Set flag to indicate that link extraction is completed.
        return true;
    }

    /**
     * Provide a human-readable textual summary of this Processor's state.
     *
     * @see org.archive.crawler.framework.Processor#report()
     */
    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: org.archive.crawler.extractor.ExtractorPDF\n");
        ret.append("  Function:          Link extraction on PDF documents\n");
        ret.append("  CrawlURIs handled: " + getURICount() + "\n");
        ret.append("  Links extracted:   " + numberOfLinksExtracted + "\n\n");

        return ret.toString();
    }
}
