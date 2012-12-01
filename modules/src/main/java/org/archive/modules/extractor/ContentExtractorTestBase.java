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


import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.archive.modules.CrawlURI;
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
     * Returns a CrawlURI for testing purposes.
     * 
     * @return   a CrawlURI
     * @throws Exception   just in case
     */
    protected CrawlURI defaultURI() throws Exception {
        UURI uuri = UURIFactory.getInstance("http://www.archive.org/start/");
        return new CrawlURI(uuri, null, null, LinkContext.NAVLINK_MISC);
    }
    
    
    /**
     * Tests that a URI with a zero content length has no links extracted.
     * 
     * @throws Exception   just in case
     */
    public void testZeroContent() throws Exception {
        CrawlURI uri = defaultURI();
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
        CrawlURI uri = defaultURI();
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
    protected static void assertNoSideEffects(CrawlURI uri) {
        assertEquals(0, uri.getNonFatalFailures().size());
        assertTrue(uri.getAnnotations().isEmpty());
    }
    
    @Deprecated
    public static Recorder createRecorder(String content) throws IOException {
        return createRecorder(content, Charset.defaultCharset().name());
    }
    
    public static Recorder createRecorder(String content, String charset)
            throws IOException {
        File temp = File.createTempFile("test", ".tmp");
        Recorder recorder = new Recorder(temp, 1024, 1024);
        byte[] b = content.getBytes(charset);
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        InputStream is = recorder.inputWrap(bais);
        for (int x = is.read(); x >= 0; x = is.read());
        is.close();
        return recorder;
    }

}
