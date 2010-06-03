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

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Selftest for figuring problems parsing URIs in a page.
 * 
 * @author stack
 * @see <a 
 * href="https://sourceforge.net/tracker/?func=detail&aid=788219&group_id=73833&atid=539099">[ 788219 ]
 * URI Syntax Errors stop page parsing.</a>
 * @version $Revision$, $Date$
 */
public class BadURIsStopPageParsingSelfTest extends SelfTestBase
{

    /**
     * Files to find as a set.
     */
    final private static Set<String> EXPECTED = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(new String[] {
            "index.html", "goodone.html", "goodthree.html", "one.html", 
            "two.html", "three.html", "robots.txt", "favicon.ico",
            "cata;pgs-new.html", "www.loc.gov/rr/european/egw/polishex.html"
    })));
    
    @Override
    protected void verify() throws Exception {
        Set<String> files = filesInArcs();
        assertEquals("URIs retrieved mismatch expected",EXPECTED,files);
    }


    @Override
    protected void verifyLogFileEmpty(String logFileName) {
        if (logFileName.equals("uri-errors.log")) {
            File logsDir = getLogsDir();
            File log = new File(logsDir, logFileName);
            if (log.length() == 0) {
                throw new IllegalStateException("Log " + logFileName + 
                        " is empty, expected URI failure.");
            }
            return;
        }
        super.verifyLogFileEmpty(logFileName);
    }
}
