/* BadURIsStopPageParsingSelfTest
 *
 * Created on Mar 10, 2004
 *
 * Copyright (C) 2004 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
            "two.html", "three.html", "robots.txt",
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
