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
 * Tests that operators can manually assign precedence values to individual 
 * URLs.
 * 
 * <p>This class crawls the same directory structure as 
 * {@link Precedence1SelfTest}, using the same number of sheets.  However, 
 * insteading of creating groups of URIs using SURT prefixes, the HiPri and
 * LoPri sheets are assigned to two individual URIs.  The test then assures
 * that the HiPri URI is crawled before anything else, and that the LoPri
 * URL is crawled after everything else.
 * 
 * @author pjack
 */
public class Precedence4SelfTest extends Precedence1SelfTest {

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
        
        assertEquals("http://127.0.0.1:7777/robots.txt", crawled.get(0));
        assertEquals("http://127.0.0.1:7777/five/a.html", crawled.get(1));
        assertEquals("http://127.0.0.1:7777/five/b.html", crawled.get(crawled.size() - 1));
    }

    
    
    protected String getSeedsString() {
        return "http://127.0.0.1:7777/seed.html\\n"+
            "http://127.0.0.1:7777/one/a.html\\n"+
            "http://127.0.0.1:7777/five/a.html\\n"+
            "http://127.0.0.1:7777/ten/a.html\\n"+
            "http://127.0.0.1:7777/ten/b.html\\n"+
            "http://127.0.0.1:7777/five/b.html\\n"+
            "http://127.0.0.1:7777/one/b.html\\n"+
            "http://127.0.0.1:7777/five/c.html\\n"+
            "http://127.0.0.1:7777/one/c.html\\n"+
            "http://127.0.0.1:7777/ten/c.html";
    }
    
    protected String configureSheets(String config) {
        // add sheets which overlay alternate precedence values for two
        // specific URIs
        String sheets = 
            "<bean class='org.archive.crawler.spring.SurtPrefixesSheetAssociation'>\n" +
            " <property name='surtPrefixes'>\n" +
            "  <list>\n" +
            "   <value>http://(127.0.0.1:7777)/five/b.html</value>\n" +
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
            "   <value>http://(127.0.0.1:7777)/five/a.html</value>\n" +
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
