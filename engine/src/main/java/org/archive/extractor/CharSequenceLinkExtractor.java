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
