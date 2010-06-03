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

import org.archive.crawler.frontier.precedence.BaseUriPrecedencePolicy;
import org.archive.util.ArchiveUtils;

/**
 * Tests that operators can create precedence groups for URIs, and that URIs
 * in one group are crawled before URIs in another group per operator preference.
 * 
 * <p>The embedded Jetty HTTP server for this test provides the following
 * document tree:
 * 
 * <ul>
 * <li>seed.html</li>
 * <li>one/</li>
 *     <ul>
 *     <li>a.html</li>
 *     <li>b.html</li>
 *     <li>c.html</li>
 *     </ul>
 * <li>five/</li>
 *     <ul>
 *     <li>a.html</li>
 *     <li>b.html</li>
 *     <li>c.html</li>
 *     </ul>
 * <li>ten/</li>
 *     <ul>
 *     <li>a.html</li>
 *     <li>b.html</li>
 *     <li>c.html</li>
 *     </ul>
 * </ul>
 * 
 * (See the <code>engine/testdata/selftest/Precedence1SelfTest</code>
 * directory to view these files.) The <code>seed.html</code> file contains
 * links to <code>five/a.html</code>, <code>ten/a.html</code>, and
 * <code>one/a.html</code>, in that order.  The <code>a.html</code> files link 
 * to to the <code>b.html</code> files, and the <code>b.html</code> link to 
 * the <code>c.html</code> files, which have no out links.
 *
 * <p>Ordinarily Heritrix would crawl these in (roughly) the order the links
 * are discovered:
 * 
 * <ol>
 * <li>seed.html</li>
 * <li>five/a.html</li>
 * <li>ten/a.html</li>
 * <li>one/a.html</li>
 * <li>five/b.html</li>
 * <li>ten/b.html</li>
 * <li>one/b.html</li>
 * <li>five/c.html</li>
 * <li>ten/c.html</li>
 * <li>one/c.html</li>
 * </ol>
 * 
 * <p>However, the crawl configuration for this test uses a 
 * {@link BaseUriPrecedencePolicy} instead of the default 
 * {@link org.archive.crawler.frontier.policy.CostUriPrecedencePolicy}.  The
 * <code>BasePrecedencePolicy</code> is configured so that all URIs have a 
 * precedence value of 5 unless otherwise specified.
 * 
 * <p>There is a sheet named <code>HiPri</code> that overrides the 
 * <code>base-precedence</code> to be 1 instead of 5; thus URIs associated
 * with the HiPri sheet should be crawled before other URIs.
 * Similarly, there is a sheet named <code>LoPri</code> that overrides
 * <code>base-precedence</code> to be 10 instead of 5.  URLs associated with
 * LoPri should be crawled after other URLs.
 * 
 * <p>The <code>one/</code> directory is associated with the HiPri sheet, and
 * the <code>ten/</code> directory is associated with the LoPri sheet.  This
 * creates three "groups" of URIs: one, five and ten.  All of the URIs in 
 * group "one" should be crawled before any of the URIs in group "five" are
 * crawled.  Similarly, all of the URIs in group "five" should be crawled before
 * any of the URIs in group "ten".
 *
 * <p>So the final order in which URLs should be crawled in this test is:
 * 
 * <ol>
 * <li>seed.html</li>
 * <li>one/a.html</li>
 * <li>one/b.html</li>
 * <li>one/c.html</li>
 * <li>five/a.html</li>
 * <li>five/b.html</li>
 * <li>five/c.html</li>
 * <li>ten/a.html</li>
 * <li>ten/b.html</li>
 * <li>ten/c.html</li>
 * </ol>
 * 
 * This tests ensures that the documents were crawled in the correct order.
 * 
 * <p>Although this test uses the directory structure of the URIs to group the URIs
 * into precedence groups, because the test executes on just one machine.
 * But the same basic configuration could be used to group URIs by any SURT
 * prefix -- by host or by domain, even by top-level domain.  So an operator
 * could associate HiPri with all .gov sites to ensure that all .gov URIs
 * are crawled before any non-.gov URIs.
 * 
 * @author pjack
 */
public class Precedence1SelfTest extends SelfTestBase {


