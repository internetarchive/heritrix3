/* Copyright (C) 2006 Internet Archive.
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
 * ContentExtractor.java
 * Created on October 5, 2006
 *
 * $Header$
 */
package org.archive.modules.extractor;

import org.archive.modules.ProcessorURI;


/**
 * Extracts link from the fetched content of a URI, as opposed to its headers.
 * 
 * @author pjack
 */
public abstract class ContentExtractor extends Extractor {


    /**
     * Extracts links 
     */
    final protected void extract(ProcessorURI uri) {
        boolean finished = innerExtract(uri);
        if (finished) {
            uri.linkExtractorFinished();
        }
    }


    /**
     * Determines if links should be extracted from the given URI.  This 
     * method performs three checks.  The first is to check the URI's
     * {@link ExtractorURI#hasBeenLinkExtracted()} result.  If that
     * result is true, then this method returns false, as some other 
     * extractor has claimed that links are already extracted.
     * 
     * <p>Next, this method checks that the content length of the URI is
     * greater than zero (in other words, that there is actually content
     * for links to be extracted from).  If the content length of the URI
     * is zero or less, then this method returns false.
     * 
     * <p>Finally, this method delegates to {@link #innerExtract(ExtractorURI)}
     * and returns that result.
     *
     * @param uri   the URI to check
     * @return   true if links should be extracted from the URI, 
     *   false otherwise
     */
    final protected boolean shouldProcess(ProcessorURI uri) {
        if (uri.hasBeenLinkExtracted()) {
            return false;
        }
        if (uri.getContentLength() <= 0) {
            return false;
        }
        if (!shouldExtract(uri)) {
            return false;
        }
        return true;
    }


    /**
     * Determines if otherwise valid URIs should have links extracted or not.
     * The given URI will not have its 
     * {@link ExtractorURI#hasBeenLinkExtracted()} flag set, and its
     * content length will be greater than zero.  Subclasses should 
     * implement this method to perform additional checks.  For instance,
     * the {@link ExtractorHTML} implementation checks that the content-type
     * of the given URI is text/html.
     * 
     * @param uri   the URI to check
     * @return   true if links should be extracted from that URI, false
     *   otherwise
     */
    protected abstract boolean shouldExtract(ProcessorURI uri);

    
    /**
     * Actually extracts links.  The given URI will have passed the three
     * checks described in {@link #shouldProcess(ExtractorURI)}.  Subclasses
     * should implement this method to discover outlinks in the URI's 
     * content stream.  For instance, {@link ExtractorHTML} extracts links
     * from Anchor tags and so on.
     * 
     * <p>This method should only return true if extraction completed 
     * successfully.  If not (for instance, if an IO error occurred), then
     * this method should return false.  Returning false indicates to the
     * pipeline that downstream extractors should attempt to extract links
     * themselves.  Returning true indicates that downstream extractors
     * should be skipped.
     * 
     * @param uri   the URI whose links to extract
     * @return  true if link extraction finished; false if downstream
     * extractors should attempt to extract links
     */
    protected abstract boolean innerExtract(ProcessorURI uri);
    
}
