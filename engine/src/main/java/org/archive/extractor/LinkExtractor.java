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
