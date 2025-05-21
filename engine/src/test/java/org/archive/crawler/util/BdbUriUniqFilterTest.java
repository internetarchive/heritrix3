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
 
package org.archive.crawler.util;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;

import org.archive.url.URIException;
import org.apache.commons.io.FileUtils;
import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

import com.sleepycat.je.DatabaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Test BdbUriUniqFilter.
 * @author stack
 */
public class BdbUriUniqFilterTest
implements UriUniqFilter.CrawlUriReceiver {
    private Logger logger =
        Logger.getLogger(BdbUriUniqFilterTest.class.getName());

    @TempDir
    Path tempDir;

    private UriUniqFilter filter = null;
    private File bdbDir = null;
    
    /**
     * Set to true if we visited received.
     */
    private boolean received = false;

    @BeforeEach
	protected void setUp() throws Exception {
        // Remove any bdb that already exists.
        this.bdbDir = new File(tempDir.toFile(), this.getClass().getName());
        if (this.bdbDir.exists()) {
        	FileUtils.deleteDirectory(bdbDir);
        }
		this.filter = new BdbUriUniqFilter(bdbDir, 50);
		this.filter.setDestination(this);
    }

    @AfterEach
	protected void tearDown() throws Exception {
        ((BdbUriUniqFilter)this.filter).close();
        // if (this.bdbDir.exists()) {
        //    FileUtils.deleteDir(bdbDir);
        // }
	}

    @Test
    public void testAdding() throws URIException {
    	this.filter.add(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addNow(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addForce(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        // Should only have add 'this' once.
        assertEquals(1, this.filter.count(), "Count is off");
    }

    @Test
    public void testCreateKey() {
        String url = "dns:archive.org";
        long fingerprint = BdbUriUniqFilter.createKey(url);
        assertEquals(8812917769287344085L, fingerprint, "Fingerprint wrong " + url);
        url = "http://archive.org/index.html";
        fingerprint = BdbUriUniqFilter.createKey(url);
        assertEquals(6613237167064754714L, fingerprint, "Fingerprint wrong " + url);
    }
    
    /**
     * Verify that two URIs which gave colliding hashes, when previously
     * the last 40bits of the composite did not sufficiently vary with certain
     * inputs, no longer collide. 
     */
    @Test
    public void testCreateKeyCollisions() {
        HashSet<Long> fingerprints = new HashSet<Long>();
        fingerprints.add(BdbUriUniqFilter
                .createKey("dns:mail.daps.dla.mil"));
        fingerprints.add(BdbUriUniqFilter
                .createKey("dns:militaryreview.army.mil"));
        assertEquals(2,fingerprints.size(),"colliding fingerprints");
    }
    
    /**
     * Time import of recovery log.
     * REMOVE
     */
    @Test
    public void testWriting()
    throws IOException, DatabaseException {
        long maxcount = 1000;
        // Look for a system property to override default max count.
        String key = this.getClass().getName() + ".maxcount";
        String maxcountStr = System.getProperty(key);
        logger.info("Looking for override system property " + key);
        if (maxcountStr != null && maxcountStr.length() > 0) {
        	maxcount = Long.parseLong(maxcountStr);
        }
        runTestWriting(maxcount);
    }
    
    protected void runTestWriting(long max)
    throws DatabaseException, URIException {
        long start = System.currentTimeMillis();
        ArrayList<UURI> list = new ArrayList<UURI>(1000);
        int count = 0;
        for (; count < max; count++) {
            UURI u = UURIFactory.getInstance("http://www" +
                count + ".archive.org/" + count + "/index.html");
            this.filter.add(u.toString(), new CrawlURI(u));
            if (count > 0 && ((count % 100) == 0)) {
                list.add(u);
            }
            if (count > 0 && ((count % 100000) == 0)) {
                this.logger.info("Added " + count + " in " +
                    (System.currentTimeMillis() - start) +
                    " misses " +
                    ((BdbUriUniqFilter)this.filter).getCacheMisses() +
                    " diff of misses " +
                    ((BdbUriUniqFilter)this.filter).getLastCacheMissDiff());
            }
        }
        this.logger.info("Added " + count + " in " +
            (System.currentTimeMillis() - start));
        
        start = System.currentTimeMillis();
        for (Iterator<UURI> i = list.iterator(); i.hasNext();) {
            UURI uuri = i.next();
            this.filter.add(uuri.toString(), new CrawlURI(uuri));
        }
        this.logger.info("Added random " + list.size() + " in " +
                (System.currentTimeMillis() - start));
        
        start = System.currentTimeMillis();
        for (Iterator<UURI> i = list.iterator(); i.hasNext();) {
            UURI uuri = i.next();
            this.filter.add(uuri.toString(), new CrawlURI(uuri));
        }
        this.logger.info("Deleted random " + list.size() + " in " +
            (System.currentTimeMillis() - start));
        // Looks like delete doesn't work.
        assertEquals(this.filter.count(), max, "Count is off: " + this.filter.count());
    }

    @Test
    public void testNote() {
    	this.filter.note(this.getUri());
        assertFalse(this.received, "Receiver was called");
    }

    @Test
    public void testForgetOnEmpty() throws URIException {
        this.filter.forget(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(getUri())));
        assertEquals(0, this.filter.count(), "Didn't forget");
    }

    @Test
    public void testForgetAllSchemeAuthorityMatching() throws URIException {
        long countBefore = this.filter.count();
        
        for (String uri: new String[] {
                "http://forgetme.com/",
                "http://forgetme.com/foo",
                "hTtP://fOrGeTmE.cOm/bar",
                "http://forgetme.com:80/toot/spuh",
                "http://forgetme.com:90/toot/spuh",
                "https://forgetme.com/baz",
        }) {
            CrawlURI curi = new CrawlURI(UURIFactory.getInstance(uri));
            this.filter.add(curi.getUURI().toCustomString(), curi);
        }

        assertEquals(countBefore + 6, this.filter.count());

        BdbUriUniqFilter bdbFilter = (BdbUriUniqFilter) filter;
        assertFalse(bdbFilter.setAdd("http://forgetme.com/foo"));

        bdbFilter.forgetAllSchemeAuthorityMatching("http://forgetme.com");
        assertEquals(countBefore + 2, this.filter.count());

        assertTrue(bdbFilter.setAdd("http://forgetme.com/foo"));
        assertFalse(bdbFilter.setAdd("http://forgetme.com/foo"));
        assertTrue(bdbFilter.setRemove("http://forgetme.com/foo"));
        assertFalse(bdbFilter.setRemove("http://forgetme.com/foo"));

        bdbFilter.forgetAllSchemeAuthorityMatching("https://forgetme.com/extra-stuff-ignored");
        assertEquals(countBefore + 1, this.filter.count());

        bdbFilter.forgetAllSchemeAuthorityMatching("http://forgetme.com:90/");
        assertEquals(countBefore, this.filter.count());
    }
    
    // TODO: Add testForget when non-empty
    
	public void receive(CrawlURI item) {
		this.received = true;
	}

	public String getUri() {
		return "http://www.archive.org";
	}
}