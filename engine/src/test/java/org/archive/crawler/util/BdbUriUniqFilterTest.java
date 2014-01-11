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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.FileUtils;
import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TmpDirTestCase;

import com.sleepycat.je.DatabaseException;


/**
 * Test BdbUriUniqFilter.
 * @author stack
 */
public class BdbUriUniqFilterTest extends TmpDirTestCase
implements UriUniqFilter.CrawlUriReceiver {
    private Logger logger =
        Logger.getLogger(BdbUriUniqFilterTest.class.getName());
    
    private UriUniqFilter filter = null;
    private File bdbDir = null;
    
    /**
     * Set to true if we visited received.
     */
    private boolean received = false;
    
	protected void setUp() throws Exception {
		super.setUp();
        // Remove any bdb that already exists.
        this.bdbDir = new File(getTmpDir(), this.getClass().getName());
        if (this.bdbDir.exists()) {
        	FileUtils.deleteDirectory(bdbDir);
        }
		this.filter = new BdbUriUniqFilter(bdbDir, 50);
		this.filter.setDestination(this);
    }
    
	protected void tearDown() throws Exception {
		super.tearDown();
        ((BdbUriUniqFilter)this.filter).close();
        // if (this.bdbDir.exists()) {
        //    FileUtils.deleteDir(bdbDir);
        // }
	}
    
    public void testAdding() throws URIException {
    	this.filter.add(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addNow(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addForce(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        // Should only have add 'this' once.
        assertTrue("Count is off", this.filter.count() == 1);
    }
    
    public void testCreateKey() {
        String url = "dns:archive.org";
        long fingerprint = BdbUriUniqFilter.createKey(url);
        assertTrue("Fingerprint wrong " + url,
            fingerprint == 8812917769287344085L);
        url = "http://archive.org/index.html";
        fingerprint = BdbUriUniqFilter.createKey(url);
        assertTrue("Fingerprint wrong " + url,
            fingerprint == 6613237167064754714L);
    }
    
    /**
     * Verify that two URIs which gave colliding hashes, when previously
     * the last 40bits of the composite did not sufficiently vary with certain
     * inputs, no longer collide. 
     */
    public void testCreateKeyCollisions() {
        HashSet<Long> fingerprints = new HashSet<Long>();
        fingerprints.add(new Long(BdbUriUniqFilter
                .createKey("dns:mail.daps.dla.mil")));
        fingerprints.add(new Long(BdbUriUniqFilter
                .createKey("dns:militaryreview.army.mil")));
        assertEquals("colliding fingerprints",2,fingerprints.size());
    }
    
    /**
     * Time import of recovery log.
     * REMOVE
     * @throws IOException
     * @throws DatabaseException
     */
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
        assertTrue("Count is off: " + this.filter.count(),
            this.filter.count() == max);
    }
    
    public void testNote() {
    	this.filter.note(this.getUri());
        assertFalse("Receiver was called", this.received);
    }
    
    public void testForgetOnEmpty() throws URIException {
        this.filter.forget(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(getUri())));
        assertEquals("Didn't forget", 0, this.filter.count());
    }
    
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
    
    /**
     * return the suite of tests for MemQueueTest
     *
     * @return the suite of test
     */
    public static Test suite() {
        return new TestSuite(BdbUriUniqFilterTest.class);
    }

    public static void main(String[] args) {
    	junit.textui.TestRunner.run(suite());
	}
}