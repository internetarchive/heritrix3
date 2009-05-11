/* RecoveryJournalTest
 * 
 * Created on Apr 18, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
