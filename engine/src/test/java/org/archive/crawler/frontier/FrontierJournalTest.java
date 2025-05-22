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

import org.archive.url.URIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

/**
 * @author stack
 * @version $Date$, $Revision$
 */
public class FrontierJournalTest {
    private FrontierJournal rj;
    @TempDir
    Path tempDir;

    @BeforeEach
    protected void setUp() throws Exception {
        this.rj = new FrontierJournal(tempDir.toAbsolutePath().toString(),
            this.getClass().getName());
    }

    @AfterEach
    protected void tearDown() throws Exception {
        if (this.rj != null) {
            this.rj.close();
        }
    }

    @Test
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
