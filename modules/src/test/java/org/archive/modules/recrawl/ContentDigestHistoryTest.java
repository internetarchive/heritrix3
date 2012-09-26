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
package org.archive.modules.recrawl;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;

import java.io.IOException;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.archive.spring.ConfigPath;
import org.archive.util.Base32;
import org.archive.util.TmpDirTestCase;

public class ContentDigestHistoryTest extends TmpDirTestCase {

    private static Logger logger = Logger.getLogger(ContentDigestHistoryTest.class.getName());
    
    protected BdbModule bdb;
    protected BdbContentDigestHistory historyStore;
    protected ContentDigestHistoryStorer storer;
    protected ContentDigestHistoryLoader loader;

    protected ContentDigestHistoryLoader loader() throws IOException {
        if (loader == null) {
            loader = new ContentDigestHistoryLoader();
            loader.setContentDigestHistory(historyStore());
            logger.info("created " + loader);
        }
        return loader;
    }
    
    protected ContentDigestHistoryStorer storer() throws IOException {
        if (storer == null) {
            storer = new ContentDigestHistoryStorer();
            storer.setContentDigestHistory(historyStore());
            logger.info("created " + storer);
        }
        return storer;
    }
    
    protected BdbContentDigestHistory historyStore() throws IOException {
        if (historyStore == null) {
            historyStore = new BdbContentDigestHistory();
            historyStore.setBdbModule(bdb());
            historyStore.start();
            logger.info("created " + historyStore);
        }
        return historyStore;
    }

    protected BdbModule bdb() throws IOException {
        if (bdb == null) {
            ConfigPath basePath = new ConfigPath("testBase",getTmpDir().getAbsolutePath());
            ConfigPath bdbDir = new ConfigPath("bdb","bdb"); 
            bdbDir.setBase(basePath); 
            FileUtils.deleteDirectory(bdbDir.getFile());

            bdb = new BdbModule();
            bdb.setDir(bdbDir);
            bdb.start();
            logger.info("created " + bdb);
        }
        return bdb;
    }

    public void testBasics() throws InterruptedException, IOException {
        CrawlURI curi1 = new CrawlURI(UURIFactory.getInstance("http://example.org/1"));
        
        assertFalse(loader().shouldProcess(curi1));
        assertFalse(storer().shouldProcess(curi1));

        // sha1 of "monkey\n", point is to have a value there
        curi1.setContentDigest("sha1", Base32.decode("orfjublpcrnymm4seg5uk6vfoeu7kw6c"));

        assertTrue(loader().shouldProcess(curi1));
        assertTrue(storer().shouldProcess(curi1));
        
        assertEquals("sha1:ORFJUBLPCRNYMM4SEG5UK6VFOEU7KW6C", historyStore().persistKeyFor(curi1));

        assertFalse(curi1.hasContentDigestHistory());
        
        loader().process(curi1);

        assertTrue(curi1.hasContentDigestHistory());
        assertTrue(curi1.getContentDigestHistory().isEmpty());

        storer().process(curi1);
        assertTrue(historyStore().store.isEmpty());
        
        curi1.getContentDigestHistory().put(A_ORIGINAL_URL, "http://example.org/original");
        // curi1.getContentDigestHistory().put(A_WARC_RECORD_ID, "<urn:uuid:f00dface-d00d-d00d-d00d-0beefface0ff>");
        // curi1.getContentDigestHistory().put(A_WARC_FILENAME, "test.warc.gz");
        // curi1.getContentDigestHistory().put(A_WARC_FILE_OFFSET, 98765432l);
        // curi1.getContentDigestHistory().put(A_ORIGINAL_DATE, "20120101000000");
        // curi1.getContentDigestHistory().put(A_CONTENT_DIGEST_COUNT, 1);
        
        loader().process(curi1);
        assertEquals("http://example.org/original", curi1.getContentDigestHistory().get(A_ORIGINAL_URL));
        
        storer().process(curi1);
        
        assertFalse(historyStore().store.isEmpty());
        assertEquals(1, historyStore().store.size());
        
        CrawlURI curi2 = new CrawlURI(UURIFactory.getInstance("http://example.org/2"));
        curi2.setContentDigest("sha1", Base32.decode("orfjublpcrnymm4seg5uk6vfoeu7kw6c"));
        
        assertFalse(curi2.hasContentDigestHistory());
        
        loader().process(curi2);
        
        assertTrue(curi2.hasContentDigestHistory());
        assertEquals("http://example.org/original", curi2.getContentDigestHistory().get(A_ORIGINAL_URL));
    }
    
    
}
