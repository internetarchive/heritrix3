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
 
package org.archive.crawler.selftest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;

/**
 * Tests that URLs can be assigned precedence values based on in-line analysis
 * module that prioritizes newly discovered links based on an approximate
 * ranking during a crawl.
 * 
 * <p>The test data consists of 15 documents, titled A through O.  Each document
 * links to two other documents, forming a sorted, balanced binary tree:
 * 
 * <ul>
 * <li>H</li>
 *     <ul>
 *     <li>D</li>
 *         <ul>
 *         <li>B</li>
 *             <ul>
 *             <li>A</li>
 *             <li>C</li>
 *             </ul>
 *         <li>F</li>
 *             <ul>
 *             <li>E</li>
 *             <li>G</li>
 *             </ul>
 *         </ul>
 *     <li>L</li>
 *         <ul>
 *         <li>J</li>
 *             <ul>
 *             <li>I</li>
 *             <li>K</li>
 *             </ul>
 *         <li>N</li>
 *             <ul>
 *             <li>M</li>
 *             <li>O</li>
 *             </ul>
 *         </ul>
 *     </ul>
 * </ul>
 * 
 * <p>If H is the seed, then Heritrix would ordinarily crawl these in the order
 * <code>(H, L, D, J, N, F, B, K, I, M, O, G, E, C, A)</code> -- loosely the
 * order the links were discovered.
 * 
 * <p>However, this test uses the {@link KeyWordProcessor} to ensure that, if
 * a document contains a certain keyword, then that document's out links are 
 * crawled before the out links of documents that do not contain the keyword.
 * 
 * <p>The documents A, B, D, E, H, I, J and M all contain the keyword (these
 * are the "left-branch"/first-link documents in the tree above, plus the 
 * root/seed).  The other documents do not.
 * 
 * <p>Therefore this test makes sure that the children of documents containing
 * the keyword are crawled before children of documents not containing the 
 * keyword:
 * 
 * <ol>
 * <li>The children of D (B and F) should be crawled before the children 
 * of L (J and N).</li>
 * <li>The children of B (A and C) should be crawled before the children 
 * of F (E and G).</li>
 * <li>The children of J (I and K) should be crawled before the children of
 * N (M and O).
 * </ol>
 * 
 * <p>This test provides a simple proof-of-concept that shows how the content
 * of one URI can alter the precedence of the out links of that URI.  See
 * {@link KeyWordProcessor} for suggestions on more sophisticated approaches.
 * 
 * @author pjack
 */
public class Precedence3SelfTest extends SelfTestBase {

    
    @Override
    protected void verify() throws Exception {
        File crawlLog = new File(getLogsDir(), "crawl.log");
        BufferedReader br = null;
        List<String> crawled = new ArrayList<String>();

        try {
            br = new BufferedReader(new FileReader(crawlLog));
            for (String s = br.readLine(); s != null; s = br.readLine()) {
                s = s.substring(42);
                int i = s.indexOf(' ');
                s = s.substring(0, i);
                crawled.add(s);
            }
        } finally {
            IOUtils.closeQuietly(br);
        }
        
        System.out.println(crawled);
      
        assertEquals("http://127.0.0.1:7777/robots.txt", crawled.get(0));
        assertEquals("http://127.0.0.1:7777/H.html", crawled.get(1));

        // D contains the keyword and L does not.
        // D's children (B and F) should be crawled before L's (J and N).
        assertBefore(crawled, 'B', 'F', 'J', 'N');
        
        // B contains the keyword and F does not.
        // B's children (A and C) should be crawled before F's (E and G).        
        assertBefore(crawled, 'A', 'C', 'E', 'G');
        
        // J contains the keyword and N does not.
        // J's children (I and K) should be crawled before N's (M and O).
        assertBefore(crawled, 'I', 'K', 'M', 'O');
    }
    
    
    private boolean assertBefore(List<String> crawled, 
            char k1, char k2, char n1, char n2) {
        int k1Index = crawled.indexOf(toFullURI(k1));
        int k2Index = crawled.indexOf(toFullURI(k2));
        int n1Index = crawled.indexOf(toFullURI(n1));
        int n2Index = crawled.indexOf(toFullURI(n2));
        // Make sure all four documents were actually crawled.
        assertTrue(k1Index > 0);
        assertTrue(k2Index > 0);
        assertTrue(n1Index > 0);
        assertTrue(n2Index > 0);

        // Make sure children of keyword-containing-parent were crawled before
        // children of no-keyword-containing-parent.
        assertTrue(k1Index + " >= " + n1Index, k1Index < n1Index);
        assertTrue(k1Index + " >= " + n2Index, k1Index < n2Index);
        assertTrue(k2Index + " >= " + n1Index, k2Index < n1Index);
        assertTrue(k2Index + " >= " + n1Index, k2Index < n2Index);
        
        return false;
    }
    
    private String toFullURI(char ch) {
        return "http://127.0.0.1:7777/" + ch + ".html";
    }

    protected String getSeedsString() {
        return "http://127.0.0.1:7777/H.html";
    }
    
    @Override
    protected String changeGlobalConfig(String config) {
        // single toethread
        config = config.replace("@@MORE_PROPERTIES@@", "crawlController.maxToeThreads=1");
        
        // add the keyword-based uriPrecedencePolicy
        String uriPrecedencePolicy = 
            "<bean class='org.archive.crawler.selftest.KeyWordUriPrecedencePolicy'>\n" +
            "  <property name='basePrecedence' value='5'/>\n" +
            " </bean>";
        config = config.replace("<!--@@BEANS_MOREBEANS@@-->", uriPrecedencePolicy);

        // add the keyword processor after candidateScoper
        config = config.replace(
                "<ref bean=\"candidateScoper\"/>", 
                "<ref bean=\"candidateScoper\"/>\n" +
                "<bean class='org.archive.crawler.selftest.KeyWordProcessor'/>");
        return super.changeGlobalConfig(config);
    }
}
