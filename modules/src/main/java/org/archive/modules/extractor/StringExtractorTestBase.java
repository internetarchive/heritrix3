package org.archive.modules.extractor;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.archive.modules.DefaultProcessorURI;



public abstract class StringExtractorTestBase extends ContentExtractorTestBase {

    
    public static class TestData {
        
        public DefaultProcessorURI uri;
        public Link expectedResult;
        
        public TestData(DefaultProcessorURI uri, Link expectedResult) {
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
            Extractor extractor = makeExtractor();
            extractor.process(testData.uri);
            List<Link> expected = new ArrayList<Link>();
            expected.add(testData.expectedResult);
            assertEquals(expected, testData.uri.getOutLinks());
            assertNoSideEffects(testData.uri);
        }
    }

    
}
