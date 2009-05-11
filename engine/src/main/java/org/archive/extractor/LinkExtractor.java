/* LinkExtractor
*
* $Id$
*
* Created on Mar 16, 2005
*
* Copyright (C) 2005 Internet Archive.
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
package org.archive.extractor;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;

import org.archive.modules.extractor.Link;
import org.archive.net.UURI;

/**
 * LinkExtractor is a general interface for classes which, when given an
 * InputStream and Charset, can scan for Links and return them via
 * an Iterator interface.
 *
 * Implementors may in fact complete all extraction on the first
 * hasNext(), then trickle Links out from an internal collection,
 * depending on whether the link-extraction technique used is amenable
 * to incremental scanning.
 *
 * ROUGH DRAFT IN PROGRESS / incomplete... untested...
 * 
 * @author gojomo
 */
public interface LinkExtractor extends Iterator<Link> {
    /**
     * Setup the LinkExtractor to operate on the given stream and charset,
     * considering the given contextURI as the initial 'base' URI for
     * resolving relative URIs.
     *
     * May be called to 'reset' a LinkExtractor to start with new input.
     *
     * @param source source URI 
     * @param base base URI (usually the source URI) for URI derelativizing
     * @param content input stream of content to scan for links
     * @param charset Charset to consult to decode stream to characters
     * @param listener ExtractErrorListener to notify, rather than raising
     *   exception through extraction loop
     */
    public void setup(UURI source, UURI base, InputStream content,
            Charset charset, ExtractErrorListener listener);
    
    /**
     * Convenience version of above for common case where source and base are 
     * same. 
     * 
     * @param sourceandbase  URI to use as source and base for derelativizing
     * @param content input stream of content to scan for links
     * @param charset Charset to consult to decode stream to characters
     * @param listener ExtractErrorListener to notify, rather than raising
     *   exception through extraction loop
     */
    public void setup(UURI sourceandbase, InputStream content,
            Charset charset, ExtractErrorListener listener);
    
    /**
     * Alternative to Iterator.next() which returns type Link.
     * @return a discovered Link
     */
    public Link nextLink();

    /**
     * Discard all state and release any used resources.
     */
    public void reset();
}
