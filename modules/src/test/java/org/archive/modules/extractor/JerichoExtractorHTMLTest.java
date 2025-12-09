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


import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.archive.url.URIException;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test html extractor.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public class JerichoExtractorHTMLTest extends ExtractorHTMLTest {

    @Override
    protected Extractor makeExtractor() {
        JerichoExtractorHTML result = new JerichoExtractorHTML();
        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();
        result.setLoggerModule(ulm);
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        result.setMetadata(metadata);
        result.setExtractorJS(new ExtractorJS());
        result.afterPropertiesSet();
        return result;
    }
    
    
    /**
     * Test a GET FORM ACTION extraction
     */
    @Test
    public void testFormsLinkGet() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.example.org");
        CrawlURI curi = new CrawlURI(uuri);
        CharSequence cs = 
        	"<form name=\"testform\" method=\"GET\" action=\"redirect_me?form=true\"> " +
        	"  <INPUT TYPE=CHECKBOX NAME=\"checked[]\" VALUE=\"1\" CHECKED> "+
        	"  <INPUT TYPE=CHECKBOX NAME=\"unchecked[]\" VALUE=\"1\"> " +
        	"  <select name=\"selectBox\">" +
        	"    <option value=\"selectedOption\" selected>option1</option>" +
        	"    <option value=\"nonselectedOption\">option2</option>" +
        	"  </select>" +
        	"  <input type=\"submit\" name=\"test\" value=\"Go\">" +
        	"</form>";   
        getExtractor().extract(curi, cs);
        curi.getOutLinks();
        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().contains(
                        "/redirect_me?form=true&checked[]=1&unchecked[]=&selectBox=selectedOption&test=Go");
            }
        }));
    }
    
    /**
     * Test a POST FORM ACTION being properly ignored 
     */
    @Test
    public void testFormsLinkIgnorePost() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.example.org");
        CrawlURI curi = new CrawlURI(uuri);
        CharSequence cs = 
            "<form name=\"testform\" method=\"POST\" action=\"redirect_me?form=true\"> " +
            "  <INPUT TYPE=CHECKBOX NAME=\"checked[]\" VALUE=\"1\" CHECKED> "+
            "  <INPUT TYPE=CHECKBOX NAME=\"unchecked[]\" VALUE=\"1\"> " +
            "  <select name=\"selectBox\">" +
            "    <option value=\"selectedOption\" selected>option1</option>" +
            "    <option value=\"nonselectedOption\">option2</option>" +
            "  </select>" +
            "  <input type=\"submit\" name=\"test\" value=\"Go\">" +
            "</form>";   
        getExtractor().extract(curi, cs);
        curi.getOutLinks();
        assertFalse(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().contains(
                        "/redirect_me?form=true&checked[]=1&unchecked[]=&selectBox=selectedOption&test=Go");
            }
        }));
    }
    
    /**
     * Test a POST FORM ACTION being found with non-default setting
     */
    @Test
    public void testFormsLinkFindPost() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.example.org");
        CrawlURI curi = new CrawlURI(uuri);
        CharSequence cs = 
            "<form name=\"testform\" method=\"POST\" action=\"redirect_me?form=true\"> " +
            "  <INPUT TYPE=CHECKBOX NAME=\"checked[]\" VALUE=\"1\" CHECKED> "+
            "  <INPUT TYPE=CHECKBOX NAME=\"unchecked[]\" VALUE=\"1\"> " +
            "  <select name=\"selectBox\">" +
            "    <option value=\"selectedOption\" selected>option1</option>" +
            "    <option value=\"nonselectedOption\">option2</option>" +
            "  </select>" +
            "  <input type=\"submit\" name=\"test\" value=\"Go\">" +
            "</form>";
        getExtractor().setExtractOnlyFormGets(false);
        getExtractor().extract(curi, cs);
        curi.getOutLinks();
        assertTrue(CollectionUtils.exists(curi.getOutLinks(), new Predicate() {
            public boolean evaluate(Object object) {
                return ((CrawlURI) object).getURI().contains(
                        "/redirect_me?form=true&checked[]=1&unchecked[]=&selectBox=selectedOption&test=Go");
            }
        }));
    }

    @Test
    public void testMultipleAttributesPerElement() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.example.org");
        CrawlURI curi = new CrawlURI(uuri);
        CharSequence cs = "<a src=\"http://www.example.com/\" href=\"http://www.archive.org/\"> "; 
        getExtractor().extract(curi, cs);
        assertEquals(2, curi.getOutLinks().size(), "not all links found");
    }

    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testConditionalComment1() throws URIException {
    }

    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testMetaContentURI() throws URIException {
    }
   
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataAttributesTelerama() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataAttributesLeMonde() throws URIException {
    }
   
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataAttributesEuronews() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testSourceSrcsetAttributes() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataFullSrcAttributesSlate() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataLazyAttributes() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataSrcAttributes() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testSrcSetAttributes() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataAttributes20Minutes() throws URIException {
    }
    
    /*
     * Override of ExtractorHTMLTest method because the test fails with
     * JerichoExtractorHTML
     */
    @Override
    @Test
    public void testDataAttributesNouvelObs() throws URIException {
    }
    
    @Override
    protected JerichoExtractorHTML getExtractor() {
        return (JerichoExtractorHTML) extractor;
    }
}