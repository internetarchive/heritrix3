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
 * ContentExtractorTest.java
 * Created on October 5, 2006
 *
 * $Header$
 */
package org.archive.modules.extractor;


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Collections;

import org.archive.modules.DefaultProcessorURI;
import org.archive.modules.ProcessorTestBase;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.state.ModuleTestBase;
import org.archive.util.Recorder;


/**
 * Abstract base class for unit testing ContentExtractor implementations.
 * 
 * @author pjack
 */
public abstract class ContentExtractorTestBase extends ProcessorTestBase {

    
    /**
     * An extractor created during the setUp.
     */
    protected Extractor extractor;

    
    /**
     * Sets up the {@link #extractor} and 
     * {@link ModuleTestBase#processorClass}
     * fields.
     */
    final public void setUp() {
        extractor = makeExtractor();
    }
    
    
    @Override
    protected Object makeModule() {
        return makeExtractor();
    }
        
    
    /**
     * Subclasses should return an Extractor instance to test.
     * 
     * @return   an Extractor instance to test
     */
    protected abstract Extractor makeExtractor();
    
    
    /**
     * Returns a DefaultProcessorURI for testing purposes.
     * 
     * @return   a DefaultProcessorURI
     * @throws Exception   just in case
     */
    protected DefaultProcessorURI defaultURI() throws Exception {
        UURI uuri = UURIFactory.getInstance("http://www.archive.org/start/");
        return new DefaultProcessorURI(uuri, LinkContext.NAVLINK_MISC);
    }
    
    
    /**
     * Tests that a URI with a zero content length has no links extracted.
     * 
     * @throws Exception   just in case
     */
    public void testZeroContent() throws Exception {
        DefaultProcessorURI uri = defaultURI();
        Recorder recorder = createRecorder("");
        uri.setContentType("text/plain");
        uri.setRecorder(recorder);
        extractor.process(uri);
        assertEquals(0, uri.getOutLinks().size());
        assertNoSideEffects(uri);
    }
    
    
    /**
     * Tests that a URI whose linkExtractionFinished flag has been set has
     * no links extracted.
     * 
     * @throws Exception   just in case
     */
    public void testFinished() throws Exception {
        DefaultProcessorURI uri = defaultURI();
        uri.linkExtractorFinished();
        extractor.process(uri);
        assertEquals(0, uri.getOutLinks().size());
        assertNoSideEffects(uri);        
    }

    
    /**
     * Asserts that the given URI has no URI errors, no localized errors, and
     * no annotations.
     * 
     * @param uri   the URI to test
     */
    protected static void assertNoSideEffects(DefaultProcessorURI uri) {
        assertEquals(0, uri.getUriErrors().size());
        assertEquals(0, uri.getNonFatalFailures().size());
        assertEquals(Collections.EMPTY_LIST, uri.getAnnotations());        
    }
    
    
    
    public static Recorder createRecorder(String content)  
    throws Exception {
        File temp = File.createTempFile("test", ".tmp");
        Recorder recorder = new Recorder(temp, 1024, 1024);
        byte[] b = content.getBytes(); // FIXME: Allow other encodings?
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        InputStream is = recorder.inputWrap(bais);
        for (int x = is.read(); x >= 0; x = is.read());
        is.close();
        return recorder;
    }

}