    /**
     * Expected results of the crawl.
     */
    final private static String EXPECTED =
        "http://127.0.0.1:7777/robots.txt\n" + 
        "http://127.0.0.1:7777/seed.html\n" + 
        "http://127.0.0.1:7777/favicon.ico\n" + 
        "http://127.0.0.1:7777/one/a.html\n" + 
        "http://127.0.0.1:7777/one/b.html\n" + 
        "http://127.0.0.1:7777/one/c.html\n" + 
        "http://127.0.0.1:7777/five/a.html\n" + 
        "http://127.0.0.1:7777/five/b.html\n" + 
        "http://127.0.0.1:7777/five/c.html\n" + 
        "http://127.0.0.1:7777/ten/a.html\n" + 
        "http://127.0.0.1:7777/ten/b.html\n" +
        "http://127.0.0.1:7777/ten/c.html\n";
    
    
    @Override
    protected void verify() throws Exception {
        File crawlLog = new File(getLogsDir(), "crawl.log");
        BufferedReader br = null;
        String crawled = "";
        try {
            br = new BufferedReader(new FileReader(crawlLog));
            for (String s = br.readLine(); s != null; s = br.readLine()) {
                s = s.substring(42);
                int i = s.indexOf(' ');
                s = s.substring(0, i);
                crawled = crawled + s + "\n";
            }
        } finally {
            ArchiveUtils.closeQuietly(br);
        }
        
        assertEquals(EXPECTED, crawled);
    }

    protected String getSeedsString() {
        return "http://127.0.0.1:7777/seed.html";
    }
    
    @Override
    protected String changeGlobalConfig(String config) {
        // add a uriPrecedencePolicy with overlayable values, IF replaced
        // string not already gone (as if by subclass)
        String uriPrecedencePolicy = 
            " <bean name=\'uriPrecedencePolicy\' class='org.archive.crawler.frontier.precedence.BaseUriPrecedencePolicy'>\n" +
            "  <property name='basePrecedence' value='5'/>\n" +
            " </bean>";
        config = config.replace("<!--@@BEANS_MOREBEANS@@-->", uriPrecedencePolicy);
        
        config = configureSheets(config);
        return super.changeGlobalConfig(config);
    }

    protected String configureSheets(String config) {
        // add sheets which overlay alternate precedence values for some URIs
        String sheets = 
            "<bean class='org.archive.crawler.spring.SurtPrefixesSheetAssociation'>\n" +
            " <property name='surtPrefixes'>\n" +
            "  <list>\n" +
            "   <value>http://(127.0.0.1:7777)/ten</value>\n" +
            "  </list>\n" +
            " </property>\n" +
            " <property name='targetSheetNames'>\n" +
            "  <list>\n" +
            "   <value>loPri</value>\n" +
            "  </list>\n" +
            " </property>\n" +
            "</bean>\n" +
            "<bean id='loPri' class='org.archive.spring.Sheet'>\n" +
            " <property name='map'>\n" +
            "  <map>\n" +
            "   <entry key='preparer.uriPrecedencePolicy.basePrecedence' value='10'/>\n" +
            "  </map>\n" +
            " </property>\n" +
            "</bean>\n" +
            "<bean class='org.archive.crawler.spring.SurtPrefixesSheetAssociation'>\n" +
            " <property name='surtPrefixes'>\n" +
            "  <list>\n" +
            "   <value>http://(127.0.0.1:7777)/one</value>\n" +
            "  </list>\n" +
            " </property>\n" +
            " <property name='targetSheetNames'>\n" +
            "  <list>\n" +
            "   <value>hiPri</value>\n" +
            "  </list>\n" +
            " </property>\n" +
            "</bean>\n" +
            "<bean id='hiPri' class='org.archive.spring.Sheet'>\n" +
            " <property name='map'>\n" +
            "  <map>\n" +
            "   <entry key='preparer.uriPrecedencePolicy.basePrecedence' value='1'/>\n" +
            "  </map>\n" +
            " </property>\n" +
            "</bean>\n";

        config = config.replace("</beans>", sheets+"</beans>");
        return config;
    }
}
