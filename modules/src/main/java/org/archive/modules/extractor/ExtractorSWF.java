/*
 * Heritrix
 *
 * $Id$
 *
 * Created on March 19, 2004
 *
 * Copyright (C) 2003 Internet Archive.
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
 */

package org.archive.modules.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import org.archive.modules.ProcessorURI;

import com.anotherbigidea.flash.interfaces.SWFTagTypes;
import com.anotherbigidea.flash.readers.SWFReader;
import com.anotherbigidea.flash.readers.TagParser;
import com.anotherbigidea.flash.writers.SWFActionsImpl;
import com.anotherbigidea.io.InStream;

/**
 * Extracts URIs from SWF (flash/shockwave) files.
 * 
 * To test, here is a link to an swf that has links
 * embedded inside of it: http://www.hitspring.com/index.swf.
 *
 * @author Igor Ranitovic
 */
public class ExtractorSWF extends ContentExtractor {

    private static final long serialVersionUID = 3L;

    private static Logger logger =
        Logger.getLogger(ExtractorSWF.class.getName());

    protected AtomicLong linksExtracted = new AtomicLong(0);

    private static final int MAX_READ_SIZE = 1024 * 1024; // 1MB

    static final String JSSTRING = "javascript:";

    /**
     * @param name
     */
    public ExtractorSWF() {
    }

    
    @Override
    protected boolean shouldExtract(ProcessorURI uri) {
        String contentType = uri.getContentType();
        if (contentType == null) {
            return false;
        }
        if ((contentType.toLowerCase().indexOf("x-shockwave-flash") < 0)
                && (!uri.toString().toLowerCase().endsWith(".swf"))) {
            return false;
        }
        return true;
    }

    
    @Override
    protected boolean innerExtract(ProcessorURI curi) {
        InputStream documentStream = null;
        // Get the SWF file's content stream.
        try {
            documentStream = curi.getRecorder().getRecordedInput().
                getContentReplayInputStream();
            if (documentStream == null) {
                return false;
            }

            // Create SWF action that will add discoved URIs to CrawlURI
            // alist(s).
            CrawlUriSWFAction curiAction = new CrawlUriSWFAction(curi);

            // Overwrite parsing of specific tags that might have URIs.
            CustomSWFTags customTags = new CustomSWFTags(curiAction);
            // Get a SWFReader instance.
            SWFReader reader =
                new SWFReader(getTagParser(customTags), documentStream) {
                /**
                 * Override because a corrupt SWF file can cause us to try
                 * read lengths that are hundreds of megabytes in size
                 * causing us to OOME.
                 * 
                 * Below is copied from SWFReader parent class.
                 */
                public int readOneTag() throws IOException {
                    int header = mIn.readUI16();
                    int  type   = header >> 6;    //only want the top 10 bits
                    int  length = header & 0x3F;  //only want the bottom 6 bits
                    boolean longTag = (length == 0x3F);
                    if(longTag) {
                        length = (int)mIn.readUI32();
                    }
                    // Below test added for Heritrix use.
                    if (length > MAX_READ_SIZE) {
                        // skip to next, rather than throw IOException ending
                        // processing
                        mIn.skipBytes(length);
                        logger.info("oversized SWF tag (type=" + type
                                + ";length=" + length + ") skipped");
                    } else {
                        byte[] contents = mIn.read(length);
                        mConsumer.tag(type, longTag, contents);
                    }
                    return type;
                }
            };
            
            reader.readFile();
            linksExtracted.addAndGet(curiAction.getLinkCount());
            logger.fine(curi + " has " + curiAction.getLinkCount() + " links.");
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
        } finally {
            try {
                documentStream.close();
            } catch (IOException e) {
                curi.getNonFatalFailures().add(e);
            }
        }


        // Set flag to indicate that link extraction is completed.
        return true;
    }
    
    public String report() {
        StringBuffer ret = new StringBuffer();
        ret.append("Processor: org.archive.crawler.extractor.ExtractorSWF\n");
        ret.append("  Function:          Link extraction on Shockwave Flash " +
            "documents (.swf)\n");

        ret.append("  CrawlURIs handled: " + getURICount() + "\n");
        ret.append("  Links extracted:   " + linksExtracted + "\n\n");
        return ret.toString();
    }
    
    
    /**
     * Get a TagParser
     * 
     * A custom ExtractorTagParser which ignores all the big binary image/
     * sound/font types which don't carry URLs is used, to avoid the 
     * occasionally fatal (OutOfMemoryError) memory bloat caused by the
     * all-in-memory SWF library handling. 
     * 
     * @param customTags A custom tag parser.
     * @return An SWFReader.
     */
    private TagParser getTagParser(CustomSWFTags customTags) {
        return new ExtractorTagParser(customTags);
    }
    
    /**
     * TagParser customized to ignore SWFTags that 
     * will never contain extractable URIs. 
     */
    protected class ExtractorTagParser extends TagParser {

        protected ExtractorTagParser(SWFTagTypes tagtypes) {
            super(tagtypes);
        }

        protected void parseDefineBits(InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in bits
        }

        protected void parseDefineBitsJPEG3(InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in bits
        }

        protected void parseDefineBitsLossless(InStream in, int length, boolean hasAlpha) throws IOException {
            // DO NOTHING - no URLs to be found in bits
        }

        protected void parseDefineButtonSound(InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in sound
        }

        protected void parseDefineFont(InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in font
        }

        protected void parseDefineJPEG2(InStream in, int length) throws IOException {
            // DO NOTHING - no URLs to be found in jpeg
        }

        protected void parseDefineJPEGTables(InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in jpeg
        }

        protected void parseDefineShape(int type, InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in shape
        }

        protected void parseDefineSound(InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in sound
        }

        protected void parseFontInfo(InStream in, int length, boolean isFI2) throws IOException {
            // DO NOTHING - no URLs to be found in font info
        }

        protected void parseDefineFont2(InStream in) throws IOException {
            // DO NOTHING - no URLs to be found in bits
        }
    }
    
    
    /**
     * SWF action that handles discovered URIs.
     *
     * @author Igor Ranitovic
     */
    public class CrawlUriSWFAction extends SWFActionsImpl {
        
        ProcessorURI curi;
        
        private long linkCount;

        /**
         *
         * @param curi
         */
        public CrawlUriSWFAction(ProcessorURI curi) {
            assert (curi != null) : "CrawlURI should not be null";
            this.curi = curi;
            this.linkCount = 0;
        }

        /**
         * Overwrite handling of discovered URIs.
         *
         * @param url Discovered URL.
         * @param target Discovered target (currently not being used.)
         * @throws IOException
         */
        public void getURL(String url, String target)
        throws IOException {
            // I have done tests on a few tens of swf files and have not seen a need
            // to use 'target.' Most of the time 'target' is not set, or it is set
            // to '_self' or '_blank'.
            if (url.startsWith(JSSTRING)) {
                linkCount =+ ExtractorJS.considerStrings(ExtractorSWF.this, curi, url, 
                        false);
            } else {
                int max = getExtractorParameters().getMaxOutlinks();
                Link.addRelativeToVia(curi, max, url, LinkContext.EMBED_MISC, 
                        Hop.EMBED);
                linkCount++;
            }
        }
        
        /**
         * @return Total number of links extracted from a swf file.
         */
        public long getLinkCount() {
            return linkCount;
        }
    }
}
