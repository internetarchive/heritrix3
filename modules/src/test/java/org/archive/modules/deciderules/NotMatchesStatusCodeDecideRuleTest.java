package org.archive.modules.deciderules;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.state.ModuleTestBase;

public class NotMatchesStatusCodeDecideRuleTest extends ModuleTestBase {

    public void testInBounds() throws Exception {
        NotMatchesStatusCodeDecideRule dr = makeDecideRule(400,499);
        CrawlURI testUri = createTestUri("http://www.archive.org");
        testUri.setFetchStatus(404);

        assertFalse(dr.evaluate(testUri));
    }
    
    public void testOutOfBounds() throws Exception {
        NotMatchesStatusCodeDecideRule dr = makeDecideRule(400,499);
        CrawlURI testUri = createTestUri("http://www.archive.org");
        testUri.setFetchStatus(200);

        assertTrue(dr.evaluate(testUri));
    }
    
    public void testNoStatusYet() throws Exception {
        NotMatchesStatusCodeDecideRule dr = makeDecideRule(400,499);
        CrawlURI testUri = createTestUri("http://www.archive.org");

        assertTrue(dr.evaluate(testUri));
    }
    
    private CrawlURI createTestUri(String urlStr) throws URIException{
        UURI testUuri = UURIFactory.getInstance(urlStr);
        CrawlURI testUri = new CrawlURI(testUuri, null, null, LinkContext.NAVLINK_MISC);

        return testUri;
    }
    private NotMatchesStatusCodeDecideRule makeDecideRule(int lowerBound, int upperBound) {
        NotMatchesStatusCodeDecideRule dr = new NotMatchesStatusCodeDecideRule();
        
        dr.setLowerBound(lowerBound);
        dr.setUpperBound(upperBound);
        return dr;
    }
}