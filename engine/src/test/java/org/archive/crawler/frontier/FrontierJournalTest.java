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
package org.archive.crawler.frontier;

import org.apache.commons.httpclient.URIException;
import org.archive.util.TmpDirTestCase;

/**
 * @author stack
 * @version $Date$, $Revision$
 */
public class FrontierJournalTest extends TmpDirTestCase {
    private FrontierJournal rj;

    protected void setUp() throws Exception {
        super.setUp();
        this.rj = new FrontierJournal(this.getTmpDir().getAbsolutePath(),
            this.getClass().getName());
    }

    protected void tearDown() throws Exception {
        super.tearDown();
        if (this.rj != null) {
            this.rj.close();
        }
    }

    public static void main(String [] args) {
        junit.textui.TestRunner.run(FrontierJournalTest.class);
    }

    public void testAdded() throws URIException {
        /*
        CandidateURI c = new CandidateURI(UURIFactory.
            getInstance("http://www.archive.org"), "LLLLL",
            UURIFactory.getInstance("http://archive.org"),
            "L");
        this.rj.added(new CrawlURI(c, 0));
        this.rj.added(new CrawlURI(c, 1));
        this.rj.added(new CrawlURI(c, 2));
        */
    }

}
