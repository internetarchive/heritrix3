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

import java.util.Collection;
import java.util.HashSet;

import org.archive.modules.CrawlURI;

public abstract class StringExtractorTestBase extends ContentExtractorTestBase {

    
    public static class TestData {
        
        public CrawlURI uri;
        public Link expectedResult;
        
        public TestData(CrawlURI uri, Link expectedResult) {
            this.uri = uri;
            this.expectedResult = expectedResult;
        }
    }


    /**
     * Returns an array of valid test data pairs.  The pairs consist of text
     * to be processed followed by 
     * 
     * @return  the test data 
     */
    protected abstract String[] getValidTestData();

    
    protected abstract Collection<TestData> makeData(String text, String uri)
    throws Exception;

    /**
     * Tests each text/URI pair in the test data array.
     * 
     * @throws Exception   just in case
     */
    public void testExtraction() throws Exception {
        try {
            String[] valid = getValidTestData();
            for (int i = 0; i < valid.length; i += 2) {
                testOne(valid[i], valid[i + 1]);
            }
        } catch (Exception e) {
            e.printStackTrace(); // I hate maven.
            throw e;
        }
    }
    
    
    /**
     * Runs the given text through an Extractor, expecting the given
     * URL to be extracted.
     * 
     * @param text    the text to process
     * @param expectedURL   the URL that should be extracted from the text
     * @throws Exception  just in case
     */
    private void testOne(String text, String expectedURL) throws Exception {
        Collection<TestData> testDataCol = makeData(text, expectedURL);
        for (TestData testData: testDataCol) {
            extractor.process(testData.uri);
            HashSet<Link> expected = new HashSet<Link>();
            if (testData.expectedResult != null) {
                expected.add(testData.expectedResult);
            }
            assertEquals(expected, testData.uri.getOutLinks());
            assertNoSideEffects(testData.uri);
        }
    }

    
}
