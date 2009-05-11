/* CharSequenceLinkExtractor
*
* $Id$
*
* Created on Mar 17, 2005
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
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import org.archive.modules.extractor.Link;
import org.archive.net.UURI;

/**
 * Abstract superclass providing utility methods for LinkExtractors which
 * would prefer to work on a CharSequence rather than a stream.
 *
 * ROUGH DRAFT IN PROGRESS / incomplete... untested... 
 * 
 * @author gojomo
 */
public abstract class CharSequenceLinkExtractor implements LinkExtractor {

    protected UURI source;
    protected UURI base;
    protected ExtractErrorListener extractErrorListener;

    protected CharSequence sourceContent;
    protected LinkedList<Link> next;

    public void setup(UURI source, UURI base, InputStream content,
            Charset charset, ExtractErrorListener listener) {
        setup(source, base, charSequenceFrom(content,charset), listener);
    }

    /**
     * @param source
     * @param base
     * @param content
     * @param listener
     */
    public void setup(UURI source, UURI base, CharSequence content,
            ExtractErrorListener listener) {
        this.source = source;
        this.base = base;
        this.extractErrorListener = listener;
        this.sourceContent = content;
        this.next = new LinkedList<Link>();
    }


    /**
     * Convenience method for when source and base are same.
     *
     * @param sourceandbase
     * @param content
     * @param listener
     */
    public void setup(UURI sourceandbase, CharSequence content,
            ExtractErrorListener listener) {
        setup(sourceandbase, sourceandbase, content, listener);
    }

    /* (non-Javadoc)
     * @see org.archive.extractor.LinkExtractor#setup(org.archive.crawler.datamodel.UURI, java.io.InputStream, java.nio.charset.Charset)
     */
    public void setup(UURI sourceandbase, InputStream content, Charset charset,
            ExtractErrorListener listener) {
        setup(sourceandbase,sourceandbase,content,charset,listener);
    }

    /* (non-Javadoc)
     * @see org.archive.extractor.LinkExtractor#nextLink()
     */
    public Link nextLink() {
        if(!hasNext()) {
            throw new NoSuchElementException();
        }
        // next will have been filled with at least one item
        return (Link) next.removeFirst();
    }

    /**
     * Discard all state. Another setup() is required to use again.
     */
    public void reset() {
        base = null;
        source = null;
        sourceContent = null; // TODO: discard other resources
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#hasNext()
     */
    public boolean hasNext() {
        if (!next.isEmpty()) {
            return true;
        }
        return findNextLink();
    }

    /**
     * Scan to the next link(s), if any, loading it into the next buffer.
     *
     * @return true if any links are found/available, false otherwise
     */
    abstract protected boolean findNextLink();

    /* (non-Javadoc)
     * @see java.util.Iterator#next()
     */
    public Link next() {
        return nextLink();
    }

    /* (non-Javadoc)
     * @see java.util.Iterator#remove()
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    /**
     * @param content
     * @param charset
     * @return CharSequence obtained from stream in given charset
     */
    protected CharSequence charSequenceFrom(InputStream content, Charset charset) {
        // See if content InputStream can provide
        if(content instanceof CharSequenceProvider) {
            return ((CharSequenceProvider)content).getCharSequence();
        }
        // otherwise, create one
        return createCharSequenceFrom(content, charset);
    }

    /**
     * @param content
     * @param charset
     * @return CharSequence built over given stream in given charset
     */
    protected CharSequence createCharSequenceFrom(InputStream content, Charset charset) {
        // TODO: implement
        return null;
        // TODO: consider cleanup in reset()
    }

    /**
     * Convenience method to do default extraction.
     *
     * @param content
     * @param source
     * @param base
     * @param collector
     * @param extractErrorListener
     */
    public static void extract(CharSequence content, UURI source, UURI base,
            List<Link> collector, ExtractErrorListener extractErrorListener) {
        // TODO: arrange for inheritance of prefs... eg when HTML includes JS
        // includes HTML, have inner HTML follow robots, etc from outer
        CharSequenceLinkExtractor extractor = newDefaultInstance();
        extractor.setup(source, base, content, extractErrorListener);
        while (extractor.hasNext()) {
            collector.add(extractor.nextLink());
        }
        extractor.reset();
    }

    protected static CharSequenceLinkExtractor newDefaultInstance() {
        // override in subclasses
        return null;
    }
}
