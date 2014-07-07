package org.archive.modules.deciderules;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.state.ModuleTestBase;

public class MatchesStatusCodeDecideRuleTest extends ModuleTestBase {

    public void testInBounds() throws Exception {
        MatchesStatusCodeDecideRule dr = makeDecideRule(400,499);
        CrawlURI testUri = createTestUri("http://www.archive.org");
        testUri.setFetchStatus(404);

        assertTrue(dr.evaluate(testUri));
    }
    
    public void testOutOfBounds() throws Exception {
        MatchesStatusCodeDecideRule dr = makeDecideRule(400,499);
        CrawlURI testUri = createTestUri("http://www.archive.org");
        testUri.setFetchStatus(200);
     
        assertFalse(dr.evaluate(testUri));
    }
    
    public void testNoStatusYet() throws Exception {
        MatchesStatusCodeDecideRule dr = makeDecideRule(400,499);
        CrawlURI testUri = createTestUri("http://www.archive.org");
 
        assertFalse(dr.evaluate(testUri));
    }
    
    private CrawlURI createTestUri(String urlStr) throws URIException{
        UURI testUuri = UURIFactory.getInstance(urlStr);
        CrawlURI testUri = new CrawlURI(testUuri, null, null, LinkContext.NAVLINK_MISC);

        return testUri;
    }
    private MatchesStatusCodeDecideRule makeDecideRule(int lowerBound, int upperBound) {
        MatchesStatusCodeDecideRule dr = new MatchesStatusCodeDecideRule();
        
        dr.setLowerBound(lowerBound);
        dr.setUpperBound(upperBound);
        return dr;
    }
}